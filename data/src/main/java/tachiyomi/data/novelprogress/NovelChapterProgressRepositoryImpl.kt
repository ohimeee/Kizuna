package tachiyomi.data.novelprogress

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.novelprogress.repository.NovelChapterProgressRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NovelChapterProgressRepositoryImpl(
    private val database: Database,
) : NovelChapterProgressRepository {

    override suspend fun upsert(chapterId: Long, progress: Int) {
        database.novel_chapter_progressQueries.upsert(chapterId, progress.toLong())
    }

    override fun subscribeAll(): Flow<Map<Long, Int>> {
        return database.novel_chapter_progressQueries
            .getAll { chapterId, progress -> chapterId to progress.toInt() }
            .subscribeToList()
            .map { it.toMap() }
    }
}
