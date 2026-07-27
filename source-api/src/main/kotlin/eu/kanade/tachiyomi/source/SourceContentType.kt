package eu.kanade.tachiyomi.source

/**
 * The kind of content a [Source] provides. Manga/manhwa/manhua are all [IMAGE] — origin and
 * language don't change the format, so they aren't split into separate types.
 */
enum class SourceContentType {
    IMAGE,
    NOVEL,
    ;

    companion object {
        fun fromValue(value: String?): SourceContentType = when (value?.lowercase()) {
            "novel" -> NOVEL
            else -> IMAGE
        }
    }
}
