package eu.kanade.presentation.reader

import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.reader.NovelReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.NovelFontFamily
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.setting.NovelTextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.collectAsState
import java.util.Date
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Matches Mihon's own image reader chrome ([eu.kanade.presentation.reader.appbars.ReaderAppBars]) -
 * same tween timings, same slide+fade combo, same translucent surface tint instead of a solid
 * app-bar color, all drawn as an overlay on top of full-bleed content rather than reserving layout
 * space (a Scaffold's topBar/bottomBar slots reflow content as they animate in/out, which read as a
 * jarring sideways pop instead of a smooth fade). */
private val chromeSlideAnimationSpec = tween<IntOffset>(200)
private val chromeFadeAnimationSpec = tween<Float>(150)

@Composable
private fun chromeBackgroundColor() = MaterialTheme.colorScheme
    .surfaceColorAtElevation(3.dp)
    .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(
    state: NovelReaderViewModel.State,
    preferences: NovelReaderPreferences,
    onBack: () -> Unit,
    onProgress: (paragraphIndex: Int, isAtEnd: Boolean) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onOpenInWebView: (() -> Unit)? = null,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
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

    val showVerticalSeekbar by preferences.showVerticalSeekbar.collectAsState()
    val showBatteryAndTime by preferences.showBatteryAndTime.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    // Tapping the reading area toggles all chrome (top bar, status bar, seekbar) - same
    // tap-to-hide behavior as Mihon's image reader. Starts hidden: opening a chapter drops
    // straight into the reading view, not into the chrome.
    var showControls by remember { mutableStateOf(false) }
    LaunchedEffect(showControls) { onControlsVisibilityChanged(showControls) }

    val listState: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.initialParagraphIndex,
    )
    val scope = rememberCoroutineScope()
    val chromeBackground = chromeBackgroundColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .pointerInput(Unit) {
                    detectTapGestures { showControls = !showControls }
                },
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    // Not always a fetch failure - e.g. a paywalled chapter with no content to
                    // show. Either way, don't strand the reader here: offer a way past it.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(MaterialTheme.padding.medium),
                    ) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                            if (state.hasPrevChapter) {
                                TextButton(onClick = onPrevChapter) { Text("Previous chapter") }
                            }
                            if (state.hasNextChapter) {
                                TextButton(onClick = onNextChapter) { Text("Next chapter") }
                            }
                        }
                    }
                }
                else -> {
                    NovelReaderContent(
                        state = state,
                        listState = listState,
                        preferences = preferences,
                        textColor = textColor,
                        onProgress = onProgress,
                        onPrevChapter = onPrevChapter,
                        onNextChapter = onNextChapter,
                    )
                }
            }
        }

        // Chrome overlay - drawn on top of the content above, never resizes it.
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(chromeSlideAnimationSpec) { -it } + fadeIn(chromeFadeAnimationSpec),
                exit = slideOutVertically(chromeSlideAnimationSpec) { -it } + fadeOut(chromeFadeAnimationSpec),
            ) {
                TopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = chromeBackground),
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
                        if (onOpenInWebView != null) {
                            IconButton(onClick = onOpenInWebView) {
                                Icon(Icons.Filled.Public, contentDescription = null)
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                        }
                    },
                )
            }

            val progressPercent = readingProgressPercent(listState, state.paragraphs.size)

            if (showVerticalSeekbar && state.paragraphs.isNotEmpty()) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = showControls,
                        enter = slideInHorizontally(chromeSlideAnimationSpec) { it } + fadeIn(chromeFadeAnimationSpec),
                        exit = slideOutHorizontally(chromeSlideAnimationSpec) { it } + fadeOut(chromeFadeAnimationSpec),
                        // 60% of the available height, not the full length between the bars.
                        modifier = Modifier
                            .fillMaxHeight(0.6f)
                            .padding(vertical = MaterialTheme.padding.medium, horizontal = MaterialTheme.padding.small),
                    ) {
                        NovelVerticalSeekbar(
                            // Drive the seekbar off the same percent the status bar shows, not the
                            // raw item index - a short trailing paragraph/footer can mean the list
                            // never actually scrolls its firstVisibleItemIndex up to the last
                            // paragraph, leaving the slider stuck short of full at the true end.
                            progressPercent = progressPercent,
                            backgroundColor = chromeBackground,
                            onProgressChange = { percent ->
                                val target = ((percent / 100f) * (state.paragraphs.size - 1).coerceAtLeast(0)).roundToInt()
                                scope.launch { listState.scrollToItem(target) }
                            },
                            onInteract = { showControls = true },
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // The footer (progress/battery/time) isn't part of the hideable chrome - it stays up
            // whenever its own preference is on, tap-to-hide or not.
            AnimatedVisibility(
                visible = showBatteryAndTime,
                enter = slideInVertically(chromeSlideAnimationSpec) { it } + fadeIn(chromeFadeAnimationSpec),
                exit = slideOutVertically(chromeSlideAnimationSpec) { it } + fadeOut(chromeFadeAnimationSpec),
            ) {
                NovelReaderStatusBar(
                    progressPercent = progressPercent,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                )
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            NovelReaderSettingsSheet(preferences = preferences)
        }
    }
}

