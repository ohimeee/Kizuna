package tachiyomi.domain.chapter.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import mihon.core.common.extensions.EMPTY

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val chapterNumber: Double,
    val scanlator: String?,
    val lastModifiedAt: Long,
    val version: Long,
    val memo: JsonObject,
) {
    val isRecognizedNumber: Boolean
        get() = chapterNumber >= 0f

    /**
     * Whether the source has flagged this chapter as paywalled/premium (e.g. Webnovel's VIP
     * chapters). Sources signal this via [memo]'s "kizuna.locked" key (see JsNovelSource) rather
     * than a dedicated DB column, since memo already exists for source-specific extras and is
     * already synced/persisted end-to-end.
     */
    val isLocked: Boolean
        get() = (memo["kizuna.locked"] as? JsonPrimitive)?.booleanOrNull ?: false

    fun copyFrom(other: Chapter): Chapter {
        return copy(
            name = other.name,
            url = other.url,
            dateUpload = other.dateUpload,
            chapterNumber = other.chapterNumber,
            scanlator = other.scanlator?.ifBlank { null },
        )
    }

    companion object {
        fun create() = Chapter(
            id = -1,
            mangaId = -1,
            read = false,
            bookmark = false,
            lastPageRead = 0,
            dateFetch = 0,
            sourceOrder = 0,
            url = "",
            name = "",
            dateUpload = -1,
            chapterNumber = -1.0,
            scanlator = null,
            lastModifiedAt = 0,
            version = 1,
            memo = JsonObject.EMPTY,
        )
    }
}
