package tachiyomi.domain.entrylink.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entrylink.repository.EntryLinkRepository

@Inject
class LinkEntries(
    private val repository: EntryLinkRepository,
) {

    suspend fun await(entryIdA: Long, entryIdB: Long, relationType: String = RELATION_ADAPTATION): Boolean {
        if (entryIdA == entryIdB) return false
        return try {
            repository.link(entryIdA, entryIdB, relationType)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    companion object {
        const val RELATION_ADAPTATION = "adaptation"
    }
}
