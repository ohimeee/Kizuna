package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.track.interactor.TrackChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.NovelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchNonCancellable
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
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Reader ViewModel for [NovelSource] chapters. Mirrors [ReaderViewModel]'s progress-persistence
 * approach (same [ChapterUpdate]/[HistoryUpdate] calls, same DB schema) but with an entirely
 * different content model: a chapter is its whole text split into paragraphs, not a list of image
 * [eu.kanade.tachiyomi.source.model.Page]s. Reading progress reuses `lastPageRead` as a paragraph
 * index — no schema change needed to share the database with image content.
 */
class NovelReaderViewModel @JvmOverloads constructor(
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val getIncognitoState: GetIncognitoState = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    private val trackChapter: TrackChapter = Injekt.get(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private var manga: Manga? = null
    private var source: NovelSource? = null
    private var chapterList: List<Chapter> = emptyList()
    private var chapterReadStartTime: Long? = null
    private var initialized = false
    private val incognitoMode: Boolean by lazy { getIncognitoState.await(manga?.source) }

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

            chapterList = getChaptersByMangaId.await(mangaId, applyScanlatorFilter = true)
                .sortedWith(getChapterSort(manga, sortDescending = false))

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
        mutableState.update { it.copy(isLoading = true, error = null) }
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
            val text = downloadManager.getChapterTextIfDownloaded(source, manga, chapter)
                ?: withTimeout(30_000) { source.getChapterText(chapter.toSChapter()) }

            val bodyParagraphs = splitParagraphs(text)

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

            mutableState.update {
                it.copy(
                    isLoading = false,
                    chapter = chapter,
                    paragraphs = paragraphs,
                    initialParagraphIndex = chapter.lastPageRead.toInt()
                        .coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)),
                    hasPrevChapter = hasPrevChapter,
                    hasNextChapter = hasNextChapter,
                    prevChapterName = prevChapterName,
                    nextChapterName = nextChapterName,
                )
            }
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
        viewModelScope.launch {
            updateChapter.await(
                ChapterUpdate(id = chapter.id, lastPageRead = paragraphIndex.toLong(), read = markRead),
            )
        }
        if (markRead && !wasRead) {
            updateTrackChapterRead(chapter)
        }
        mutableState.update {
            it.copy(chapter = chapter.copy(lastPageRead = paragraphIndex.toLong(), read = markRead))
        }
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
        val context = Injekt.get<Application>()

        viewModelScope.launchNonCancellable {
            trackChapter.await(context, manga.id, chapter.chapterNumber)
        }
    }

    fun saveHistory() {
        val chapter = state.value.chapter ?: return
        val startTime = chapterReadStartTime ?: return
        chapterReadStartTime = null
        viewModelScope.launch {
            val duration = System.currentTimeMillis() - startTime
            upsertHistory.await(HistoryUpdate(chapter.id, Date(), duration))
        }
    }

    /** URL of the currently displayed chapter, for "Open in WebView". */
    fun getChapterUrl(): String? {
        val chapter = state.value.chapter ?: return null
        return source?.getChapterUrl(chapter.toSChapter())
    }

    fun loadNextChapter() = loadAdjacentChapter(offset = 1)

    fun loadPreviousChapter() = loadAdjacentChapter(offset = -1)

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

    private fun loadAdjacentChapter(offset: Int) {
        val current = state.value.chapter ?: return
        val index = chapterList.indexOfFirst { it.id == current.id }
        val target = chapterList.getOrNull(index + offset) ?: return
        val source = manga?.let { sourceManager.get(it.source) as? NovelSource } ?: return

        // Advancing marks the chapter being left as read. The only way forward is the "Next: ..."
        // pill rendered after the last paragraph, so getting here means the chapter was read to
        // the end - but the scroll-driven progress collector is debounced, so tapping that pill
        // promptly would otherwise navigate away before the end-of-chapter update ever landed and
        // leave a fully-read chapter sitting at 80-90% and unread. Going backwards must not mark
        // anything.
        if (offset > 0 && !current.read) {
            markChapterRead(current)
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveHistory()
            openChapter(source, target)
        }
    }

    private fun markChapterRead(chapter: Chapter) {
        viewModelScope.launchNonCancellable {
            updateChapter.await(ChapterUpdate(id = chapter.id, read = true))
        }
        updateTrackChapterRead(chapter)
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

    /**
     * Splits chapter HTML into paragraphs, keeping *inline* tags (`<b>`, `<i>`, etc.) intact so
     * [eu.kanade.presentation.reader.parseInlineHtml] can render them as real bold/italic spans
     * instead of losing formatting - only block-level `<p>` wrappers and paragraph boundaries are
     * stripped here.
     */
    private fun splitParagraphs(text: String): List<String> {
        // A closing heading tag ends a paragraph just like </p> does. Without it, a chapter body
        // shaped like "<h4>Title</h4><p>First sentence...</p>" - which is what NovelArrow and
        // Webnovel both produce, since they prepend the chapter name as a heading - collapsed the
        // title and the opening sentence into a single chunk. That chunk is index 0, which the
        // reader renders force-bolded as the chapter title, so the first sentence of every chapter
        // got swallowed into the bold title line.
        val stripBlockTags = Regex("(?i)</?(p|h[1-6])[^>]*>")
        val split = text.split(Regex("(?i)</p>|</h[1-6]>|<br\\s*/?>\\s*<br\\s*/?>|\n{2,}"))
            .map { it.replace(stripBlockTags, "").trim() }
            .filter { it.isNotEmpty() }
        return split.ifEmpty { listOf(text.replace(stripBlockTags, "").trim()) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        /** Novel title, shown as the reader top bar's subtitle under the chapter name. */
        val mangaTitle: String? = null,
        val chapter: Chapter? = null,
        val paragraphs: List<String> = emptyList(),
        val initialParagraphIndex: Int = 0,
        val hasPrevChapter: Boolean = false,
        val hasNextChapter: Boolean = false,
        val prevChapterName: String? = null,
        val nextChapterName: String? = null,
        val error: String? = null,
    )
}
