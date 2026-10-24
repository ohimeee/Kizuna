package tachiyomi.domain.novelprogress.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.novelprogress.repository.NovelChapterProgressRepository

@Inject
class UpsertNovelChapterProgress(
    private val repository: NovelChapterProgressRepository,
) {

    suspend fun await(chapterId: Long, progress: Int) {
        try {
            repository.upsert(chapterId, progress)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
