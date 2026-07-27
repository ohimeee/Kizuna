package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter

/**
 * A source that provides text-based light novel content instead of image pages.
 *
 * Chapter/series metadata is shared with [Source] (an [eu.kanade.tachiyomi.source.model.SManga]
 * and [SChapter] describe a novel and its chapters just as well as a comic), so the library,
 * backup, and tracker sync layers need no novel-specific handling. The one thing that doesn't
 * carry over is [getPageList]/[Page], since a chapter here is text, not images — [getChapterText]
 * replaces it.
 */
interface NovelSource : Source {
    override val contentType: SourceContentType
        get() = SourceContentType.NOVEL

    /**
     * Fetches the textual content of a chapter (plain text or simple HTML paragraphs — rendering
     * is the reader's responsibility, not this source's).
     */
    suspend fun getChapterText(chapter: SChapter): String

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        throw UnsupportedOperationException("NovelSource has no image pages; use getChapterText")
    }
}
