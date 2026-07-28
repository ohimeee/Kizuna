package eu.kanade.tachiyomi.ui.browse.novel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.source.novel.JsNovelSource
import eu.kanade.tachiyomi.source.novel.NovelSourceCatalog
import eu.kanade.tachiyomi.source.novel.NovelSourceCatalogEntry
import eu.kanade.tachiyomi.source.novel.NovelSourceInstaller
import eu.kanade.tachiyomi.source.novel.NovelSourceLoader
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSourcesViewModel(
    private val context: Application = Injekt.get(),
) : StateViewModel<NovelSourcesViewModel.State>(State()) {

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launchIO {
            mutableState.update { it.copy(isLoading = true, error = null) }
            try {
                val entries = NovelSourceCatalog.fetch()
                val items = entries.map { entry ->
                    val file = NovelSourceLoader.directory(context).resolve("${entry.id}.js")
                    val installedVersion = if (file.exists()) {
                        try {
                            JsNovelSource(file).version
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Failed to read installed version for ${entry.id}" }
                            null
                        }
                    } else {
                        null
                    }
                    NovelSourceCatalogItem(entry, installedVersion)
                }
                mutableState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch novel source catalog" }
                mutableState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun install(entry: NovelSourceCatalogEntry) {
        viewModelScope.launchIO {
            mutableState.update { it.copy(installingIds = it.installingIds + entry.id) }
            try {
                NovelSourceInstaller.install(context, entry)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to install novel source ${entry.id}" }
            } finally {
                mutableState.update { it.copy(installingIds = it.installingIds - entry.id) }
            }
            refresh()
        }
    }

    fun uninstall(entry: NovelSourceCatalogEntry) {
        viewModelScope.launchIO {
            NovelSourceInstaller.uninstall(context, entry.id)
            refresh()
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: List<NovelSourceCatalogItem> = emptyList(),
        val installingIds: Set<String> = emptySet(),
        val error: String? = null,
    ) {
        val isEmpty = !isLoading && items.isEmpty() && error == null
    }

    @Immutable
    data class NovelSourceCatalogItem(
        val entry: NovelSourceCatalogEntry,
        val installedVersion: String?,
    ) {
        val isInstalled get() = installedVersion != null
        val hasUpdate get() = isInstalled && installedVersion != entry.version
    }
}
