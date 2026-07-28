package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.browse.novel.NovelSourcesViewModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun NovelSourcesScreen(
    state: NovelSourcesViewModel.State,
    contentPadding: PaddingValues,
    onClickInstall: (NovelSourcesViewModel.NovelSourceCatalogItem) -> Unit,
    onClickUninstall: (NovelSourcesViewModel.NovelSourceCatalogItem) -> Unit,
    onClickRetry: () -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.error != null -> EmptyScreen(
            message = state.error,
            modifier = Modifier.padding(contentPadding),
            actions = listOf(
                EmptyScreenAction(
                    stringRes = MR.strings.action_retry,
                    icon = Icons.Outlined.Refresh,
                    onClick = onClickRetry,
                ),
            ),
        )
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            ScrollbarLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(
                    items = state.items,
                    key = { it.entry.id },
                ) { item ->
                    NovelSourceCatalogItemRow(
                        item = item,
                        isInstalling = item.entry.id in state.installingIds,
                        onClickInstall = { onClickInstall(item) },
                        onClickUninstall = { onClickUninstall(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelSourceCatalogItemRow(
    item: NovelSourcesViewModel.NovelSourceCatalogItem,
    isInstalling: Boolean,
    onClickInstall: () -> Unit,
    onClickUninstall: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(text = item.entry.name) },
        supportingContent = {
            val version = item.installedVersion?.let { "$it → ${item.entry.version}".takeIf { _ -> item.hasUpdate } }
                ?: item.entry.version
            Text(text = "${item.entry.lang.uppercase()} · $version")
        },
        trailingContent = {
            when {
                isInstalling -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                item.hasUpdate -> TextButton(onClick = onClickInstall) {
                    Text(
                        text = stringResource(MR.strings.ext_update),
                        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                    )
                }
                item.isInstalled -> TextButton(onClick = onClickUninstall) {
                    Text(text = stringResource(MR.strings.ext_uninstall))
                }
                else -> TextButton(onClick = onClickInstall) {
                    Text(
                        text = stringResource(MR.strings.ext_install),
                        style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
