package tachiyomi.domain.chapter.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.source.SourceContentType
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

@Inject
class SetMangaDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val getFavorites: GetFavorites,
    private val sourceManager: SourceManager,
) {

    suspend fun await(manga: Manga) {
        setDefaultFlags(listOf(manga))
    }

    suspend fun awaitAll() {
        setDefaultFlags(getFavorites.await())
    }

    private suspend fun setDefaultFlags(mangas: List<Manga>) {
        // Novel-source chapterList() is natural reading order (chapter 1 first), the
        // opposite of the manga-extension newest-first convention Mihon's own sort
        // direction preference assumes - see JsNovelSource.getMangaUpdate(). This runs
        // on every non-favorite details-screen visit (SetMangaDefaultChapterFlags.await
        // is called from MangaViewModel.init unconditionally), so it must decide the
        // direction itself rather than deferring to the global manga preference.
        val (novels, others) = mangas.partition {
            sourceManager.get(it.source)?.contentType == SourceContentType.NOVEL
        }
        setDefaultFlags(others.map { it.id }, novel = false)
        setDefaultFlags(novels.map { it.id }, novel = true)
    }

    private suspend fun setDefaultFlags(mangaIds: List<Long>, novel: Boolean) {
        if (mangaIds.isEmpty()) return
        with(libraryPreferences) {
            setMangaChapterFlags.awaitSetAllFlags(
                mangaIds = mangaIds,
                unreadFilter = filterChapterByRead.get(),
                downloadedFilter = filterChapterByDownloaded.get(),
                bookmarkedFilter = filterChapterByBookmarked.get(),
                sortingMode = sortChapterBySourceOrNumber.get(),
                sortingDirection = if (novel) Manga.CHAPTER_SORT_ASC else sortChapterByAscendingOrDescending.get(),
                displayMode = displayChapterByNameOrNumber.get(),
            )
        }
    }
}
