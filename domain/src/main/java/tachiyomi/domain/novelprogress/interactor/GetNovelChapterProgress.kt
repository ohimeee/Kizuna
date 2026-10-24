package tachiyomi.domain.novelprogress.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.novelprogress.repository.NovelChapterProgressRepository

@Inject
class GetNovelChapterProgress(
    private val repository: NovelChapterProgressRepository,
) {

    fun subscribe(): Flow<Map<Long, Int>> {
        return repository.subscribeAll()
    }
}
