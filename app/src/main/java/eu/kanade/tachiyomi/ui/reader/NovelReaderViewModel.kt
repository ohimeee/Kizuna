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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    fun needsInit() = !initialized

    suspend fun init(mangaId: Long, chapterId: Long): Result<Boolean> {
        if (initialized) return Result.success(true)
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

        try {
            val manga = manga ?: error("Manga not loaded")
            val text = downloadManager.getChapterTextIfDownloaded(source, manga, chapter)
                ?: source.getChapterText(chapter.toSChapter())
            val index = chapterList.indexOfFirst { it.id == chapter.id }

            val paragraphs = splitParagraphs(text)

            if (paragraphs.all { it.isBlank() }) {
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
                        hasPrevChapter = index > 0,
                        hasNextChapter = index in 0 until chapterList.lastIndex,
                        prevChapterName = chapterList.getOrNull(index - 1)?.name,
                        nextChapterName = chapterList.getOrNull(index + 1)?.name,
                    )
                }
                return
            }

            mutableState.update {
                it.copy(
                    isLoading = false,
                    chapter = chapter,
                    paragraphs = paragraphs,
                    initialParagraphIndex = chapter.lastPageRead.toInt()
                        .coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)),
                    hasPrevChapter = index > 0,
                    hasNextChapter = index in 0 until chapterList.lastIndex,
                    prevChapterName = chapterList.getOrNull(index - 1)?.name,
                    nextChapterName = chapterList.getOrNull(index + 1)?.name,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            mutableState.update {
                it.copy(isLoading = false, error = e.message ?: "Failed to load chapter")
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

    private fun loadAdjacentChapter(offset: Int) {
        val current = state.value.chapter ?: return
        val index = chapterList.indexOfFirst { it.id == current.id }
        val target = chapterList.getOrNull(index + offset) ?: return
        val source = manga?.let { sourceManager.get(it.source) as? NovelSource } ?: return

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveHistory()
            openChapter(source, target)
        }
    }

    /**
     * Splits chapter HTML into paragraphs, keeping *inline* tags (`<b>`, `<i>`, etc.) intact so
     * [eu.kanade.presentation.reader.parseInlineHtml] can render them as real bold/italic spans
     * instead of losing formatting - only block-level `<p>` wrappers and paragraph boundaries are
     * stripped here.
     */
    private fun splitParagraphs(text: String): List<String> {
        val stripBlockTags = Regex("(?i)</?p[^>]*>")
        val split = text.split(Regex("(?i)</p>|<br\\s*/?>\\s*<br\\s*/?>|\n{2,}"))
            .map { it.replace(stripBlockTags, "").trim() }
            .filter { it.isNotEmpty() }
        return split.ifEmpty { listOf(text.replace(stripBlockTags, "").trim()) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
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
