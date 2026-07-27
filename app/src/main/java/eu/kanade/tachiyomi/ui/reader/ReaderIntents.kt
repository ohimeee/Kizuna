package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.source.SourceContentType
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Builds the intent to open a chapter's reader, routing to [NovelReaderActivity] or
 * [ReaderActivity] depending on the manga's source content type. Every call site that used to
 * call `ReaderActivity.newIntent` directly should go through this instead.
 */
fun readerIntent(
    context: Context,
    mangaId: Long?,
    chapterId: Long?,
    getManga: GetManga = Injekt.get(),
    sourceManager: SourceManager = Injekt.get(),
): Intent {
    val contentType = mangaId?.let { id ->
        runBlocking { getManga.await(id) }?.let { sourceManager.get(it.source)?.contentType }
    }

    return if (contentType == SourceContentType.NOVEL) {
        NovelReaderActivity.newIntent(context, mangaId, chapterId)
    } else {
        ReaderActivity.newIntent(context, mangaId, chapterId)
    }
}
