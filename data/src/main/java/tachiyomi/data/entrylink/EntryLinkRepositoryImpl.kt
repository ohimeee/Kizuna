package tachiyomi.data.entrylink

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.entrylink.repository.EntryLinkRepository

class EntryLinkRepositoryImpl(
    private val database: Database,
) : EntryLinkRepository {

    override suspend fun link(entryIdA: Long, entryIdB: Long, relationType: String) {
        val (a, b) = canonicalPair(entryIdA, entryIdB)
        database.entry_linksQueries.insert(a, b, relationType)
    }

    override suspend fun unlink(entryIdA: Long, entryIdB: Long) {
        val (a, b) = canonicalPair(entryIdA, entryIdB)
        database.entry_linksQueries.delete(a, b)
    }

    override suspend fun getLinkedEntryId(entryId: Long): Long? {
        return database.entry_linksQueries.getLinkedEntryId(entryId).awaitAsOneOrNull()
    }

    override fun subscribe(entryId: Long): Flow<Long?> {
        return database.entry_linksQueries.getLinkedEntryId(entryId).subscribeToOneOrNull()
    }

    /** Orders a pair so (a, b) and (b, a) always map to the same row, per entry_links.sq's index. */
    private fun canonicalPair(entryIdA: Long, entryIdB: Long): Pair<Long, Long> {
        return if (entryIdA <= entryIdB) entryIdA to entryIdB else entryIdB to entryIdA
    }
}
