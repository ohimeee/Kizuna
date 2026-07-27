package tachiyomi.domain.entrylink.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entrylink.repository.EntryLinkRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga

/** Resolves the manga linked to [entryId] (its comic/novel counterpart), if any. */
class GetLinkedEntry(
    private val repository: EntryLinkRepository,
    private val getManga: GetManga,
) {

    suspend fun await(entryId: Long): Manga? {
        return try {
            val linkedId = repository.getLinkedEntryId(entryId) ?: return null
            getManga.await(linkedId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    fun subscribe(entryId: Long): Flow<Manga?> {
        return repository.subscribe(entryId).map { linkedId -> linkedId?.let { getManga.await(it) } }
    }
}
