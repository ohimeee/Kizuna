package eu.kanade.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.StringResource
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.base.ContentModeFilter
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Icon + label for a [ContentModeFilter] value, shared by every screen that displays the mode. */
fun ContentModeFilter.iconAndTitleRes(): Pair<ImageVector, StringResource> = when (this) {
    ContentModeFilter.ALL -> Icons.Outlined.Apps to MR.strings.all
    ContentModeFilter.COMICS -> Icons.Outlined.Image to MR.strings.label_comics
    ContentModeFilter.NOVELS -> Icons.Outlined.MenuBook to MR.strings.label_novels
}

/**
 * App-wide Comics/Novels mode toggle (phase 5), for use in any screen's [AppBarActions] list.
 * Tapping cycles ALL -> COMICS -> NOVELS -> ALL; the icon reflects the current mode. Backed by a
 * single shared [BasePreferences.contentMode] preference, so every screen using this stays in
 * sync without any explicit cross-screen wiring.
 */
@Composable
fun contentModeAction(): AppBar.Action {
    val context = LocalContext.current
    val preference = remember { context.appGraph.basePreferences.contentMode }
    var mode by preference.asState(rememberCoroutineScope())

    val (icon, titleRes) = mode.iconAndTitleRes()

    return AppBar.Action(
        title = stringResource(titleRes),
        icon = icon,
        onClick = { mode = mode.next() },
    )
}
