package tachiyomi.domain.manga.interactor

import eu.kanade.tachiyomi.source.SourceContentType
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager

class NetworkToLocalManga(
    private val mangaRepository: MangaRepository,
    private val sourceManager: SourceManager,
) {

    suspend operator fun invoke(manga: Manga): Manga {
        return invoke(listOf(manga)).single()
    }

    suspend operator fun invoke(manga: List<Manga>): List<Manga> {
        return mangaRepository.insertNetworkManga(manga.map(::applyDefaultChapterSort))
    }

    /**
     * Mihon's chapter-sort default (`CHAPTER_SORT_DESC`, newest chapter first) assumes the
     * manga-extension convention of `chapterList()` returning newest-first. Novel-source JS
     * plugins return chapters in natural reading order instead (see `JsNovelSource
     * .getMangaUpdate()`), so a novel should default to ascending - chapter 1 shown first,
     * matching what a reader actually expects - not Mihon's manga-extension default. Harmless to
     * apply unconditionally here: the underlying insert (`insertNetworkManga` in mangas.sq) only
     * ever writes `chapterFlags` on a brand-new row (`WHERE NOT EXISTS`), so this never overwrites
     * an existing user's own sort choice on a manga/novel already in the library.
     */
    private fun applyDefaultChapterSort(manga: Manga): Manga {
        val isNovel = sourceManager.get(manga.source)?.contentType == SourceContentType.NOVEL
        if (!isNovel) return manga
        return manga.copy(chapterFlags = manga.chapterFlags or Manga.CHAPTER_SORT_ASC)
    }
}
