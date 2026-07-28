package eu.kanade.tachiyomi.source.novel

import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Installs/updates/removes `.js` novel source files (see [NovelSourceCatalogEntry],
 * [NovelSourceLoader]) - the download/file-management counterpart to the APK-based
 * `eu.kanade.tachiyomi.extension.ExtensionManager`, just much simpler since there's no package
 * manager involved, only a text file in app-private storage.
 */
object NovelSourceInstaller {

    /** Filename installed sources are tracked under - also how [isInstalled] checks presence. */
    private fun fileFor(context: Context, id: String) =
        NovelSourceLoader.directory(context).resolve("$id.js")

    fun isInstalled(context: Context, id: String): Boolean = fileFor(context, id).exists()

    suspend fun install(
        context: Context,
        entry: NovelSourceCatalogEntry,
        network: NetworkHelper = Injekt.get(),
    ) = withIOContext {
        val script = network.client.newCall(GET(entry.fileUrl)).awaitSuccess().body.string()
        fileFor(context, entry.id).writeText(script)
        NovelSourceLoader.notifyChanged()
    }

    suspend fun uninstall(context: Context, id: String) = withIOContext {
        fileFor(context, id).delete()
        NovelSourceLoader.notifyChanged()
    }
}
