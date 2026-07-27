package eu.kanade.tachiyomi.source.novel

import android.content.Context
import eu.kanade.tachiyomi.source.NovelSource
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Loads [NovelSource]s from `.js` plugin files placed in [directory].
 *
 * There's no install/download UI wired up yet (that's the natural next step once this MVP is
 * proven) — for now, sources are picked up by dropping a `.js` file (see
 * `docs/novel-sources/README.md` for the plugin contract) into this app-private directory, e.g.
 * via `adb push` during development.
 */
object NovelSourceLoader {
    private const val DIR_NAME = "novel_sources"

    fun directory(context: Context): File = context.filesDir.resolve(DIR_NAME).apply { mkdirs() }

    /**
     * Scans [directory] and instantiates a [JsNovelSource] per `.js` file. A file that fails to
     * load (bad script, missing `Register(...)` call, etc.) is logged and skipped rather than
     * failing the whole scan.
     */
    fun loadAll(context: Context): List<NovelSource> {
        val files = directory(context).listFiles { file -> file.isFile && file.extension == "js" }
            ?: return emptyList()

        return files.mapNotNull { file ->
            try {
                JsNovelSource(file).also { it.id }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load novel source from ${file.name}" }
                null
            }
        }
    }
}
