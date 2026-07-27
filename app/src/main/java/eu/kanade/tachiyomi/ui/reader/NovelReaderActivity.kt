package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import eu.kanade.presentation.reader.NovelReaderScreen
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            NovelReaderScreen(
                state = state,
                preferences = preferences,
                onBack = { finish() },
                onProgress = viewModel::updateProgress,
                onPrevChapter = viewModel::loadPreviousChapter,
                onNextChapter = viewModel::loadNextChapter,
            )
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveHistory()
    }
}
