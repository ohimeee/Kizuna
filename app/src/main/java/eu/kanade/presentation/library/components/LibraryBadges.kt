package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.source.SourceContentType
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun DownloadsBadge(count: Int) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

/** Shared by any list/grid item that wants to show [ContentTypeBadge] - one Injekt/SourceManager
 * lookup pattern instead of repeating it at every call site. */
@Composable
internal fun rememberIsNovelSource(sourceId: Long): Boolean {
    val sourceManager = remember { Injekt.get<SourceManager>() }
    return remember(sourceId) { sourceManager.get(sourceId)?.contentType == SourceContentType.NOVEL }
}

/**
 * Marks EVERY entry in the library with its content type - a book icon for novels, an image icon
 * for manga/manhwa/manhua - the same icon language [ExtensionsScreen]'s source listing already
 * uses to distinguish novel sources from image ones. Always shown (not just for novels): leaving
 * manga entries unbadged read as "is this one just untagged?" rather than "this one is a comic" -
 * showing both makes the distinction the point, not an absence.
 */
@Composable
internal fun ContentTypeBadge(isNovel: Boolean) {
    Badge(
        imageVector = if (isNovel) Icons.Outlined.MenuBook else Icons.Outlined.Image,
        color = MaterialTheme.colorScheme.tertiary,
        iconColor = MaterialTheme.colorScheme.onTertiary,
    )
}

/**
 * [ContentTypeBadge] overlaid on the top-end corner of a bare cover, for the surfaces that render a
 * cover directly instead of going through a library grid item (manga details, History rows).
 *
 * Those places used to show the type as an icon inline before the title, inside a
 * `verticalAlignment = CenterVertically` Row - so once a title wrapped to two or three lines the
 * icon drifted to the vertical middle of the block and read as a stray glyph floating beside the
 * text. Putting it on the cover instead also matches where the Library grid already shows it, so
 * the type lives in one consistent place app-wide and titles stay plain text.
 *
 * Expects to be placed inside a [androidx.compose.foundation.layout.Box] that also holds the cover.
 */
@Composable
internal fun ContentTypeCoverBadge(isNovel: Boolean, modifier: Modifier = Modifier) {
    BadgeGroup(modifier = modifier.padding(4.dp)) {
        ContentTypeBadge(isNovel = isNovel)
    }
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
            ContentTypeBadge(isNovel = true)
            ContentTypeBadge(isNovel = false)
        }
    }
}
