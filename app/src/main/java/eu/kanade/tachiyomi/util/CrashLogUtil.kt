package eu.kanade.tachiyomi.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CrashLogUtil(
    private val context: Context,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val preferences: BasePreferences = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) {

    /**
     * Writes a log file for the Settings > Advanced button and the crash screen, then toasts
     * where it went and opens a share sheet.
     */
    suspend fun dumpLogs(exception: Throwable? = null) = withNonCancellableContext {
        try {
            val (uri, savedToStorage, fileName) = writeLogFile(exception)
            withUIContext {
                context.toast(if (savedToStorage) "Saved to Kizuna/logs/$fileName" else "Saved log (storage folder not set up)")
            }
            context.startActivity(uri.toShareIntent(context, "text/plain"))
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to dump crash logs" }
            withUIContext { context.toast("Failed to get logs") }
        }
    }

    /**
     * Dumps this app's own process logs (all priorities, not just errors - a hang has no
     * exception to log at Error level) into a timestamped file under the user's storage folder
     * (`<base>/logs/`, e.g. `Kizuna/logs/` by default), so it survives to be inspected/shared
     * later instead of only existing as a share-sheet-only temp file. Scoped to this app's own
     * PID rather than the whole device's logcat buffer - unscoped grabs mostly unrelated system
     * noise and pushes this app's own lines out of the (small, rotating) buffer.
     */
    private suspend fun writeLogFile(exception: Throwable?): Triple<Uri, Boolean, String> = withContext(Dispatchers.IO) {
        // redirectErrorStream merges stderr into the same stream being read below - without it,
        // logcat filling the (unread) stderr pipe while this coroutine blocks reading stdout can
        // deadlock the process indefinitely.
        val process = ProcessBuilder("logcat", "-d", "-v", "year", "-v", "zone", "--pid=${Process.myPid()}")
            .redirectErrorStream(true)
            .start()
        // Never written to, but left open by the OS otherwise - leaks a file descriptor per call
        // if not closed explicitly.
        process.outputStream.close()
        val logcatOutput = try {
            process.inputStream.bufferedReader().use { it.readText() }
        } finally {
            process.waitFor()
            process.destroy()
        }

        val content = buildString {
            append(getDebugInfo())
            append("\n\n")
            getExtensionsInfo()?.let { append(it); append("\n\n") }
            exception?.let { append(it.stackTraceToString()); append("\n\n") }
            append(logcatOutput)
        }

        // Includes milliseconds - two dumps landing in the same wall-clock second (e.g. a crash
        // immediately after a reader failure) would otherwise collide on the same filename.
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS").format(OffsetDateTime.now())
        val fileName = "kizuna_log_$timestamp.txt"

        // Prefer the user's storage folder (Kizuna/logs/) so the dump survives to be read later,
        // but fall back to the app's always-available cache dir if that folder isn't set up yet
        // (e.g. right after install, before onboarding) - this tool exists to help when things are
        // already going wrong, it shouldn't itself fail silently.
        val logsDir = storageManager.getLogsDirectory()
        if (logsDir != null) {
            val logFile = logsDir.createFile(fileName) ?: error("Could not create log file")
            logFile.openOutputStream().use { it.write(content.toByteArray()) }
            Triple(logFile.uri, true, fileName)
        } else {
            val file = context.createFileInCacheDir(fileName)
            file.writeText(content)
            Triple(file.getUriCompat(context), false, fileName)
        }
    }

    fun getDebugInfo(): String {
        return """
            App ID: ${BuildConfig.APPLICATION_ID}
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Installation ID: ${preferences.installationId.get()}
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            WebView: ${WebViewUtil.getVersion(context)}
            Current time: ${OffsetDateTime.now(ZoneId.systemDefault())}
        """.trimIndent()
    }

    private fun getExtensionsInfo(): String? {
        val availableExtensions = extensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        val extensionInfoList = extensionManager.installedExtensionsFlow.value
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }
}
