package eu.kanade.presentation.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.reader.NovelReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderTheme
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(
    state: NovelReaderViewModel.State,
    preferences: NovelReaderPreferences,
    onBack: () -> Unit,
    onProgress: (paragraphIndex: Int, isAtEnd: Boolean) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val theme by preferences.theme.collectAsState()
    val backgroundColor = if (theme == NovelReaderTheme.FOLLOW_SYSTEM) {
        MaterialTheme.colorScheme.background
    } else {
        Color(theme.backgroundColor)
    }
    val textColor = if (theme == NovelReaderTheme.FOLLOW_SYSTEM) {
        MaterialTheme.colorScheme.onBackground
    } else {
        Color(theme.textColor)
    }

    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.chapter?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevChapter, enabled = state.hasPrevChapter) {
                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = null)
                }
                IconButton(onClick = onNextChapter, enabled = state.hasNextChapter) {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(contentPadding),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(MaterialTheme.padding.medium),
                    )
                }
                else -> {
                    NovelReaderContent(
                        paragraphs = state.paragraphs,
                        initialParagraphIndex = state.initialParagraphIndex,
                        preferences = preferences,
                        textColor = textColor,
                        onProgress = onProgress,
                    )
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            NovelReaderSettingsContent(preferences = preferences)
        }
    }
}

@Composable
private fun NovelReaderContent(
    paragraphs: List<String>,
    initialParagraphIndex: Int,
    preferences: NovelReaderPreferences,
    textColor: Color,
    onProgress: (paragraphIndex: Int, isAtEnd: Boolean) -> Unit,
) {
    val fontSize by preferences.fontSize.collectAsState()
    val lineSpacing by preferences.lineSpacing.collectAsState()

    val listState: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialParagraphIndex,
    )

    LaunchedEffect(listState, paragraphs) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(500.milliseconds)
            .collect { index ->
                val isAtEnd = !listState.canScrollForward
                onProgress(index, isAtEnd)
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(MaterialTheme.padding.medium),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(paragraphs) { paragraph ->
            Text(
                text = paragraph,
                color = textColor,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineSpacing).sp,
                modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
            )
        }
    }
}

@Composable
private fun NovelReaderSettingsContent(preferences: NovelReaderPreferences) {
    val fontSize by preferences.fontSize.collectAsState()
    val lineSpacing by preferences.lineSpacing.collectAsState()
    val theme by preferences.theme.collectAsState()

    Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
        Text(text = "Font size: $fontSize sp", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = fontSize.toFloat(),
            onValueChange = { preferences.fontSize.set(it.toInt()) },
            valueRange = NovelReaderPreferences.MIN_FONT_SIZE.toFloat()..NovelReaderPreferences.MAX_FONT_SIZE.toFloat(),
            steps = NovelReaderPreferences.MAX_FONT_SIZE - NovelReaderPreferences.MIN_FONT_SIZE - 1,
        )

        Text(text = "Line spacing: ${"%.1f".format(lineSpacing)}x", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = lineSpacing,
            onValueChange = { preferences.lineSpacing.set(it) },
            valueRange = NovelReaderPreferences.MIN_LINE_SPACING..NovelReaderPreferences.MAX_LINE_SPACING,
        )

        Text(text = "Theme", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            NovelReaderTheme.entries.forEach { option ->
                FilterChip(
                    selected = theme == option,
                    onClick = { preferences.theme.set(option) },
                    label = { Text(text = option.name) },
                )
            }
        }
    }
}
