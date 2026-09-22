package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.source.SourceContentType
import kotlinx.coroutines.runBlocking
import mihon.app.di.appGraph

/**
 * Builds the intent to open a chapter's reader, routing to [NovelReaderActivity] or
 * [ReaderActivity] depending on the manga's source content type. Every call site that used to
 * call `ReaderActivity.newIntent` directly should go through this instead.
 */
fun readerIntent(
    context: Context,
    mangaId: Long?,
    chapterId: Long?,
): Intent {
    val graph = context.appGraph
    val contentType = mangaId?.let { id ->
        // SourceManager's surface is suspend as of the 0.20.4 merge, so the source lookup has to
        // happen inside the same blocking block as the manga lookup.
        runBlocking {
            graph.getManga.await(id)?.let { graph.sourceManager.get(it.source)?.contentType }
        }
    }

    return if (contentType == SourceContentType.NOVEL) {
        NovelReaderActivity.newIntent(context, mangaId, chapterId)
    } else {
        ReaderActivity.newIntent(context, mangaId, chapterId)
    }
}
