package tachiyomi.domain.entrylink.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entrylink.repository.EntryLinkRepository

@Inject
class UnlinkEntries(
    private val repository: EntryLinkRepository,
) {

    suspend fun await(entryIdA: Long, entryIdB: Long): Boolean {
        return try {
            repository.unlink(entryIdA, entryIdB)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }
}
