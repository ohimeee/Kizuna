package eu.kanade.presentation.reader

import android.os.BatteryManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.NovelReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.NovelFontFamily
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.setting.NovelTextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
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

/**
 * Bundled OFL-licensed variable-weight fonts (TTFs under `res/font/`, license text under
 * `docs/third-party-licenses/fonts/`) - unlike the generic `FontFamily.Serif`/`SansSerif`
 * options, these are real typefaces matching what LNReader itself offers. Each family is a single
 * variable-font file per style (upright/italic); [FontVariation.weight] picks the specific weight
 * out of it rather than needing separate static files per weight.
 */
private val LoraFontFamily = FontFamily(
    Font(R.font.lora, weight = FontWeight.Normal, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora, weight = FontWeight.Bold, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.lora_italic, weight = FontWeight.Normal, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.lora_italic, weight = FontWeight.Bold, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
/**
 * The reader's font preference as a real [FontFamily]. Shared so the status strip renders in
 * whatever the reader picked rather than the app's default, instead of each call site repeating
 * the mapping and drifting apart.
 */
@Composable
private fun NovelFontFamily.toFontFamily(): FontFamily = when (this) {
    NovelFontFamily.ORIGINAL -> FontFamily.Default
    NovelFontFamily.SERIF -> FontFamily.Serif
    NovelFontFamily.SANS_SERIF -> FontFamily.SansSerif
    NovelFontFamily.MONOSPACE -> FontFamily.Monospace
    NovelFontFamily.LORA -> LoraFontFamily
    NovelFontFamily.NUNITO -> NunitoFontFamily
}

private val NunitoFontFamily = FontFamily(
    Font(R.font.nunito, weight = FontWeight.Normal, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito, weight = FontWeight.Bold, style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_italic, weight = FontWeight.Normal, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_italic, weight = FontWeight.Bold, style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** One in-chapter search hit: which paragraph, and the offset within that paragraph's text. */
private data class NovelSearchMatch(val paragraphIndex: Int, val offset: Int)

private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Below this length a query matches so much of a chapter that highlighting it is noise rather than
 * navigation. Punctuation-only queries are exempt - searching for `"` or `—` is a legitimate thing
 * to want and can't reach the threshold. Same rule LNReader's reader searchbar uses.
 */
private const val MIN_SEARCH_LENGTH = 3

/** Ceiling on collected hits, so a pathological query can't build a huge list on the UI thread. */
private const val MAX_SEARCH_MATCHES = 500

/** Leaves a strip of the page visible beside the chapter panel, so it reads as an overlay. */
private const val CHAPTER_PANEL_WIDTH_FRACTION = 0.86f

/** Dividers are drawn from the reading theme's text colour, so they need to be knocked well back. */
private const val DIVIDER_ALPHA = 0.2f

/**
 * Dim behind the chapter panel. Deep enough that the page reads as pushed back rather than merely
 * tinted, but not opaque - the strip of page beside the panel stays visible, just clearly inert.
 */
private const val CHAPTER_PANEL_SCRIM_ALPHA = 0.6f

/**
 * Converts the auto-scroll speed preference into pixels per second. The old loop moved
 * `autoScrollSpeed` pixels every 32ms, so this keeps 1000/32 as the scale and every existing
 * saved speed keeps feeling exactly as fast as it did.
 */
private const val AUTO_SCROLL_SPEED_SCALE = 1000f / 32f

private const val NANOS_PER_SECOND = 1_000_000_000f

/** Longest per-frame step auto-scroll will honour, so a parked frame clock can't resume as a leap. */
private const val MAX_AUTO_SCROLL_FRAME_SECONDS = 1f / 30f

/**
 * Chapter title size, as a multiple of the reader's body text size. Modelled on LNReader, where
 * the title reads as a proper heading rather than emboldened body text.
 */
private const val CHAPTER_TITLE_SCALE = 1.7f

/** Titles wrap often, and body line spacing set on a heading this size drifts too far apart. */
private const val CHAPTER_TITLE_LINE_SPACING = 1.25f

/** Space above the chapter title, kept clear of the top edge (and of any camera cutout). */
private val CHAPTER_TOP_PADDING = 32.dp

/**
 * Longest first paragraph still treated as a chapter heading. Real titles are short; anything
 * past this is prose from a source that didn't emit a heading line, and blowing it up to
 * [CHAPTER_TITLE_SCALE] would look broken.
 */
private const val CHAPTER_TITLE_MAX_LENGTH = 120

private val SEARCH_SPECIAL_CHARACTER_REGEX = Regex("""[^\p{L}\p{N}\s]""")

private fun isSearchable(query: String): Boolean =
    query.length >= MIN_SEARCH_LENGTH || (query.isNotEmpty() && SEARCH_SPECIAL_CHARACTER_REGEX.containsMatchIn(query))

/**
 * Reader chrome (top bar, search bar, seekbar pill) follows the **app's** light/dark theme, not the
 * reading theme - same as LNReader, where a dark app theme keeps dark chrome even over a light
 * reading page. Deriving it from the page instead was tried and reverted: it's the app theme that
 * owns this, and the reading theme only owns the page itself.
 */
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
    onFinishChapter: () -> Unit,
    onReloadChapter: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSelectChapter: (Long) -> Unit,
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
    // The status strip renders in the reader's own font, so this is needed out here rather than
    // only inside NovelReaderContent.
    val statusBarFontFamilyPref by preferences.fontFamily.collectAsState()
    val statusBarFontFamily = statusBarFontFamilyPref.toFontFamily()

    var showSettings by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }

    // The chapter panel is a hand-rolled overlay rather than a Material sheet, so Back doesn't
    // dismiss it for free - without this it would fall through and exit the reader entirely.
    BackHandler(enabled = showChapterList) { showChapterList = false }
    // Tapping the reading area toggles all chrome (top bar, status bar, seekbar) - same
    // tap-to-hide behavior as Mihon's image reader. Starts hidden: opening a chapter drops
    // straight into the reading view, not into the chrome.
    var showControls by remember { mutableStateOf(false) }
    LaunchedEffect(showControls) { onControlsVisibilityChanged(showControls) }

    // Keyed on the chapter id so a fresh LazyListState is created on every chapter change -
    // without this, rememberLazyListState only reads initialFirstVisibleItemIndex the very first
    // time it's constructed, then keeps reusing that same state object (and its current scroll
    // position) across every later chapter load, since state.chapter changing alone doesn't
    // recreate it. That's what made a freshly-opened next chapter appear to inherit the previous
    // chapter's scroll position instead of starting at its own initialParagraphIndex.
    val listState: LazyListState = key(state.chapter?.id) {
        rememberLazyListState(initialFirstVisibleItemIndex = state.initialParagraphIndex)
    }
    val scope = rememberCoroutineScope()
    val chromeBackground = chromeBackgroundColor()
    // Chrome is app-themed (see chromeBackgroundColor), so its content is too - using the reading
    // theme's text color here would render invisible whenever the two themes disagree.
    val chromeContentColor = MaterialTheme.colorScheme.onSurface
    // Solid counterpart to chromeBackground, for the chapter panel. The bars are translucent
    // because they're thin strips over the page; a full-height panel at that alpha just lets the
    // page text read through it.
    val chapterPanelBackground = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    // Hoisted out of the chrome Column: the bottom chrome is drawn from the root Box now (so the
    // status strip can sit above the chapter panel), and reads this from there.
    val progressPercent = readingProgressPercent(listState, state.paragraphs.size)

    // --- In-chapter text search (mirrors LNReader's reader searchbar) ---
    var searchVisible by remember(state.chapter?.id) { mutableStateOf(false) }
    var searchQuery by remember(state.chapter?.id) { mutableStateOf("") }
    var debouncedQuery by remember(state.chapter?.id) { mutableStateOf("") }
    var currentMatch by remember(state.chapter?.id) { mutableIntStateOf(0) }

    LaunchedEffect(searchQuery) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = searchQuery.trim().takeIf { isSearchable(it) }.orEmpty()
        currentMatch = 0
    }

    // Search the *rendered* text, not the source HTML - a match offset taken from raw markup
    // wouldn't line up with what parseInlineHtml actually draws. `lazy` matters here: this parses
    // every paragraph in the chapter, and most reading sessions never open search at all, so it
    // must not run as part of simply opening a chapter. Cached per chapter once it does run, so
    // typing doesn't re-parse on every keystroke.
    val plainParagraphs = remember(state.paragraphs) {
        lazy { state.paragraphs.map { parseInlineHtml(it).text } }
    }
    val matches = remember(plainParagraphs, debouncedQuery) {
        if (debouncedQuery.isEmpty()) {
            emptyList()
        } else {
            buildList {
                for ((paragraphIndex, text) in plainParagraphs.value.withIndex()) {
                    var offset = text.indexOf(debouncedQuery, ignoreCase = true)
                    while (offset >= 0) {
                        add(NovelSearchMatch(paragraphIndex, offset))
                        if (size >= MAX_SEARCH_MATCHES) break
                        offset = text.indexOf(debouncedQuery, offset + 1, ignoreCase = true)
                    }
                    if (size >= MAX_SEARCH_MATCHES) break
                }
            }
        }
    }
    val activeMatch = matches.getOrNull(currentMatch)

    // Offset the jump by roughly the search bar's height: a bare scrollToItem parks the matched
    // paragraph flush against the top of the viewport, which is exactly where the search bar
    // overlay sits - so the hit you just navigated to ends up hidden underneath it.
    val searchScrollOffsetPx = with(LocalDensity.current) { 96.dp.roundToPx() }
    LaunchedEffect(activeMatch) {
        activeMatch?.let { listState.scrollToItem(it.paragraphIndex, -searchScrollOffsetPx) }
    }

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
                            TextButton(onClick = onReloadChapter) { Text("Reload") }
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
                        onFinishChapter = onFinishChapter,
                        searchQuery = debouncedQuery,
                        activeMatchParagraph = activeMatch?.paragraphIndex ?: -1,
                        activeMatchOffset = activeMatch?.offset ?: -1,
                    )
                }
            }
        }

        // Chrome overlay - drawn on top of the content above, never resizes it.
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                // The chapter panel replaces the chrome rather than stacking on top of it, so the
                // top bar steps aside while it's open. The battery/progress/time strip does not.
                visible = showControls && !showChapterList,
                enter = slideInVertically(chromeSlideAnimationSpec) { -it } + fadeIn(chromeFadeAnimationSpec),
                exit = slideOutVertically(chromeSlideAnimationSpec) { -it } + fadeOut(chromeFadeAnimationSpec),
            ) {
                if (searchVisible) {
                    NovelReaderSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        // 1-based for display; 0/0 while a query is too short to have run yet.
                        currentMatch = if (matches.isEmpty()) 0 else currentMatch + 1,
                        totalMatches = matches.size,
                        isTruncated = matches.size >= MAX_SEARCH_MATCHES,
                        showMinLengthHint = searchQuery.isNotBlank() && !isSearchable(searchQuery.trim()),
                        onPrev = { if (matches.isNotEmpty()) currentMatch = (currentMatch - 1 + matches.size) % matches.size },
                        onNext = { if (matches.isNotEmpty()) currentMatch = (currentMatch + 1) % matches.size },
                        onClose = {
                            searchVisible = false
                            searchQuery = ""
                            debouncedQuery = ""
                            currentMatch = 0
                        },
                        containerColor = chromeBackground,
                        contentColor = chromeContentColor,
                    )
                } else {
                TopAppBar(
                    // `windowInsets`, not an outer windowInsetsPadding modifier: this pads the
                    // bar's *content* below the status bar while its container still paints behind
                    // it. Padding the whole bar instead left the reading page showing through the
                    // status bar strip, which is what made the system icons white-on-white on a
                    // light reading theme. Matches LNReader, where the status bar sits on the
                    // appbar's own chrome color.
                    windowInsets = WindowInsets.statusBars,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = chromeBackground),
                    title = {
                        // Chapter name over novel name, matching LNReader's title/subtitle top bar
                        // - the chapter alone left no indication of which novel was being read.
                        Column {
                            Text(
                                text = state.chapter?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.mangaTitle?.takeIf { it.isNotBlank() }?.let { novelTitle ->
                                Text(
                                    text = novelTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchVisible = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search in chapter")
                        }
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
            }

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
                            textColor = chromeContentColor,
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

        }

        // Bottom chrome, drawn before the chapter list so the list's backdrop dims the
        // battery/progress/time strip along with everything else. The strip itself is unchanged:
        // rooted at the bottom, governed only by its own preference, never tied to tap-to-hide.
        NovelReaderBottomChrome(
            showNavBar = showControls && !showChapterList,
            showBatteryAndTime = showBatteryAndTime,
            progressPercent = progressPercent,
            textColor = textColor,
            backgroundColor = backgroundColor,
            chromeBackground = chromeBackground,
            chromeContentColor = chromeContentColor,
            statusBarFontFamily = statusBarFontFamily,
            hasPrevChapter = state.hasPrevChapter,
            hasNextChapter = state.hasNextChapter,
            isBookmarked = state.chapter?.bookmark == true,
            onPrevChapter = onPrevChapter,
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
            onOpenChapterList = { showChapterList = true },
            onToggleBookmark = onToggleBookmark,
            onNextChapter = onNextChapter,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Chapter list. Chrome, like the bars - a slide-in panel over the page rather than a
        // Material bottom sheet, painted from the *chrome* palette (what the top and nav bars
        // use) rather than the reading theme's. Those two disagree whenever a light reading theme
        // is used inside a dark app theme, which left this panel glaringly white while everything
        // else on screen was dark.
        //
        // The panel is solid, and everything it doesn't cover - page text and the status strip
        // alike - sits under one uniform dim, so nothing pokes through at full brightness beside
        // it. Drawn after the bottom chrome above precisely so that strip is dimmed too.
        AnimatedVisibility(
            visible = showChapterList,
            enter = fadeIn(chromeFadeAnimationSpec),
            exit = fadeOut(chromeFadeAnimationSpec),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = CHAPTER_PANEL_SCRIM_ALPHA))
                    .pointerInput(Unit) { detectTapGestures { showChapterList = false } },
            )
        }
        AnimatedVisibility(
            visible = showChapterList,
            enter = slideInHorizontally(chromeSlideAnimationSpec) { -it },
            exit = slideOutHorizontally(chromeSlideAnimationSpec) { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            NovelChapterListPanel(
                chapters = state.chapters,
                currentChapterId = state.chapter?.id,
                backgroundColor = chapterPanelBackground,
                textColor = chromeContentColor,
                onSelectChapter = {
                    showChapterList = false
                    onSelectChapter(it)
                },
                onClose = { showChapterList = false },
            )
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
 * Chapter picker behind the nav bar's list button: a slide-in panel over the page, painted from
 * the chrome palette so it matches the top/nav bars rather than the page underneath - a light
 * reading theme inside a dark app theme would otherwise leave this panel glaringly white.
 *
 * Opens already scrolled to the chapter being read - a novel here can run to thousands of
 * chapters, so landing at the top of the list would strand the reader.
 */
@Composable
private fun NovelChapterListPanel(
    chapters: List<Chapter>,
    currentChapterId: Long?,
    backgroundColor: Color,
    textColor: Color,
    onSelectChapter: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val currentIndex = remember(chapters, currentChapterId) {
        chapters.indexOfFirst { it.id == currentChapterId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth(CHAPTER_PANEL_WIDTH_FRACTION)
            .fillMaxHeight()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small)
                .padding(vertical = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close chapter list", tint = textColor)
            }
        }
        HorizontalDivider(color = textColor.copy(alpha = DIVIDER_ALPHA))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(chapters, key = { _, chapter -> chapter.id }) { _, chapter ->
                val isCurrent = chapter.id == currentChapterId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectChapter(chapter.id) }
                        .background(if (isCurrent) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                        // Roomier than Mihon's own chapter rows (which use 12.dp vertical): this
                        // list is a full-height picker scrolled with a thumb, not a dense inline
                        // list, and at Mihon's density the entries read as one solid block.
                        .padding(start = 16.dp, top = 18.dp, end = 8.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = when {
                            isCurrent -> accentColor
                            // Read chapters dim, matching how the details screen greys them out.
                            chapter.read -> textColor.copy(alpha = DISABLED_ALPHA)
                            else -> textColor
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (chapter.isLocked) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "Locked on the source site",
                            tint = textColor.copy(alpha = DISABLED_ALPHA),
                            modifier = Modifier
                                .padding(start = MaterialTheme.padding.extraSmall)
                                .size(16.dp),
                        )
                    }
                    if (chapter.bookmark) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier
                                .padding(start = MaterialTheme.padding.extraSmall)
                                .size(16.dp),
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = textColor.copy(alpha = DIVIDER_ALPHA))
        Column(
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Button(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scroll to top")
            }
            Button(
                onClick = { scope.launch { listState.animateScrollToItem(currentIndex) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scroll to current chapter")
            }
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
 * Bottom chrome: the always-on battery/progress/time strip, with the tap-hideable nav bar drawn
 * *over* it. Both are anchored to the bottom edge, so bringing the chrome up covers the numbers
 * rather than pushing the nav bar above them, and the strip itself keeps the exact behaviour it
 * always had - rooted at the bottom, governed only by its own preference, never tied to
 * tap-to-hide.
 *
 * This lives in its own composable rather than inline in the chrome `Column` for a compiler
 * reason: with a `ColumnScope` receiver still in scope, `AnimatedVisibility` resolves to
 * `ColumnScope.AnimatedVisibility`, which then can't be called inside the `Box` here.
 */
@Composable
private fun NovelReaderBottomChrome(
    showNavBar: Boolean,
    showBatteryAndTime: Boolean,
    progressPercent: Int,
    textColor: Color,
    backgroundColor: Color,
    chromeBackground: Color,
    chromeContentColor: Color,
    statusBarFontFamily: FontFamily,
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    isBookmarked: Boolean,
    onPrevChapter: () -> Unit,
    onScrollToTop: () -> Unit,
    onOpenChapterList: () -> Unit,
    onToggleBookmark: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Unchanged from before the nav bar existed: needs the solid page-colored backdrop, or
        // scrolling body text runs into the numbers.
        AnimatedVisibility(
            visible = showBatteryAndTime,
            enter = slideInVertically(chromeSlideAnimationSpec) { it } + fadeIn(chromeFadeAnimationSpec),
            exit = slideOutVertically(chromeSlideAnimationSpec) { it } + fadeOut(chromeFadeAnimationSpec),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NovelReaderStatusBar(
                progressPercent = progressPercent,
                textColor = textColor,
                backgroundColor = backgroundColor,
                fontFamily = statusBarFontFamily,
            )
        }

        AnimatedVisibility(
            visible = showNavBar,
            enter = slideInVertically(chromeSlideAnimationSpec) { it } + fadeIn(chromeFadeAnimationSpec),
            exit = slideOutVertically(chromeSlideAnimationSpec) { it } + fadeOut(chromeFadeAnimationSpec),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            NovelReaderNavBar(
                hasPrevChapter = hasPrevChapter,
                hasNextChapter = hasNextChapter,
                isBookmarked = isBookmarked,
                backgroundColor = chromeBackground,
                contentColor = chromeContentColor,
                onPrevChapter = onPrevChapter,
                onScrollToTop = onScrollToTop,
                onOpenChapterList = onOpenChapterList,
                onToggleBookmark = onToggleBookmark,
                onNextChapter = onNextChapter,
                // Drawn over the status strip, so it owns the navigation-bar inset too -
                // otherwise its buttons sit under the system gesture bar.
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

/**
 * Bottom navigation row: previous chapter, scroll to top, chapter list, bookmark, next chapter.
 *
 * Unlike the battery/progress strip below it, this **is** part of the tap-hideable chrome - it
 * mirrors the top bar, sharing its `showControls` gating and translucent background. Prev/next are
 * dimmed rather than removed at the first/last chapter so the row never reflows under a thumb.
 */
@Composable
private fun NovelReaderNavBar(
    hasPrevChapter: Boolean,
    hasNextChapter: Boolean,
    isBookmarked: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    onPrevChapter: () -> Unit,
    onScrollToTop: () -> Unit,
    onOpenChapterList: () -> Unit,
    onToggleBookmark: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(modifier)
            .padding(vertical = MaterialTheme.padding.extraSmall),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevChapter, enabled = hasPrevChapter) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous chapter",
                tint = contentColor.copy(alpha = if (hasPrevChapter) 1f else DISABLED_ALPHA),
            )
        }
        IconButton(onClick = onScrollToTop) {
            Icon(
                imageVector = Icons.Filled.VerticalAlignTop,
                contentDescription = "Scroll to top",
                tint = contentColor,
            )
        }
        IconButton(onClick = onOpenChapterList) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "Chapter list",
                tint = contentColor,
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark chapter",
                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else contentColor,
            )
        }
        IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next chapter",
                tint = contentColor.copy(alpha = if (hasNextChapter) 1f else DISABLED_ALPHA),
            )
        }
    }
}

/**
 * Three plain segments in a row - battery level, reading progress, clock. Shown together as one
 * unit, gated only by `preferences.showBatteryAndTime` - never tied to tap-to-hide, so the reader
 * can glance at progress/battery/time without needing to bring the rest of the chrome up. Clock
 * uses the device's own 12h/24h system setting
 * (`android.text.format.DateFormat.getTimeFormat`), not a hardcoded format.
 */
@Composable
private fun NovelReaderStatusBar(
    progressPercent: Int,
    textColor: Color,
    backgroundColor: Color,
    fontFamily: FontFamily,
) {
    // Follows the reader's own font choice and sits a step up from labelSmall - at 11sp in the
    // app default these numbers were noticeably smaller than anything else on the page.
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily)
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
            // Solid *page* background, not the translucent `chromeBackground` the top bar and
            // seekbar use. Those two get their content color from the app's Material theme, but
            // this footer's [textColor] comes from the reading theme - pairing reading-theme text
            // with app-theme chrome renders dark-on-dark (or light-on-light) the moment the two
            // themes disagree, e.g. a white reading page inside a dark app theme. Keeping both
            // colors from the same reading theme guarantees contrast, and since this footer hides
            // with the rest of the chrome now, a page-colored backdrop reads as seamless rather
            // than as an opaque band carved out of the page.
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (batteryPercent >= 0) "$batteryPercent%" else "",
            color = textColor,
            style = textStyle,
        )
        Text(
            text = "$progressPercent%",
            color = textColor,
            style = textStyle,
        )
        Text(
            text = currentTime,
            color = textColor,
            style = textStyle,
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
    textColor: Color,
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
        Text(
            text = "$progressPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
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
    /** The end-of-chapter pill - the one forward control that means "finished", so it marks read. */
    onFinishChapter: () -> Unit,
    searchQuery: String = "",
    activeMatchParagraph: Int = -1,
    activeMatchOffset: Int = -1,
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
    val showBatteryAndTime by preferences.showBatteryAndTime.collectAsState()

    val textAlign = when (textAlignPref) {
        NovelTextAlign.LEFT -> TextAlign.Left
        NovelTextAlign.CENTER -> TextAlign.Center
        NovelTextAlign.JUSTIFY -> TextAlign.Justify
        NovelTextAlign.RIGHT -> TextAlign.Right
    }
    val fontFamily = fontFamilyPref.toFontFamily()
    val paragraphSpacing = if (removeExtraSpacing) MaterialTheme.padding.extraSmall else MaterialTheme.padding.medium

    // Observe the end-of-chapter state alongside the index, not just the index. Scrolling through
    // the final screenful usually doesn't change firstVisibleItemIndex - the same item stays first
    // while the remaining content scrolls up - so watching the index alone never re-emitted at the
    // moment the chapter actually reached its end. isAtEnd was only ever sampled inside collect,
    // which meant the chapter was never marked read and its progress froze short of 100%.
    LaunchedEffect(listState, paragraphs) {
        snapshotFlow { listState.firstVisibleItemIndex to !listState.canScrollForward }
            .distinctUntilChanged()
            .debounce(500.milliseconds)
            .collect { (index, isAtEnd) ->
                onProgress(index, isAtEnd)
            }
    }

    // Auto-scroll, driven off the display's own frame clock rather than a fixed delay.
    //
    // This used to scroll a whole `autoScrollSpeed` pixels every 32ms - about 31 steps a second,
    // in lockstep with nothing. On a 60/90/120Hz panel that lands between frames and moves the
    // page in visible jumps, which is what read as jagged. Waiting on withFrameNanos instead
    // scrolls exactly once per drawn frame, and scaling by the real elapsed time means each frame
    // moves a proportional (often sub-pixel) amount - so it stays smooth at any refresh rate and
    // holds the same average speed even if a frame is late.
    //
    // One scrollBy per frame rather than a single long-lived scroll session: a session, once
    // cancelled, is gone for good, whereas each per-frame scroll is independent (see the catch
    // below).
    LaunchedEffect(listState, autoScroll, autoScrollSpeed) {
        if (!autoScroll) return@LaunchedEffect
        val pixelsPerSecond = autoScrollSpeed * AUTO_SCROLL_SPEED_SCALE
        var previousFrameNanos = withFrameNanos { it }
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            // Clamped: Compose parks the frame clock while the reader is backgrounded, so an
            // unclamped delta would replay the entire paused stretch as one enormous jump the
            // instant it resumes - background for two minutes and the page would leap thousands
            // of pixels. Capping at a slow frame keeps that to a single ordinary step.
            val elapsedSeconds = ((frameNanos - previousFrameNanos) / NANOS_PER_SECOND)
                .coerceAtMost(MAX_AUTO_SCROLL_FRAME_SECONDS)
            previousFrameNanos = frameNanos
            try {
                listState.scrollBy(pixelsPerSecond * elapsedSeconds)
            } catch (_: CancellationException) {
                // A drag takes the scroll mutex at UserInput priority, which cancels this
                // Default-priority scroll. Auto-scroll has to survive that - letting it escape
                // would end the loop for good, and the LaunchedEffect's keys haven't changed, so
                // nothing would ever restart it: one flick would silently kill auto-scroll until
                // the user toggled the setting off and on. Only a real cancellation of this
                // coroutine should stop us, which is exactly what ensureActive() rethrows.
                currentCoroutineContext().ensureActive()
            }
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
                // Only a real finger drag may pull to the previous chapter. Fling momentum arrives
                // here too (as NestedScrollSource.SideEffect), so a hard swipe up from the middle
                // or bottom of a chapter used to coast into the top boundary and spend its leftover
                // velocity on this gesture - jumping to the previous chapter when the reader was
                // just scrolling back to re-read something.
                if (source != NestedScrollSource.UserInput) return Offset.Zero

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

        // Long-press to select, then copy/share - the same thing LNReader allows. Compose text is
        // inert unless it sits inside a SelectionContainer, which is why none of this was
        // selectable before.
        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = contentPadding.dp,
                    end = contentPadding.dp,
                    // Breathing room above the chapter title. The reader draws edge-to-edge and
                    // hides the system bars while reading, at which point the ancestor's systemBars
                    // inset collapses to zero and the title sat flush against the top of the screen
                    // - close enough to a punch-hole camera to be clipped by it.
                    //
                    // Deliberately a flat value rather than the displayCutout inset: that inset is
                    // already covered by the ancestor's systemBars padding while the bars are up, so
                    // adding it here made the text jump down by a cutout's height every time the
                    // chrome was toggled - and it reports zero while the bars are hidden, which is
                    // the exact case this is for.
                    top = CHAPTER_TOP_PADDING,
                    // The footer (progress/battery/time) is always-visible overlay, not part of the
                    // scrollable content, so without extra bottom padding the last item (the
                    // Finished/Next-chapter pill) ends up rendered right underneath it instead of
                    // stopping above it once scrolled all the way down.
                    bottom = if (showBatteryAndTime) 64.dp else MaterialTheme.padding.medium,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .offset { IntOffset(0, overscrollPx.roundToInt()) },
            ) {
                itemsIndexed(paragraphs) { index, paragraph ->
                    // The chapter title is always the first paragraph (source HTML's own heading
                    // line, see NovelReaderViewModel.splitParagraphs) - always bold and scaled up to
                    // read as a real heading rather than just emboldened body text, with one blank
                    // line of extra space before the body starts, regardless of the
                    // "remove extra spacing" preference.
                    val isTitle = index == 0
                    // The heading treatment is gated on length as well as position: splitParagraphs
                    // only splits on block boundaries, so the first paragraph is a title by
                    // convention, not by guarantee. A source whose chapter body opens straight into
                    // prose would otherwise get a whole paragraph set at 1.7x with heading line
                    // spacing. Bold and the trailing blank line still apply either way, as before.
                    val isHeading = isTitle && paragraph.length <= CHAPTER_TITLE_MAX_LENGTH
                    // Scales with the reader's own font size rather than being a fixed sp value, so
                    // the heading keeps its proportion at every text size the user picks.
                    val paragraphFontSize = if (isHeading) fontSize * CHAPTER_TITLE_SCALE else fontSize.toFloat()
                    val annotated = remember(paragraph, bionicReading) {
                        val base = parseInlineHtml(paragraph)
                        if (bionicReading) applyBionicReading(base) else base
                    }
                    // Only this paragraph's own hit counts as "active" - every other match in the
                    // chapter gets the plain wash.
                    val activeOffset = if (index == activeMatchParagraph) activeMatchOffset else null
                    val displayed = remember(annotated, searchQuery, activeOffset) {
                        if (searchQuery.isEmpty()) {
                            annotated
                        } else {
                            highlightSearchMatches(annotated, searchQuery, activeOffset)
                        }
                    }
                    Text(
                        text = displayed,
                        color = textColor,
                        fontSize = paragraphFontSize.sp,
                        fontWeight = if (isTitle) FontWeight.Bold else null,
                        // A heading set at the body's line spacing drifts far apart when it wraps, so
                        // the title gets its own tighter multiplier while the body keeps the user's.
                        lineHeight = if (isHeading) {
                            (paragraphFontSize * CHAPTER_TITLE_LINE_SPACING).sp
                        } else {
                            (fontSize * lineSpacing).sp
                        },
                        textAlign = textAlign,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(
                            bottom = if (isTitle) {
                                paragraphSpacing + (fontSize * lineSpacing).dp
                            } else {
                                paragraphSpacing
                            },
                        ),
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
                                onClick = onFinishChapter,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * In-chapter find bar, modelled on LNReader's reader searchbar: a text field with a `current/total`
 * counter and up/down chevrons that step through hits. Replaces the top bar while open rather than
 * stacking below it, so the reading area doesn't lose more height than it has to.
 */
@Composable
private fun NovelReaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    currentMatch: Int,
    totalMatches: Int,
    isTruncated: Boolean,
    showMinLengthHint: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // background before windowInsetsPadding so the chrome color paints behind the status bar,
    // same as TopAppBar's own `windowInsets` handling - see the note at its call site.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        val mutedContentColor = contentColor.copy(alpha = 0.6f)
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                    tint = contentColor,
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                cursorBrush = SolidColor(contentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search in chapter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = mutedContentColor,
                        )
                    }
                    innerTextField()
                },
            )

            if (query.isNotBlank() && !showMinLengthHint) {
                Text(
                    text = "$currentMatch/$totalMatches" + if (isTruncated) "+" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedContentColor,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
                )
            }

            IconButton(onClick = onPrev, enabled = totalMatches > 0) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Previous match",
                    tint = if (totalMatches > 0) contentColor else mutedContentColor,
                )
            }
            IconButton(onClick = onNext, enabled = totalMatches > 0) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Next match",
                    tint = if (totalMatches > 0) contentColor else mutedContentColor,
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = contentColor)
                }
            }
        }

        if (showMinLengthHint) {
            Text(
                text = "Type at least $MIN_SEARCH_LENGTH characters",
                style = MaterialTheme.typography.labelSmall,
                color = mutedContentColor,
                modifier = Modifier.padding(
                    start = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.small,
                ),
            )
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
                modifier = Modifier
                    .padding(top = MaterialTheme.padding.small)
                    .horizontalScroll(rememberScrollState()),
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
