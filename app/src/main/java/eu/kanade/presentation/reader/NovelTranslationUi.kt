package eu.kanade.presentation.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.translation.GlossaryEntry
import eu.kanade.tachiyomi.ui.reader.translation.GlossaryGender
import eu.kanade.tachiyomi.ui.reader.translation.GlossaryType

/**
 * Material's "translate" glyph (文A). The extended Material icon pack isn't a dependency, so the
 * path is inlined here, same as the reader's other custom-drawn icons.
 */
val TranslateIcon: ImageVector by lazy {
    ImageVector.Builder("Translate", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(
                "M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17" +
                    "C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56" +
                    "l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12z" +
                    "m-2.62 7l1.62-4.33L19.12 17h-3.24z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/**
 * Top-bar translate button. Off: one tap switches translation on for this novel. On: the icon is
 * tinted and a tap opens a menu (show original, glossary, retranslate).
 */
@Composable
fun NovelTranslateAction(
    enabled: Boolean,
    onToggle: () -> Unit,
    onOpenGlossary: () -> Unit,
    onRetranslate: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { if (enabled) menuOpen = true else onToggle() }) {
            Icon(
                imageVector = TranslateIcon,
                contentDescription = if (enabled) "Translation options" else "Translate",
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Show original") },
                onClick = {
                    menuOpen = false
                    onToggle()
                },
            )
            DropdownMenuItem(
                text = { Text("Glossary") },
                onClick = {
                    menuOpen = false
                    onOpenGlossary()
                },
            )
            DropdownMenuItem(
                text = { Text("Retranslate chapter") },
                onClick = {
                    menuOpen = false
                    onRetranslate()
                },
            )
        }
    }
}

/**
 * Banner over the page while a chapter translates, or when it failed. It stays visible with the
 * chrome hidden, since that's exactly when the reader would otherwise wonder why nothing changed.
 */
@Composable
fun NovelTranslationStatus(
    isTranslating: Boolean,
    progress: Pair<Int, Int>?,
    error: String?,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isTranslating && error == null) return
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        if (error != null) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Text(text = "Translation failed: $error", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                // Parts run in parallel and appear as they land, so overall progress says more
                // than "part 1 of 6" (which read as six waits in a row).
                val (done, total) = progress ?: (0 to 0)
                Text(
                    text = if (total > 1 && done > 0) "Translating… ${done * 100 / total}%" else "Translating…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Glossary list for one novel, shown in a bottom sheet from the translate menu. */
@Composable
fun NovelGlossarySheet(
    entries: List<GlossaryEntry>,
    onSave: (index: Int?, entry: GlossaryEntry) -> Unit,
    onDelete: (index: Int) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onRetranslate: () -> Unit,
) {
    // null = closed, -1 = adding, otherwise editing that index
    var editing by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = "Glossary",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            text = "Names and terms here are always translated the way you set them. Changes apply to " +
                "chapters translated from now on; use Retranslate for ones already done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            TextButton(onClick = { editing = -1 }) { Text("Add") }
            TextButton(onClick = onImport) { Text("Import CSV") }
            TextButton(onClick = onExport, enabled = entries.isNotEmpty()) { Text("Export CSV") }
            TextButton(onClick = onRetranslate) { Text("Retranslate") }
        }
        HorizontalDivider()
        if (entries.isEmpty()) {
            Text(
                text = "No entries yet. Add the main characters first: a fixed spelling and gender for each " +
                    "keeps names and pronouns consistent from chapter to chapter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(entries) { index, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = index }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "${entry.raw}  →  ${entry.translated.ifBlank { "(not set)" }}",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = buildString {
                            append(entry.type.label)
                            if (entry.type == GlossaryType.CHARACTER && entry.gender != GlossaryGender.UNDECIDED) {
                                append(" · ").append(entry.gender.label)
                            }
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    editing?.let { index ->
        GlossaryEntryDialog(
            initial = entries.getOrNull(index),
            onDismiss = { editing = null },
            onSave = { entry ->
                onSave(index.takeIf { it >= 0 }, entry)
                editing = null
            },
            onDelete = if (index >= 0) {
                {
                    onDelete(index)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun GlossaryEntryDialog(
    initial: GlossaryEntry?,
    onDismiss: () -> Unit,
    onSave: (GlossaryEntry) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(initial?.type ?: GlossaryType.CHARACTER) }
    var raw by remember { mutableStateOf(initial?.raw.orEmpty()) }
    var translated by remember { mutableStateOf(initial?.translated.orEmpty()) }
    var gender by remember { mutableStateOf(initial?.gender ?: GlossaryGender.UNDECIDED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add entry" else "Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlossaryType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("Original (as in the raw text)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = translated,
                    onValueChange = { translated = it },
                    label = { Text("Translation") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == GlossaryType.CHARACTER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlossaryGender.entries.forEach { option ->
                            FilterChip(
                                selected = gender == option,
                                onClick = { gender = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = raw.isNotBlank(),
                onClick = {
                    onSave(
                        GlossaryEntry(
                            type = type,
                            raw = raw.trim(),
                            translated = translated.trim(),
                            gender = if (type == GlossaryType.CHARACTER) gender else GlossaryGender.UNDECIDED,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
