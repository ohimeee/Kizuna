package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import eu.kanade.presentation.reader.NovelReaderScreen
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Reader activity for [eu.kanade.tachiyomi.source.NovelSource] chapters. A separate, pure-Compose
 * activity rather than a new mode on [ReaderActivity], since the existing reader is built around
 * View-based image viewers (ViewPager2/RecyclerView) that a text reader has no use for.
 */
class NovelReaderActivity : BaseActivity() {

    companion object {
        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?): Intent {
            return Intent(context, NovelReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private val viewModel by viewModels<NovelReaderViewModel>()
    private val preferences = Injekt.get<NovelReaderPreferences>()

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, window.decorView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        preferences.keepScreenOn.changes()
            .onEach { setKeepScreenOn(it) }
            .launchIn(lifecycleScope)

        if (viewModel.needsInit()) {
            val mangaId = intent.extras?.getLong("manga", -1) ?: -1L
            val chapterId = intent.extras?.getLong("chapter", -1) ?: -1L
            if (mangaId == -1L || chapterId == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(this, mangaId.hashCode(), Notifications.ID_NEW_CHAPTERS)

            lifecycleScope.launch {
                val result = viewModel.init(mangaId, chapterId)
                if (result.isFailure) {
                    finish()
                }
            }
        }

        setComposeContent {
            val state by viewModel.state.collectAsState()
            val fullscreen by preferences.fullscreen.collectAsState()
            var controlsVisible by remember { mutableStateOf(false) }

            // Both system bars follow the exact same tap-to-hide state as the in-app chrome -
            // hidden on chapter open, shown/hidden together on tap. "Fullscreen" forces both
            // hidden regardless. (Battery & time is a separate, self-contained Compose overlay -
            // see NovelReaderScreen - not tied to the real system status bar, so it can stay
            // visible independent of this without fighting over who controls the status bar.)
            LaunchedEffect(fullscreen, controlsVisible) {
                setSystemBarsVisible(!fullscreen && controlsVisible)
            }

            // The two system bars sit on different backgrounds, so their icons are tinted
            // independently:
            //   - Status bar: the top app bar paints its chrome behind it (see the `windowInsets`
            //     note in NovelReaderScreen) and the status bar only ever shows while that chrome
            //     is up, so it follows the *app* theme's surface.
            //   - Navigation bar: the reading page - and the footer, which shares the page color -
            //     is what paints down there, so it follows the *reading* theme.
            // Getting this wrong is what left the clock/battery/wifi white-on-white (and the status
            // bar looking missing rather than hidden) on a light reading theme in a dark app theme.
            val readerTheme by preferences.theme.collectAsState()
            val pageBackground = if (readerTheme == NovelReaderTheme.FOLLOW_SYSTEM) {
                MaterialTheme.colorScheme.background
            } else {
                Color(readerTheme.backgroundColor)
            }
            val lightChrome = MaterialTheme.colorScheme.surface.luminance() > 0.5f
            val lightPage = pageBackground.luminance() > 0.5f
            LaunchedEffect(lightChrome, lightPage) {
                windowInsetsController.isAppearanceLightStatusBars = lightChrome
                windowInsetsController.isAppearanceLightNavigationBars = lightPage
            }

            NovelReaderScreen(
                state = state,
                preferences = preferences,
                onBack = { finish() },
                onProgress = viewModel::updateProgress,
                onPrevChapter = viewModel::loadPreviousChapter,
                onNextChapter = viewModel::loadNextChapter,
                onOpenInWebView = { openInWebView() },
                onControlsVisibilityChanged = { controlsVisible = it },
            )
        }
    }

    /**
     * This Activity is launchMode="singleTask", so tapping a *different* chapter (e.g. spamming
     * entries in History) while a reader is already open reuses this same instance instead of
     * creating a new one - delivered here, not to [onCreate]. Without this override the new
     * chapter/manga extras were silently discarded and the reader just kept whatever state the
     * first-ever load left behind (including a leftover "no content" error from an unrelated
     * chapter, since the state was never for the newly-tapped one to begin with).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val mangaId = intent.extras?.getLong("manga", -1) ?: -1L
        val chapterId = intent.extras?.getLong("chapter", -1) ?: -1L
        if (mangaId == -1L || chapterId == -1L) return

        NotificationReceiver.dismissNotification(this, mangaId.hashCode(), Notifications.ID_NEW_CHAPTERS)
        viewModel.openNewTarget(mangaId, chapterId)
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveHistory()
    }

    private fun openInWebView() {
        val url = viewModel.getChapterUrl() ?: return
        startActivity(WebViewActivity.newIntent(this, url, title = viewModel.state.value.chapter?.name))
    }

    private fun setSystemBarsVisible(visible: Boolean) {
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
