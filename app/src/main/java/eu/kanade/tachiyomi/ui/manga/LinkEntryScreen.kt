package eu.kanade.tachiyomi.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.launch
import mihon.app.di.appGraph
import tachiyomi.domain.entrylink.interactor.GetLinkedEntry
import tachiyomi.domain.entrylink.interactor.LinkEntries
import tachiyomi.domain.entrylink.interactor.UnlinkEntries
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Lets the user bond [manga] to another library entry (its comic/novel counterpart), or unlink
 * it if already bonded. Self-contained on purpose — it calls the entry-link interactors
 * directly rather than going through MangaViewModel, so this feature doesn't have to thread new
 * state through that already-large screen beyond the single entry point that opens it.
 */
class LinkEntryScreen(private val manga: Manga) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        val context = LocalContext.current
        val getLinkedEntry = remember { context.appGraph.getLinkedEntry }
        val linkEntries = remember { context.appGraph.linkEntries }
        val unlinkEntries = remember { context.appGraph.unlinkEntries }
        val getLibraryManga = remember { context.appGraph.getLibraryManga }
        val getLinkSuggestions = remember { context.appGraph.getLinkSuggestions }

        var isLoading by remember { mutableStateOf(true) }
        var linkedManga by remember { mutableStateOf<Manga?>(null) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Manga>>(emptyList()) }
        var suggestions by remember { mutableStateOf<List<Manga>>(emptyList()) }

        LaunchedEffect(Unit) {
            linkedManga = getLinkedEntry.await(manga.id)
            suggestions = getLinkSuggestions.await(manga)
            isLoading = false
        }

        LaunchedEffect(query) {
            results = if (query.isBlank()) {
                emptyList()
            } else {
                getLibraryManga.await()
                    .map { it.manga }
                    .filter { it.id != manga.id && it.title.contains(query, ignoreCase = true) }
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_link_related_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                linkedManga != null -> {
                    val linked = linkedManga!!
                    Column(modifier = Modifier.padding(contentPadding).padding(16.dp)) {
                        Text(text = linked.title)
                        TextButton(
                            onClick = {
                                scope.launch {
                                    unlinkEntries.await(manga.id, linked.id)
                                    navigator.pop()
                                }
                            },
                        ) {
                            Text(text = stringResource(MR.strings.action_unlink_related_title))
                        }
                    }
                }
                else -> {
                    val onPick: (Manga) -> Unit = { target ->
                        scope.launch {
                            linkEntries.await(manga.id, target.id)
                            navigator.pop()
                        }
                    }
                    // One list so the suggestions come first and scroll away with the rest
                    LazyColumn(contentPadding = contentPadding) {
                        if (suggestions.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(MR.strings.label_link_suggestions),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(suggestions) { target ->
                                ListItem(
                                    headlineContent = { Text(text = target.title) },
                                    modifier = Modifier.clickable { onPick(target) },
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text(text = stringResource(MR.strings.label_linked_title_search_hint)) },
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        if (query.isNotBlank() && results.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(MR.strings.label_linked_title_none),
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        items(results) { target ->
                            ListItem(
                                headlineContent = { Text(text = target.title) },
                                modifier = Modifier.clickable { onPick(target) },
                            )
                        }
                    }
                }
            }
        }
    }
}
