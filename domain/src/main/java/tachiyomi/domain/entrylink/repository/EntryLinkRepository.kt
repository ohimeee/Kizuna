package tachiyomi.domain.entrylink.repository

import kotlinx.coroutines.flow.Flow

/**
 * Bonds two library entries together (e.g. a comic and its novel adaptation). The link is a pure
 * navigation shortcut — each entry keeps its own independently tracked reading progress.
 */
interface EntryLinkRepository {

    suspend fun link(entryIdA: Long, entryIdB: Long, relationType: String)

    suspend fun unlink(entryIdA: Long, entryIdB: Long)

    /** Returns the id of the entry linked to [entryId], if any. */
    suspend fun getLinkedEntryId(entryId: Long): Long?

    /** Reactive version of [getLinkedEntryId]. */
    fun subscribe(entryId: Long): Flow<Long?>
}
