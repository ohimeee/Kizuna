package tachiyomi.domain.chapter.interactor

import eu.kanade.tachiyomi.source.SourceContentType
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

class SetMangaDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val getFavorites: GetFavorites,
    private val sourceManager: SourceManager,
) {

    suspend fun await(manga: Manga) {
        withNonCancellableContext {
            with(libraryPreferences) {
                // Novel-source chapterList() is natural reading order (chapter 1 first), the
                // opposite of the manga-extension newest-first convention Mihon's own sort
                // direction preference assumes - see JsNovelSource.getMangaUpdate(). This runs
                // on every non-favorite details-screen visit (SetMangaDefaultChapterFlags.await
                // is called from MangaViewModel.init unconditionally), so it must decide the
                // direction itself rather than deferring to the global manga preference.
                val isNovel = sourceManager.get(manga.source)?.contentType == SourceContentType.NOVEL
                val sortingDirection = if (isNovel) {
                    Manga.CHAPTER_SORT_ASC
                } else {
                    sortChapterByAscendingOrDescending.get()
                }
                setMangaChapterFlags.awaitSetAllFlags(
                    mangaId = manga.id,
                    unreadFilter = filterChapterByRead.get(),
                    downloadedFilter = filterChapterByDownloaded.get(),
                    bookmarkedFilter = filterChapterByBookmarked.get(),
                    sortingMode = sortChapterBySourceOrNumber.get(),
                    sortingDirection = sortingDirection,
                    displayMode = displayChapterByNameOrNumber.get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
