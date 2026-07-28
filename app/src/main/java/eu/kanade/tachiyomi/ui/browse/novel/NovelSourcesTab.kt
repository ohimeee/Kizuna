package eu.kanade.tachiyomi.ui.browse.novel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.browse.NovelSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.novelSourcesTab(): TabContent {
    val viewModel = viewModel<NovelSourcesViewModel>()
    val state by viewModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_novels,
        actions = listOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_retry),
                icon = Icons.Outlined.Refresh,
                onClick = viewModel::refresh,
            ),
        ),
        content = { contentPadding, _ ->
            NovelSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickInstall = { viewModel.install(it.entry) },
                onClickUninstall = { viewModel.uninstall(it.entry) },
                onClickRetry = viewModel::refresh,
            )
        },
    )
}