/**
 * [listState.firstVisibleItemIndex] / [total] as a whole-number percent. Reports 100 once the
 * list can no longer scroll forward regardless of the raw index/total ratio, since the trailing
 * "Finished"/next-chapter footer item pads the list past the last paragraph and would otherwise
 * make the percentage stall short of 100 at the true end of the chapter.
 */
@Composable
private fun readingProgressPercent(listState: LazyListState, total: Int): Int {
    if (total <= 1) return 100
    val index by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
    val canScrollForward by remember(listState) { derivedStateOf { listState.canScrollForward } }
    if (!canScrollForward) return 100
    return ((index.toFloat() / (total - 1)) * 100).toInt().coerceIn(0, 100)
}

/**
 * Three plain segments in a row - reading progress, battery level, clock - matching LNReader's own
 * footer format exactly (no pill/box background, just text sitting directly on the page). Always
 * shown together as one unit, gated only by `preferences.showBatteryAndTime` - never tied to
 * tap-to-hide, so the reader can glance at progress/battery/time without needing to bring the rest
 * of the chrome up. Clock uses the device's own 12h/24h system setting
 * (`android.text.format.DateFormat.getTimeFormat`), not a hardcoded format.
 */
@Composable
private fun NovelReaderStatusBar(
    progressPercent: Int,
    textColor: Color,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    var batteryPercent by remember { mutableIntStateOf(-1) }
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        while (isActive) {
            batteryPercent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            currentTime = timeFormat.format(Date())
            delay(30_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Solid page-background backdrop - this footer is always visible (not part of the
            // hideable chrome), so without an opaque backing the last line of scrolling text
            // collides/overlaps directly with the numbers instead of stopping short of them.
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (batteryPercent >= 0) "$batteryPercent%" else "",
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = "$progressPercent%",
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = currentTime,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * The vertical progress slider, built the same way Mihon's own reader builds its side chapter
 * navigator ([eu.kanade.presentation.reader.components.VerticalChapterNavigator]): the real
 * Material3 [VerticalSlider] inside a translucent rounded pill, not a hand-rolled drag detector -
 * that's what was causing the seek gestures to fight with the tap-to-hide-chrome gesture
 * underneath, and the manual pixel-offset math that made the "0"/"100" labels overlap the rail.
 */
@Composable
private fun NovelVerticalSeekbar(
    progressPercent: Int,
    backgroundColor: Color,
    onProgressChange: (Int) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { SliderState(value = progressPercent.toFloat(), valueRange = 0f..100f) }
    state.value = progressPercent.toFloat()
    state.onValueChange = { onProgressChange(it.roundToInt()) }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) { if (isDragged) onInteract() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(40.dp)
            .background(backgroundColor, RoundedCornerShape(percent = 50))
            .padding(vertical = MaterialTheme.padding.small),
    ) {
        Text(text = "$progressPercent%", style = MaterialTheme.typography.labelSmall)
        VerticalSlider(
            state = state,
            interactionSource = interactionSource,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaterialTheme.padding.small),
        )
    }
}

@Composable
private fun NovelReaderContent(
    state: NovelReaderViewModel.State,
    listState: LazyListState,
    preferences: NovelReaderPreferences,
    textColor: Color,
    onProgress: (paragraphIndex: Int, isAtEnd: Boolean) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val paragraphs = state.paragraphs
    val fontSize by preferences.fontSize.collectAsState()
    val lineSpacing by preferences.lineSpacing.collectAsState()
    val contentPadding by preferences.contentPadding.collectAsState()
    val textAlignPref by preferences.textAlign.collectAsState()
    val fontFamilyPref by preferences.fontFamily.collectAsState()
    val removeExtraSpacing by preferences.removeExtraSpacing.collectAsState()
    val bionicReading by preferences.bionicReading.collectAsState()
    val autoScroll by preferences.autoScroll.collectAsState()
    val autoScrollSpeed by preferences.autoScrollSpeed.collectAsState()

    val textAlign = when (textAlignPref) {
        NovelTextAlign.LEFT -> TextAlign.Left
        NovelTextAlign.CENTER -> TextAlign.Center
        NovelTextAlign.JUSTIFY -> TextAlign.Justify
        NovelTextAlign.RIGHT -> TextAlign.Right
    }
    val fontFamily = when (fontFamilyPref) {
        NovelFontFamily.ORIGINAL -> FontFamily.Default
        NovelFontFamily.SERIF -> FontFamily.Serif
        NovelFontFamily.SANS_SERIF -> FontFamily.SansSerif
        NovelFontFamily.MONOSPACE -> FontFamily.Monospace
    }
    val paragraphSpacing = if (removeExtraSpacing) MaterialTheme.padding.extraSmall else MaterialTheme.padding.medium

    LaunchedEffect(listState, paragraphs) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(500.milliseconds)
            .collect { index ->
                val isAtEnd = !listState.canScrollForward
                onProgress(index, isAtEnd)
            }
    }

    LaunchedEffect(listState, autoScroll, autoScrollSpeed) {
        if (!autoScroll) return@LaunchedEffect
        while (isActive) {
            listState.scrollBy(autoScrollSpeed.toFloat())
            delay(32)
        }
    }

    // Pulling down past the very top of the chapter goes to the previous one, with a two-phase
    // rubber-band feel: an easy, smooth pull at first, then noticeably harder resistance right
    // before it triggers - the "hard stop" itself is what confirms the navigation, no release
    // gesture needed. Keyed by chapter id so it resets cleanly on every chapter switch.
    val density = LocalDensity.current
    val softLimitPx = with(density) { 40.dp.toPx() }
    val triggerPx = with(density) { 70.dp.toPx() }
    var overscrollPx by remember(state.chapter?.id) { mutableFloatStateOf(0f) }
    var triggered by remember(state.chapter?.id) { mutableStateOf(false) }

    val nestedScrollConnection = remember(state.hasPrevChapter, listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Confirmed via live logcat trace (see docs/novel-reader-ui-notes.md): at the true
                // top boundary, a continued downward pull produces POSITIVE available.y (~17-33 per
                // frame) - a prior "fix" flipped this check to reject positive values, which broke
                // the gesture entirely. Do not flip this again without re-verifying against a real
                // on-device trace first.
                if (!state.hasPrevChapter || triggered || available.y <= 0f) return Offset.Zero
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (!atTop) return Offset.Zero

                val pull = available.y
                val damping = if (overscrollPx < softLimitPx) 0.6f else 0.35f
                overscrollPx = (overscrollPx + pull * damping).coerceAtMost(triggerPx)
                if (overscrollPx >= triggerPx) {
                    triggered = true
                    onPrevChapter()
                }
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!triggered && overscrollPx != 0f) {
                    animate(overscrollPx, 0f) { value, _ -> overscrollPx = value }
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.hasPrevChapter && overscrollPx > 0f) {
            Text(
                text = if (overscrollPx >= triggerPx) {
                    "Release to go to previous chapter"
                } else {
                    "Pull down for previous chapter"
                },
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = MaterialTheme.padding.small)
                    .alpha((overscrollPx / softLimitPx).coerceIn(0f, 1f)),
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = contentPadding.dp,
                vertical = MaterialTheme.padding.medium,
            ),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .offset { IntOffset(0, overscrollPx.roundToInt()) },
        ) {
            items(paragraphs) { paragraph ->
                val annotated = remember(paragraph, bionicReading) {
                    val base = parseInlineHtml(paragraph)
                    if (bionicReading) applyBionicReading(base) else base
                }
                Text(
                    text = annotated,
                    color = textColor,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineSpacing).sp,
                    textAlign = textAlign,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(bottom = paragraphSpacing),
                )
            }

            item(key = "chapter-end") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Finished: ${state.chapter?.name.orEmpty()}",
                        color = textColor,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
                    )
                    if (state.hasNextChapter) {
                        ChapterSwitchPill(
                            label = "Next: ${state.nextChapterName.orEmpty()}",
                            onClick = onNextChapter,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterSwitchPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Solid accent pill (not a translucent tint of the reading text color) - matches LNReader's
    // own "Next: ..." pill look.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = MaterialTheme.padding.medium, horizontal = MaterialTheme.padding.large),
        )
    }
}

