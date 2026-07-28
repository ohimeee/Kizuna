package eu.kanade.tachiyomi.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.NovelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
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
) : ViewModel() {

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private var manga: Manga? = null
    private var source: NovelSource? = null
    private var chapterList: List<Chapter> = emptyList()
    private var chapterReadStartTime: Long? = null
    private var initialized = false

    fun needsInit() = !initialized

    suspend fun init(mangaId: Long, chapterId: Long): Result<Boolean> {
        if (initialized) return Result.success(true)
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
            initialized = true
            Result.success(true)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.failure(e)
        }
    }

    private suspend fun openChapter(source: NovelSource, chapter: Chapter) {
        mutableState.update { it.copy(isLoading = true, error = null) }
        chapterReadStartTime = System.currentTimeMillis()

        try {
            val text = source.getChapterText(chapter.toSChapter())
            val paragraphs = splitParagraphs(text)
            val index = chapterList.indexOfFirst { it.id == chapter.id }

            mutableState.update {
                it.copy(
                    isLoading = false,
                    chapter = chapter,
                    paragraphs = paragraphs,
                    initialParagraphIndex = chapter.lastPageRead.toInt()
                        .coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)),
                    hasPrevChapter = index > 0,
                    hasNextChapter = index in 0 until chapterList.lastIndex,
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
        val markRead = isAtEnd || chapter.read
        viewModelScope.launch {
            updateChapter.await(
                ChapterUpdate(id = chapter.id, lastPageRead = paragraphIndex.toLong(), read = markRead),
            )
        }
        mutableState.update {
            it.copy(chapter = chapter.copy(lastPageRead = paragraphIndex.toLong(), read = markRead))
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

        viewModelScope.launch {
            saveHistory()
            openChapter(source, target)
        }
    }

    private fun splitParagraphs(text: String): List<String> {
        val stripTags = Regex("<[^>]+>")
        val split = text.split(Regex("(?i)</p>|<br\\s*/?>|\n{2,}"))
            .map { it.replace(stripTags, "").trim() }
            .filter { it.isNotEmpty() }
        return split.ifEmpty { listOf(text.replace(stripTags, "").trim()) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val chapter: Chapter? = null,
        val paragraphs: List<String> = emptyList(),
        val initialParagraphIndex: Int = 0,
        val hasPrevChapter: Boolean = false,
        val hasNextChapter: Boolean = false,
        val error: String? = null,
    )
}
