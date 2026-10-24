package tachiyomi.data.entrylink

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToOneOrNull
import tachiyomi.domain.entrylink.repository.EntryLinkRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class EntryLinkRepositoryImpl(
    private val database: Database,
) : EntryLinkRepository {

    override suspend fun link(entryIdA: Long, entryIdB: Long, relationType: String) {
        val (a, b) = canonicalPair(entryIdA, entryIdB)
        database.entry_linkQueries.insert(a, b, relationType)
    }

    override suspend fun unlink(entryIdA: Long, entryIdB: Long) {
        val (a, b) = canonicalPair(entryIdA, entryIdB)
        database.entry_linkQueries.delete(a, b)
    }

    override suspend fun getLinkedEntryId(entryId: Long): Long? {
        return database.entry_linkQueries.getLinkedEntryId(entryId).awaitAsOneOrNull()
    }

    override fun subscribe(entryId: Long): Flow<Long?> {
        return database.entry_linkQueries.getLinkedEntryId(entryId).subscribeToOneOrNull()
    }

    /** Orders a pair so (a, b) and (b, a) always map to the same row, per entry_link.sq's index. */
    private fun canonicalPair(entryIdA: Long, entryIdB: Long): Pair<Long, Long> {
        return if (entryIdA <= entryIdB) entryIdA to entryIdB else entryIdB to entryIdA
    }
}
