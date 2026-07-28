package eu.kanade.domain.base

import eu.kanade.tachiyomi.source.SourceContentType

/**
 * App-wide Comics/Novels mode toggle (phase 5). Filters Library/Updates/History/Browse down to
 * entries whose source matches, or shows everything when [ALL].
 */
enum class ContentModeFilter {
    ALL,
    COMICS,
    NOVELS,
    ;

    fun matches(contentType: SourceContentType): Boolean = when (this) {
        ALL -> true
        COMICS -> contentType == SourceContentType.IMAGE
        NOVELS -> contentType == SourceContentType.NOVEL
    }

    fun next(): ContentModeFilter = when (this) {
        ALL -> COMICS
        COMICS -> NOVELS
        NOVELS -> ALL
    }
}
