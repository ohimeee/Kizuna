package eu.kanade.tachiyomi.source.novel

import kotlinx.serialization.Serializable

/**
 * One entry of the novel source catalog index (see [NovelSourceCatalog]) - a `.js` plugin file
 * available to install, not yet an installed [eu.kanade.tachiyomi.source.NovelSource].
 */
@Serializable
data class NovelSourceCatalogEntry(
    val id: String,
    val name: String,
    val lang: String,
    val version: String,
    val fileUrl: String,
)
