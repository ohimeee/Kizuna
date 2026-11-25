package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.translation.GlossaryCsv
import eu.kanade.tachiyomi.ui.reader.translation.GlossaryEntry
import eu.kanade.tachiyomi.ui.reader.translation.NovelTranslationConfig
import eu.kanade.tachiyomi.ui.reader.translation.NovelTranslationStore
import eu.kanade.tachiyomi.ui.reader.translation.NovelTranslator
import eu.kanade.tachiyomi.ui.reader.translation.TranslationException
import eu.kanade.tachiyomi.ui.reader.translation.TranslationSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.getChapterSort
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.novelprogress.interactor.UpsertNovelChapterProgress
import tachiyomi.domain.source.service.SourceManager
import kotlin.time.Clock

/**
 * Reader ViewModel for [NovelSource] chapters. Mirrors [ReaderViewModel]'s progress-persistence
 * approach (same [ChapterUpdate]/[HistoryUpdate] calls, same DB schema) but with an entirely
 * different content model: a chapter is its whole text split into paragraphs, not a list of image
 * [eu.kanade.tachiyomi.source.model.Page]s. Reading progress reuses `lastPageRead` as a paragraph
 * index — no schema change needed to share the database with image content.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class NovelReaderViewModel(
    private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val sourceManager: SourceManager,
    private val getManga: GetManga,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateChapter: UpdateChapter,
    private val upsertHistory: UpsertHistory,
    private val downloadManager: DownloadManager,
    private val getIncognitoState: GetIncognitoState,
    private val trackPreferences: TrackPreferences,
    private val trackChapter: TrackChapter,
    private val upsertNovelChapterProgress: UpsertNovelChapterProgress,
    private val network: NetworkHelper,
    private val novelReaderPreferences: NovelReaderPreferences,
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private var manga: Manga? = null
    private var source: NovelSource? = null
    private var chapterList: List<Chapter> = emptyList()
    private var chapterReadStartTime: Long? = null
    private var initialized = false
    private var incognitoMode: Boolean = false

    // Scroll ticks are frequent and the percent changes far less often, so only changes are written
    private var savedPercent: Pair<Long, Int>? = null

    // NovelReaderActivity is launchMode="singleTask", so re-tapping a different chapter (e.g.
    // spamming entries in History) while a reader is already open reuses the SAME Activity/
    // ViewModel instance via onNewIntent rather than creating a new one. Guards against two loads
    // racing each other and clobbering shared state (manga/source/chapterList) with a stale
    // result landing after a newer one.
    private var loadJob: Job? = null

    /**
     * The most recently requested (mangaId, chapterId), kept so [reloadChapter] still works after
     * a failure that happened *before* any chapter could be put into state (e.g. the chapter-list
     * fetch itself failed) - in that case there is no `state.chapter` to retry from.
     */
    private var lastTarget: Pair<Long, Long>? = null

    // --- AI translation ---
    private val translationStore by lazy { NovelTranslationStore(context) }
    private val translator by lazy { NovelTranslator(network.client) }
    private var novelKey: String? = null
    private var translationConfig = NovelTranslationConfig()

    /** The chapter as the source gave it; `state.paragraphs` holds whichever version is shown. */
    private var originalParagraphs: List<String> = emptyList()
    private var translatedParagraphs: List<String>? = null
    private var translateJob: Job? = null
    private var prefetchJob: Job? = null

    /**
     * Translations in progress by chapter id, shared by the reader and the next-chapter prefetch
     * so a chapter is never translated twice at once (opening a chapter that is still being
     * prefetched just waits for that job instead of starting another).
     */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<Long, Deferred<List<String>>>()

    fun needsInit() = !initialized

    suspend fun init(mangaId: Long, chapterId: Long): Result<Boolean> {
        if (initialized) return Result.success(true)
        lastTarget = mangaId to chapterId
        val result = loadManga(mangaId, chapterId)
        if (result.isSuccess) initialized = true
        return result
    }

    /**
     * Called from [NovelReaderActivity.onNewIntent] when a `singleTask` re-entry brings an
     * already-open reader back to front for a *different* chapter/manga (e.g. tapping another
     * History entry while a reader is already open) - unlike [init], this always (re)loads
     * regardless of [initialized], since the existing state is for the wrong chapter entirely.
     */
    fun openNewTarget(mangaId: Long, chapterId: Long) {
        lastTarget = mangaId to chapterId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveHistory()
            loadManga(mangaId, chapterId)
        }
    }

    private suspend fun loadManga(mangaId: Long, chapterId: Long): Result<Boolean> {
        return try {
            val manga = getManga.await(mangaId) ?: error("Manga not found")
            this.manga = manga
            mutableState.update { it.copy(mangaTitle = manga.title) }

            val source = sourceManager.get(manga.source) as? NovelSource
                ?: error("Source ${manga.source} is not a novel source")
            this.source = source
            incognitoMode = getIncognitoState.await(manga.source)

            val key = translationStore.novelKey(manga.source, manga.url)
            novelKey = key
            translationConfig = withContext(Dispatchers.IO) { translationStore.loadConfig(key) }
            mutableState.update {
                it.copy(translationEnabled = translationConfig.enabled, glossary = translationConfig.glossary)
            }

            chapterList = getChaptersByMangaId.await(mangaId, applyScanlatorFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))
            mutableState.update { it.copy(chapters = chapterList) }

            val chapter = chapterList.find { it.id == chapterId } ?: error("Chapter not found")

            openChapter(source, chapter)
            Result.success(true)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            mutableState.update {
                it.copy(isLoading = false, error = e.message ?: "Failed to load chapter")
            }
            Result.failure(e)
        }
    }

    private suspend fun openChapter(source: NovelSource, chapter: Chapter) {
        translateJob?.cancel()
        originalParagraphs = emptyList()
        translatedParagraphs = null
        mutableState.update {
            it.copy(isLoading = true, error = null, isTranslating = false, translationProgress = null, translationError = null)
        }
        chapterReadStartTime = System.currentTimeMillis()

        // Computed up front (not just on success) so a failed load still lets the error screen's
        // Previous/Next reflect *this* chapter instead of leaking over stale values from whichever
        // chapter last loaded successfully.
        val index = chapterList.indexOfFirst { it.id == chapter.id }
        val hasPrevChapter = index > 0
        val hasNextChapter = index in 0 until chapterList.lastIndex
        val prevChapterName = chapterList.getOrNull(index - 1)?.name
        val nextChapterName = chapterList.getOrNull(index + 1)?.name

        try {
            val manga = manga ?: error("Manga not loaded")
            val bodyParagraphs = fetchBodyParagraphs(source, manga, chapter)

            // Emptiness must be judged on the body alone - withChapterTitle() below always
            // prepends a non-blank title, which would otherwise mask a completely empty chapter.
            if (bodyParagraphs.all { it.isBlank() }) {
                // Not every "no content" case is a fetch failure - some sites (Webnovel in
                // particular) paywall chapters past the first few free ones per novel, and the
                // source plugin deliberately doesn't try to bypass that. The raw response can
                // still contain non-blank markup (e.g. an empty "<p></p>") even though nothing
                // readable comes out the other end of paragraph-splitting, so this is checked
                // after splitting, not on the raw text. Wire up prev/next regardless so a locked
                // chapter doesn't strand the reader with no way past it.
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        chapter = chapter,
                        error = "No content available - this chapter may be locked on the source site",
                        hasPrevChapter = hasPrevChapter,
                        hasNextChapter = hasNextChapter,
                        prevChapterName = prevChapterName,
                        nextChapterName = nextChapterName,
                    )
                }
                return
            }

            val paragraphs = withChapterTitle(chapter.name, bodyParagraphs)
            originalParagraphs = paragraphs

            mutableState.update {
                it.copy(
                    isLoading = false,
                    chapter = chapter,
                    paragraphs = paragraphs,
                    showingTranslation = false,
                    initialParagraphIndex = chapter.lastPageRead
                        .coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)),
                    hasPrevChapter = hasPrevChapter,
                    hasNextChapter = hasNextChapter,
                    prevChapterName = prevChapterName,
                    nextChapterName = nextChapterName,
                )
            }
            if (translationConfig.enabled) startTranslation(chapter, force = false)
        } catch (e: TimeoutCancellationException) {
            // TimeoutCancellationException is itself a CancellationException, so this must be
            // caught ahead of the generic CancellationException rethrow below - otherwise a real
            // timeout gets treated as job replacement and silently leaves isLoading stuck true
            // forever instead of surfacing as the failure it actually is.
            logcat(LogPriority.ERROR, e) { "Timed out loading chapter ${chapter.url}" }
            mutableState.update {
                it.copy(
                    isLoading = false,
                    chapter = chapter,
                    error = "Loading timed out - the source may be slow or unreachable",
                    hasPrevChapter = hasPrevChapter,
                    hasNextChapter = hasNextChapter,
                    prevChapterName = prevChapterName,
                    nextChapterName = nextChapterName,
                )
            }
        } catch (e: CancellationException) {
            // Must not be handled like a failure. CancellationException is an Exception, so the
            // catch below used to swallow it and have the *cancelled* load write
            // `isLoading = false` + an error into shared state - after the replacement load had
            // already set `isLoading = true`. A dead job clobbering the live one's state that way
            // leaves the reader showing a spurious error, or stuck, depending on which write lands
            // last. Rethrowing keeps cancellation structured; the job that replaced this one owns
            // the state from here.
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            mutableState.update {
                it.copy(
                    isLoading = false,
                    chapter = chapter,
                    error = e.message ?: "Failed to load chapter",
                    hasPrevChapter = hasPrevChapter,
                    hasNextChapter = hasNextChapter,
                    prevChapterName = prevChapterName,
                    nextChapterName = nextChapterName,
                )
            }
        }
    }

    /** Called by the reader UI as the user scrolls; persists the paragraph index as progress. */
    fun updateProgress(paragraphIndex: Int, isAtEnd: Boolean) {
        val chapter = state.value.chapter ?: return
        val wasRead = chapter.read
        val markRead = isAtEnd || wasRead
        // On the app scope so the read flag lands in the DB along with the tracker push below, even
        // if the reader is closed right as the chapter ends. Main.immediate (as ReaderViewModel does)
        // keeps successive scroll ticks starting in order, so a stale one can't land after a newer one.
        appScope.launch(Dispatchers.Main.immediate) {
            updateChapter.await(
                ChapterUpdate(chapter.id) {
                    lastPageRead = paragraphIndex
                    read = markRead
                },
            )
        }
        val percent = novelProgressPercent(paragraphIndex, state.value.paragraphs.size, isAtEnd)
        if (savedPercent != chapter.id to percent) {
            savedPercent = chapter.id to percent
            appScope.launch(Dispatchers.Main.immediate) {
                upsertNovelChapterProgress.await(chapter.id, percent)
            }
        }
        if (markRead && !wasRead) {
            updateTrackChapterRead(chapter)
        }
        val updated = chapter.copy(lastPageRead = paragraphIndex, read = markRead)
        syncChapterInList(updated)
        mutableState.update { it.copy(chapter = updated) }
    }

    /**
     * Mirrors [ReaderViewModel]'s tracker sync (same [TrackChapter] call, same incognito/
     * auto-update guards) - without this, finishing a novel chapter never updates "chapters read"
     * on AniList/MAL/etc, unlike the manga reader.
     */
    private fun updateTrackChapterRead(chapter: Chapter) {
        if (incognitoMode) return
        if (!trackPreferences.autoUpdateTrack.get()) return

        val manga = manga ?: return

        // Finishing a chapter and leaving right away is common, so this outlives the reader
        appScope.launch {
            trackChapter.await(context, manga.id, chapter.chapterNumber)
        }
    }

    fun saveHistory() {
        val chapter = state.value.chapter ?: return
        val startTime = chapterReadStartTime ?: return
        chapterReadStartTime = null
        // On the app scope so the session still gets recorded when the reader is closed
        appScope.launch(Dispatchers.Main.immediate) {
            val duration = System.currentTimeMillis() - startTime
            upsertHistory.await(HistoryUpdate(chapter.id, Clock.System.now(), duration))
        }
    }

    /** URL of the currently displayed chapter, for "Open in WebView". */
    fun getChapterUrl(): String? {
        val chapter = state.value.chapter ?: return null
        return source?.getChapterUrl(chapter.toSChapter())
    }

    /**
     * Plain forward navigation - does NOT mark the outgoing chapter read. Used by the nav bar's
     * Next button and the error screen, both reachable from anywhere in a chapter; treating those
     * as "finished" would mark a chapter read (and push that to trackers) two paragraphs in.
     * [finishAndLoadNextChapter] is the one that means "read this to the end".
     */
    fun loadNextChapter() = loadAdjacentChapter(offset = 1, markCurrentRead = false)

    /**
     * Advances from the end-of-chapter pill, which is only reachable by scrolling to the bottom,
     * so the outgoing chapter really was read.
     */
    fun finishAndLoadNextChapter() = loadAdjacentChapter(offset = 1, markCurrentRead = true)

    fun loadPreviousChapter() = loadAdjacentChapter(offset = -1, markCurrentRead = false)

    /** Jumps straight to a chapter picked from the reader's chapter-list sheet. */
    fun openChapterById(chapterId: Long) {
        if (chapterId == state.value.chapter?.id) return
        val target = chapterList.find { it.id == chapterId } ?: return
        val source = this.source ?: return

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveHistory()
            openChapter(source, target)
        }
    }

    /**
     * Toggles the bookmark on the chapter being read. State is updated up front rather than
     * waiting on the DB write, so the icon reacts immediately; [chapterList] is kept in sync too
     * so the chapter-list sheet doesn't show a stale flag for the same chapter.
     */
    fun toggleBookmark() {
        val chapter = state.value.chapter ?: return
        val bookmarked = !chapter.bookmark
        val updated = chapter.copy(bookmark = bookmarked)

        syncChapterInList(updated)
        mutableState.update { it.copy(chapter = updated) }

        // On the app scope so a toggle made right before leaving the reader is still saved
        appScope.launch {
            updateChapter.await(ChapterUpdate(chapter.id) { bookmark = bookmarked })
        }
    }

    /** Retries the chapter currently shown (including a failed one) - does not navigate. */
    fun reloadChapter() {
        val chapter = state.value.chapter
        val source = this.source

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (chapter != null && source != null) {
                openChapter(source, chapter)
            } else {
                // Nothing loaded to retry from - the failure happened before a chapter (or the
                // source) was ever resolved, so redo the whole load rather than no-op behind a
                // button the error screen is actively offering.
                val (mangaId, chapterId) = lastTarget ?: return@launch
                loadManga(mangaId, chapterId)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // AI translation
    // ---------------------------------------------------------------------------------------

    /** Switches translation on or off for this whole novel. Off shows the original right away. */
    fun toggleTranslation() {
        val enabled = !translationConfig.enabled
        updateTranslationConfig(translationConfig.copy(enabled = enabled))
        val chapter = state.value.chapter ?: return
        if (enabled) {
            startTranslation(chapter, force = false)
        } else {
            translateJob?.cancel()
            showParagraphs(originalParagraphs)
            mutableState.update { it.copy(isTranslating = false, translationProgress = null, translationError = null) }
        }
    }

    /** Throws away this chapter's saved translation and translates it again (e.g. after glossary edits). */
    fun retranslateChapter() {
        val chapter = state.value.chapter ?: return
        val key = novelKey ?: return
        translationStore.deleteChapter(key, chapter.url, novelReaderPreferences.translationTargetLanguage.get())
        translatedParagraphs = null
        startTranslation(chapter, force = true)
    }

    fun dismissTranslationError() {
        mutableState.update { it.copy(translationError = null) }
    }

    /** Adds [entry], or replaces the entry at [index]. */
    fun saveGlossaryEntry(index: Int?, entry: GlossaryEntry) {
        val glossary = translationConfig.glossary.toMutableList()
        if (index != null && index in glossary.indices) glossary[index] = entry else glossary += entry
        updateTranslationConfig(translationConfig.copy(glossary = glossary))
    }

    fun deleteGlossaryEntry(index: Int) {
        val glossary = translationConfig.glossary.toMutableList()
        if (index !in glossary.indices) return
        glossary.removeAt(index)
        updateTranslationConfig(translationConfig.copy(glossary = glossary))
    }

    /** Merges a CSV glossary in; a row whose raw term already exists replaces that entry. Returns the row count. */
    fun importGlossaryCsv(text: String): Int {
        val imported = GlossaryCsv.parse(text)
        if (imported.isEmpty()) return 0
        val byRaw = LinkedHashMap<String, GlossaryEntry>()
        translationConfig.glossary.forEach { byRaw[it.raw] = it }
        imported.forEach { byRaw[it.raw] = it }
        updateTranslationConfig(translationConfig.copy(glossary = byRaw.values.toList()))
        return imported.size
    }

    fun exportGlossaryCsv(): String = GlossaryCsv.export(translationConfig.glossary)

    private fun updateTranslationConfig(config: NovelTranslationConfig) {
        translationConfig = config
        mutableState.update { it.copy(translationEnabled = config.enabled, glossary = config.glossary) }
        val key = novelKey ?: return
        appScope.launch(Dispatchers.IO) { translationStore.saveConfig(key, config) }
    }

    private fun currentTranslationSettings() = TranslationSettings(
        provider = novelReaderPreferences.translationProvider.get(),
        apiKey = novelReaderPreferences.translationApiKey.get().trim(),
        model = novelReaderPreferences.translationModel.get().trim(),
        baseUrl = novelReaderPreferences.translationBaseUrl.get().trim(),
        targetLanguage = novelReaderPreferences.translationTargetLanguage.get().trim().ifBlank { "English" },
    )

    private fun startTranslation(chapter: Chapter, force: Boolean) {
        val key = novelKey ?: return
        val original = originalParagraphs
        if (original.isEmpty()) return
        translatedParagraphs?.let {
            if (!force) {
                showParagraphs(it)
                prefetchNext(chapter)
                return
            }
        }
        val settings = currentTranslationSettings()
        if (force) inFlight.remove(chapter.id)?.cancel()

        translateJob?.cancel()
        translateJob = viewModelScope.launch {
            // A saved translation shows straight away, without flashing the "Translating" banner.
            val saved = if (force) {
                null
            } else {
                withContext(Dispatchers.IO) { translationStore.loadChapter(key, chapter.url, settings.targetLanguage, original) }
            }
            if (saved != null) {
                applyTranslation(chapter, saved)
                prefetchNext(chapter)
                return@launch
            }
            mutableState.update { it.copy(isTranslating = true, translationProgress = null, translationError = null) }
            try {
                applyTranslation(chapter, translationFor(chapter, original, settings).await())
                prefetchNext(chapter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TranslationException) {
                mutableState.update { it.copy(isTranslating = false, translationProgress = null, translationError = e.message) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Translation failed for ${chapter.url}" }
                mutableState.update {
                    it.copy(isTranslating = false, translationProgress = null, translationError = e.message ?: "Translation failed")
                }
            }
        }
    }

    /**
     * The translation of [chapter]: saved one if present, else a new one that is saved when done.
     * While it runs, every finished chunk is shown right away if the reader is on that chapter,
     * so the start of a long chapter is readable long before the end arrives.
     */
    private fun translationFor(chapter: Chapter, original: List<String>, settings: TranslationSettings): Deferred<List<String>> {
        inFlight[chapter.id]?.takeIf { it.isActive }?.let { return it }
        val key = novelKey ?: error("No novel loaded")
        val job = viewModelScope.async {
            withContext(Dispatchers.IO) { translationStore.loadChapter(key, chapter.url, settings.targetLanguage, original) }
                ?: translator.translate(original, translationConfig.glossary, settings) { partial, done, total ->
                    val onThisChapter = state.value.chapter?.id == chapter.id && originalParagraphs.size == original.size
                    if (onThisChapter && translationConfig.enabled && translatedParagraphs == null) {
                        showParagraphs(partial.mapIndexed { i, text -> text ?: originalParagraphs[i] })
                        mutableState.update { it.copy(translationProgress = done to total) }
                    }
                }.also { translated ->
                    withContext(Dispatchers.IO) {
                        translationStore.saveChapter(key, chapter.url, settings.targetLanguage, original, translated)
                    }
                }
        }
        inFlight[chapter.id] = job
        job.invokeOnCompletion { inFlight.remove(chapter.id, job) }
        return job
    }

    /**
     * Translates the next chapter in the background once the current one is done, so tapping Next
     * opens it already translated. One chapter ahead only: each one costs an API call.
     */
    private fun prefetchNext(chapter: Chapter) {
        if (!translationConfig.enabled) return
        val source = source ?: return
        val manga = manga ?: return
        val key = novelKey ?: return
        val next = chapterList.getOrNull(chapterList.indexOfFirst { it.id == chapter.id } + 1) ?: return
        if (inFlight[next.id]?.isActive == true) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            try {
                val body = fetchBodyParagraphs(source, manga, next)
                if (body.all { it.isBlank() }) return@launch
                val original = withChapterTitle(next.name, body)
                val settings = currentTranslationSettings()
                val saved = withContext(Dispatchers.IO) {
                    translationStore.loadChapter(key, next.url, settings.targetLanguage, original)
                }
                if (saved == null) translationFor(next, original, settings).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Prefetch translation failed for ${next.url}" }
            }
        }
    }

    /** The chapter's body paragraphs, from a download if there is one, else from the source. */
    private suspend fun fetchBodyParagraphs(source: NovelSource, manga: Manga, chapter: Chapter): List<String> {
        val text = downloadManager.getChapterTextIfDownloaded(source, manga, chapter)
            ?: withTimeout(30_000) { source.getChapterText(chapter.toSChapter()) }
        return splitNovelParagraphs(text)
    }

    /** Shows [translated] if the reader is still on [chapter] with translation switched on. */
    private fun applyTranslation(chapter: Chapter, translated: List<String>) {
        if (state.value.chapter?.id != chapter.id || translated.size != originalParagraphs.size) return
        translatedParagraphs = translated
        mutableState.update { it.copy(isTranslating = false, translationProgress = null) }
        if (translationConfig.enabled) showParagraphs(translated)
    }

    /**
     * Swaps the displayed text. Both versions always have the same paragraph count, so the reader
     * keeps its scroll position, progress and percent across the swap.
     */
    private fun showParagraphs(paragraphs: List<String>) {
        if (paragraphs.isEmpty()) return
        mutableState.update { it.copy(paragraphs = paragraphs, showingTranslation = paragraphs !== originalParagraphs) }
    }

    private fun loadAdjacentChapter(offset: Int, markCurrentRead: Boolean) {
        val current = state.value.chapter ?: return
        val index = chapterList.indexOfFirst { it.id == current.id }
        val target = chapterList.getOrNull(index + offset) ?: return
        val source = this.source ?: return

        // Only the end-of-chapter pill passes markCurrentRead - it sits after the last paragraph,
        // so reaching it means the chapter was read to the end, and the scroll-driven progress
        // collector is debounced enough that tapping it promptly would otherwise navigate away
        // before the end-of-chapter update landed, leaving a finished chapter at 80-90% and
        // unread. The nav bar's Next button is reachable from anywhere and must not mark anything.
        if (markCurrentRead && offset > 0 && !current.read) {
            markChapterRead(current)
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveHistory()
            openChapter(source, target)
        }
    }

    /**
     * Writes an updated chapter back into the cached [chapterList] (and the state copy the
     * chapter-list sheet renders from).
     *
     * [chapterList] is loaded once per novel and otherwise never refreshed, so without this any
     * progress made this session is invisible to it: the sheet would show chapters finished
     * minutes ago as still unread, and jumping back to one via the sheet would reopen it at its
     * stale `lastPageRead` - then overwrite the good value on the next scroll.
     */
    private fun syncChapterInList(updated: Chapter) {
        chapterList = chapterList.map { if (it.id == updated.id) updated else it }
        mutableState.update { it.copy(chapters = chapterList) }
    }

    private fun markChapterRead(chapter: Chapter) {
        appScope.launch {
            updateChapter.await(ChapterUpdate(chapter.id) { read = true })
        }
        updateTrackChapterRead(chapter)
        syncChapterInList(chapter.copy(read = true))
        mutableState.update {
            if (it.chapter?.id == chapter.id) it.copy(chapter = chapter.copy(read = true)) else it
        }
    }

    /**
     * Makes paragraph 0 the chapter's own name, always.
     *
     * The reader renders paragraph 0 as the chapter title (bold, with extra trailing space), which
     * previously assumed the source's own body text opened with a heading line. That assumption
     * doesn't hold: some sources emit a heading, some don't, and the ones that do don't agree on
     * the tag. When it was missing, the reader force-bolded whatever the first body paragraph
     * happened to be - so a chapter would open with its first sentence styled as the title and no
     * actual title anywhere.
     *
     * Using [Chapter.name] instead makes the title identical to what the chapter list shows,
     * regardless of source. A leading body paragraph that just repeats that name (from a source
     * that does emit its own heading) is dropped rather than shown twice.
     */
    private fun withChapterTitle(chapterName: String, body: List<String>): List<String> {
        val duplicateTitle = body.firstOrNull()?.let { isSameTitle(it, chapterName) } == true
        return listOf(chapterName) + if (duplicateTitle) body.drop(1) else body
    }

    /** Compares on letters/digits only, so punctuation and tag differences don't defeat the match. */
    private fun isSameTitle(paragraph: String, chapterName: String): Boolean {
        fun normalize(value: String) = value
            .replace(Regex("<[^>]*>"), "")
            .filter { it.isLetterOrDigit() }
            .lowercase()

        val normalizedParagraph = normalize(paragraph)
        return normalizedParagraph.isNotEmpty() && normalizedParagraph == normalize(chapterName)
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        /** Novel title, shown as the reader top bar's subtitle under the chapter name. */
        val mangaTitle: String? = null,
        val chapter: Chapter? = null,
        /** Every chapter of this novel, in reading order - backs the reader's chapter-list sheet. */
        val chapters: List<Chapter> = emptyList(),
        val paragraphs: List<String> = emptyList(),
        val initialParagraphIndex: Int = 0,
        val hasPrevChapter: Boolean = false,
        val hasNextChapter: Boolean = false,
        val prevChapterName: String? = null,
        val nextChapterName: String? = null,
        val error: String? = null,
        /** Translation is switched on for this novel. */
        val translationEnabled: Boolean = false,
        /** [paragraphs] currently holds the translation rather than the original. */
        val showingTranslation: Boolean = false,
        val isTranslating: Boolean = false,
        /** Chunks done / total while a long chapter translates. */
        val translationProgress: Pair<Int, Int>? = null,
        val translationError: String? = null,
        val glossary: List<GlossaryEntry> = emptyList(),
    )
}
