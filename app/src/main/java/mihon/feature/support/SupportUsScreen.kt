package mihon.feature.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.components.material.padding

// Kizuna: Mihon's original SupportUsScreen linked directly to Mihon's own Patreon,
// OpenCollective, and Discord (via Constants.URL_DONATE_*/URL_DISCORD). Those must not be
// presented under the Kizuna name, so this screen is a placeholder until Kizuna has its own
// support links (see README/NOTICE for attribution to the upstream Mihon project).
class SupportUsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                AppBar(
                    title = "Support Us",
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).padding(MaterialTheme.padding.medium)) {
                Text(
                    text = "Kizuna doesn't have its own donation platforms set up yet. " +
                        "Check the project's README for current links, if any.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
