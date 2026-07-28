package eu.kanade.tachiyomi.source.novel

import android.content.Context
import eu.kanade.tachiyomi.source.NovelSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Loads [NovelSource]s from `.js` plugin files placed in [directory].
 *
 * Sources are picked up either by [eu.kanade.tachiyomi.source.novel.NovelSourceInstaller]
 * (install/update/uninstall UI, backed by a catalog JSON) or by dropping a `.js` file (see
 * `docs/novel-sources/README.md` for the plugin contract) into this app-private directory
 * directly, e.g. via `adb push` during development — either way, call [notifyChanged] afterwards
 * so [eu.kanade.tachiyomi.source.AndroidSourceManager] picks up the change without needing a full
 * app restart.
 */
object NovelSourceLoader {
    private const val DIR_NAME = "novel_sources"

    // replay = 1 so a late collector (AndroidSourceManager's init, which subscribes after this
    // object already exists) still gets an initial value to combine with.
    private val _changes = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Call after installing, updating, or removing a `.js` file in [directory]. */
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

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
