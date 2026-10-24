package tachiyomi.domain.novelprogress.repository

import kotlinx.coroutines.flow.Flow

/**
 * How far through a novel chapter the reader got, as a 0..100 percent. A chapter only stores a
 * paragraph index (`lastPageRead`), and the paragraph total is only known once the text is loaded,
 * so the percent is saved alongside it for screens that show a chapter without opening it.
 */
interface NovelChapterProgressRepository {

    suspend fun upsert(chapterId: Long, progress: Int)

    /** Every stored percent, keyed by chapter id. */
    fun subscribeAll(): Flow<Map<Long, Int>>
}
