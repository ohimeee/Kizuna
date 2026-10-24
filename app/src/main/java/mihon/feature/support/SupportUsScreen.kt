package mihon.feature.support

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.Screen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Favorite
import mihon.icons.materialsymbols.rounded.Public
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// Kizuna: Mihon's original SupportUsScreen linked directly to Mihon's own Patreon,
// OpenCollective, and Discord (via Constants.URL_DONATE_*/URL_DISCORD). Those must not be
// presented under the Kizuna name, so they are not used here. This screen now points to
// Kizuna's own Ko-fi page (Constants.URL_KOFI), plus a small credit row linking to Mihon's
// website (see README/NOTICE for attribution to the upstream Mihon project). The row is a
// plain credit link; Mihon's donation platforms are deliberately not surfaced as Kizuna's own.
class SupportUsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.label_support_us),
                    navigateUp = navigator::pop,
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(MR.strings.support_us_message),
                    modifier = Modifier.padding(MaterialTheme.padding.medium),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Button(
                    onClick = { uriHandler.openUri(Constants.URL_KOFI) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Favorite,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(MR.strings.support_us_kofi),
                        modifier = Modifier.padding(start = MaterialTheme.padding.small),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.medium))

                TextPreferenceWidget(
                    title = stringResource(MR.strings.support_us_mihon),
                    icon = MaterialSymbols.Rounded.Public,
                    onPreferenceClick = { uriHandler.openUri(MIHON_URL) },
                )
            }
        }
    }

    private companion object {
        const val MIHON_URL = "https://mihon.app"
    }
}