@Composable
private fun NovelReaderSettingsSheet(preferences: NovelReaderPreferences) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Reader") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("General") })
        }
        HorizontalDivider()
        when (tab) {
            0 -> NovelReaderTabSettings(preferences)
            1 -> NovelGeneralTabSettings(preferences)
        }
    }
}

@Composable
private fun NovelReaderTabSettings(preferences: NovelReaderPreferences) {
    val fontSize by preferences.fontSize.collectAsState()
    val lineSpacing by preferences.lineSpacing.collectAsState()
    val contentPadding by preferences.contentPadding.collectAsState()
    val theme by preferences.theme.collectAsState()
    val textAlign by preferences.textAlign.collectAsState()
    val fontFamily by preferences.fontFamily.collectAsState()

    Column(
        modifier = Modifier
            .padding(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Column {
            Text(text = "Text size: ${fontSize}px", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { preferences.fontSize.set(it.toInt()) },
                valueRange = NovelReaderPreferences.MIN_FONT_SIZE.toFloat()..NovelReaderPreferences.MAX_FONT_SIZE.toFloat(),
                steps = NovelReaderPreferences.MAX_FONT_SIZE - NovelReaderPreferences.MIN_FONT_SIZE - 1,
            )
        }

        Column {
            Text(text = "Color", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                NovelReaderTheme.entries.forEach { option ->
                    val swatchColor = if (option == NovelReaderTheme.FOLLOW_SYSTEM) {
                        MaterialTheme.colorScheme.background
                    } else {
                        Color(option.backgroundColor)
                    }
                    val checkColor = if (swatchColor.luminance() > 0.5f) Color.Black else Color.White
                    Surface(
                        shape = CircleShape,
                        color = swatchColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(40.dp),
                        onClick = { preferences.theme.set(option) },
                    ) {
                        if (theme == option) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = checkColor)
                            }
                        }
                    }
                }
            }
        }

        Column {
            Text(text = "Text alignment", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                NovelTextAlign.entries.forEach { option ->
                    FilterChip(
                        selected = textAlign == option,
                        onClick = { preferences.textAlign.set(option) },
                        label = {
                            TextAlignIcon(
                                option,
                                tint = if (textAlign == option) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                }
            }
        }

        Column {
            Text(text = "Line height: ${"%.1f".format(lineSpacing)}x", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = lineSpacing,
                onValueChange = { preferences.lineSpacing.set(it) },
                valueRange = NovelReaderPreferences.MIN_LINE_SPACING..NovelReaderPreferences.MAX_LINE_SPACING,
            )
        }

        Column {
            Text(text = "Padding: ${contentPadding}dp", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = contentPadding.toFloat(),
                onValueChange = { preferences.contentPadding.set(it.toInt()) },
                valueRange = NovelReaderPreferences.MIN_PADDING.toFloat()..NovelReaderPreferences.MAX_PADDING.toFloat(),
                steps = NovelReaderPreferences.MAX_PADDING - NovelReaderPreferences.MIN_PADDING - 1,
            )
        }

        Column {
            Text(text = "Font style", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                NovelFontFamily.entries.forEach { option ->
                    FilterChip(
                        selected = fontFamily == option,
                        onClick = { preferences.fontFamily.set(option) },
                        label = {
                            Text(
                                text = option.name.split("_").joinToString(" ") {
                                    it.lowercase().replaceFirstChar(Char::uppercase)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextAlignIcon(align: NovelTextAlign, tint: Color) {
    Canvas(modifier = Modifier.size(width = 20.dp, height = 14.dp)) {
        val lineWidths = listOf(1f, 0.8f, 1f, 0.6f)
        val gap = size.height / lineWidths.size
        lineWidths.forEachIndexed { i, widthFraction ->
            val lineWidth = size.width * widthFraction
            val startX = when (align) {
                NovelTextAlign.LEFT -> 0f
                NovelTextAlign.CENTER -> (size.width - lineWidth) / 2
                NovelTextAlign.JUSTIFY -> 0f
                NovelTextAlign.RIGHT -> size.width - lineWidth
            }
            val actualLineWidth = if (align == NovelTextAlign.JUSTIFY) size.width else lineWidth
            val y = gap * i + gap / 2
            drawLine(
                color = tint,
                start = Offset(startX, y),
                end = Offset(startX + actualLineWidth, y),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun NovelGeneralTabSettings(preferences: NovelReaderPreferences) {
    val fullscreen by preferences.fullscreen.collectAsState()
    val keepScreenOn by preferences.keepScreenOn.collectAsState()
    val showBatteryAndTime by preferences.showBatteryAndTime.collectAsState()
    val showVerticalSeekbar by preferences.showVerticalSeekbar.collectAsState()
    val removeExtraSpacing by preferences.removeExtraSpacing.collectAsState()
    val bionicReading by preferences.bionicReading.collectAsState()
    val autoScroll by preferences.autoScroll.collectAsState()
    val autoScrollSpeed by preferences.autoScrollSpeed.collectAsState()

    Column {
        SwitchPreferenceWidget(
            title = "Fullscreen",
            subtitle = "Hide the status bar and navigation bar while reading",
            checked = fullscreen,
            onCheckedChanged = { preferences.fullscreen.set(it) },
        )
        SwitchPreferenceWidget(
            title = "Battery & time",
            subtitle = "Show reading progress, battery level and clock over the reading screen, even when hidden",
            checked = showBatteryAndTime,
            onCheckedChanged = { preferences.showBatteryAndTime.set(it) },
        )
        SwitchPreferenceWidget(
            title = "Vertical seekbar",
            subtitle = "Show a slider on the side to jump to any point in the chapter",
            checked = showVerticalSeekbar,
            onCheckedChanged = { preferences.showVerticalSeekbar.set(it) },
        )
        SwitchPreferenceWidget(
            title = "Remove extra spacing",
            subtitle = "Reduce blank space between paragraphs",
            checked = removeExtraSpacing,
            onCheckedChanged = { preferences.removeExtraSpacing.set(it) },
        )
        SwitchPreferenceWidget(
            title = "Bionic reading",
            subtitle = "Bold the start of each word to help your eyes track faster",
            checked = bionicReading,
            onCheckedChanged = { preferences.bionicReading.set(it) },
        )
        SwitchPreferenceWidget(
            title = "Keep screen on",
            checked = keepScreenOn,
            onCheckedChanged = { preferences.keepScreenOn.set(it) },
        )
        SwitchPreferenceWidget(
            title = "Auto-scroll",
            subtitle = if (autoScroll) "Speed: $autoScrollSpeed" else null,
            checked = autoScroll,
            onCheckedChanged = { preferences.autoScroll.set(it) },
        )
        if (autoScroll) {
            Slider(
                value = autoScrollSpeed.toFloat(),
                onValueChange = { preferences.autoScrollSpeed.set(it.toInt()) },
                valueRange = NovelReaderPreferences.MIN_AUTO_SCROLL_SPEED.toFloat()..
                    NovelReaderPreferences.MAX_AUTO_SCROLL_SPEED.toFloat(),
                steps = NovelReaderPreferences.MAX_AUTO_SCROLL_SPEED - NovelReaderPreferences.MIN_AUTO_SCROLL_SPEED - 1,
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )
        }
    }
}
