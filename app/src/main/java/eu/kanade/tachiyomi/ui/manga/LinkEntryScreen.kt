package eu.kanade.tachiyomi.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.launch
import tachiyomi.domain.entrylink.interactor.GetLinkedEntry
import tachiyomi.domain.entrylink.interactor.LinkEntries
import tachiyomi.domain.entrylink.interactor.UnlinkEntries
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

        val getLinkedEntry = remember { Injekt.get<GetLinkedEntry>() }
        val linkEntries = remember { Injekt.get<LinkEntries>() }
        val unlinkEntries = remember { Injekt.get<UnlinkEntries>() }
        val getLibraryManga = remember { Injekt.get<GetLibraryManga>() }

        var isLoading by remember { mutableStateOf(true) }
        var linkedManga by remember { mutableStateOf<Manga?>(null) }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Manga>>(emptyList()) }

        LaunchedEffect(Unit) {
            linkedManga = getLinkedEntry.await(manga.id)
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
                    Column(modifier = Modifier.padding(contentPadding)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(text = stringResource(MR.strings.label_linked_title_search_hint)) },
                            modifier = Modifier.padding(16.dp),
                        )
                        if (query.isNotBlank() && results.isEmpty()) {
                            Text(
                                text = stringResource(MR.strings.label_linked_title_none),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        LazyColumn {
                            items(results) { target ->
                                ListItem(
                                    headlineContent = { Text(text = target.title) },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            linkEntries.await(manga.id, target.id)
                                            navigator.pop()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
