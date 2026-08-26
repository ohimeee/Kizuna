# Novel reader UI — requirements log

Living spec for `NovelReaderScreen.kt` / `NovelReaderActivity.kt` / `NovelReaderViewModel.kt` /
`NovelReaderPreferences.kt`. Built up from the user's actual requests across sessions, in the
order given. **Read this before editing the novel reader UI** — several rounds of edits have
accidentally undone earlier requests; this file exists so that stops happening.

Scope note: **only the novel reader gets this treatment.** Manga/manhwa/manhua reader UI (Mihon's
own `ReaderActivity`) stays untouched — explicit instruction, no changes there ever.

## Global Settings mirror

`NovelReaderPreferences` is also exposed from the app's global **Settings > Reader** screen
(`app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsReaderScreen.kt`), not
just the in-chapter bottom sheet — a Manga/Novel segmented-button switcher (`CustomPreference` +
`MultiChoiceSegmentedButtonRow`, `selectedTab` state local to `getPreferences()`) toggles which
preference-group list is shown; picking "Novel" swaps in `getNovelReaderGroup()` /
`getNovelGeneralGroup()` instead of the manga groups. Manga reader settings/behavior are
completely unaffected - only which group is *displayed* changes.

**If you add/remove/rename a preference in `NovelReaderPreferences.kt`, mirror the same change in
`SettingsReaderScreen.kt`'s two novel group functions** - these are two independent UIs reading
the same underlying `Preference<T>` objects, and nothing enforces they stay in sync automatically.
Titles/subtitles there are plain hardcoded strings (not `stringResource(MR.strings...)`) to match
how the in-chapter sheet already does it - this fork doesn't localize the novel-specific additions.

## Rich text

- Source HTML's inline formatting (`<b>`/`<strong>`, `<i>`/`<em>`, `<u>`) must render as real
  bold/italic/underline spans, not be stripped to plain text. Any *other* tag (`<h2>`, `<div>`,
  etc.) must be dropped silently, not leaked as literal `<tag>` text.
  - Implemented in `NovelRichText.kt`: `parseInlineHtml()` (tag → `AnnotatedString` spans) +
    `applyBionicReading()` (overlays bold on first ~half of each word, composes on top of real
    bold/italic rather than replacing it).
  - `NovelReaderViewModel.splitParagraphs()` must keep inline tags intact when splitting into
    paragraphs — only strip block-level `<p>` wrappers.

## Settings sheet (Reader tab / General tab, matching LNReader's layout)

Reader tab: text size, color (theme presets — white/cream/mint/gray/black/follow-system, swatch
row with checkmark), text alignment (left/center/justify/right, custom-drawn icons since the
extended Material icon pack isn't a dependency), line height, padding, font style
(Original/Serif/Sans Serif/Monospace generic system families, **plus Lora and Nunito as real
bundled fonts** — variable-weight OFL-licensed TTFs under `app/src/main/res/font/`
(`lora.ttf`/`lora_italic.ttf`/`nunito.ttf`/`nunito_italic.ttf`), license text under
`docs/third-party-licenses/fonts/`. Built into actual Compose `FontFamily`s
(`LoraFontFamily`/`NunitoFontFamily` in `NovelReaderScreen.kt`) using `FontVariation.weight(...)`
per entry since each is a single variable-font file per style, not separate static files per
weight — reversing an earlier decision that ruled this out as "out of scope"; the user asked for
Lora specifically by name).

The chapter **title is always the first paragraph** (the source HTML's own heading line — see
`NovelReaderViewModel.splitParagraphs`), not a separately-modeled field. It's rendered forced-bold
(`fontWeight = FontWeight.Bold` on that one `Text`, regardless of any inline styling in the source
HTML) with one full blank line of extra space before the body text starts (`paragraphSpacing +
(fontSize * lineSpacing).dp` as the bottom padding on just that item) — always, independent of the
"remove extra spacing" preference. Implemented in the `itemsIndexed(paragraphs)` loop in
`NovelReaderContent`, keyed off `index == 0`.

Since 2026-08-26 it is also **scaled up to read as a real heading** — `CHAPTER_TITLE_SCALE` (1.7x)
with its own tighter `CHAPTER_TITLE_LINE_SPACING`, modelled on LNReader. Two things to preserve:

- The scale is a **multiple of the reader's own font size**, never a fixed `sp` value, so the
  heading keeps its proportion at every text size.
- The heading treatment is gated on **length as well as position** (`CHAPTER_TITLE_MAX_LENGTH`).
  `splitParagraphs` only splits on block boundaries, so "first paragraph is the title" is a
  convention, not a guarantee — a source whose chapter opens straight into prose would otherwise
  get a whole paragraph set at 1.7x. Bold and the trailing blank line still apply either way.

`CHAPTER_TOP_PADDING` puts space above that title (the `top` of the `LazyColumn`'s
`contentPadding`). The reader draws edge-to-edge and hides the system bars while reading, at which
point the ancestor's `systemBars` inset collapses to zero and the title sat flush against the top
of the screen, close enough to a punch-hole camera to be clipped. **Keep it a flat value** — using
the `displayCutout` inset was tried and reverted: it is already covered by the `systemBars` padding
while the bars are up (so the text jumped by a cutout's height every time the chrome was toggled),
and it reports zero while the bars are hidden, which is the exact case it was meant for.

Chapter text sits in a **`SelectionContainer`** so it can be long-pressed, selected and copied —
Compose text is inert without one. Known limit, inherent to rendering chapters in a `LazyColumn`:
a selection dragged well past the viewport only captures paragraphs still composed, so "select
all" across a long chapter won't take everything. Rendering the whole chapter as a single `Text`
would fix it and break paragraph-index progress, search highlighting and the progress percent —
don't.

General tab: fullscreen, battery & time, reading progress, vertical seekbar, remove extra spacing,
bionic reading, keep screen on, auto-scroll (+ speed slider). **TTS explicitly excluded** — user
said "no dont build TTS" when asked; don't add a TTS tab.

All backed by `NovelReaderPreferences.kt`.

## Top bar

- Two-line title, matching LNReader's reader appbar: **chapter name** (`titleMedium`) on top,
  **novel title** (`bodySmall`, `onSurfaceVariant`) underneath as a subtitle. The novel title comes
  from `NovelReaderViewModel.State.mangaTitle`, set once in `loadManga()`. Before this the bar
  showed only the chapter name, which left no indication of which novel was open.
- Actions: open-in-WebView (only when the source provides a URL) and settings. Back arrow as the
  navigation icon.

## System bar icon tint

`NovelReaderActivity` sets `isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` from the
**reading theme's** background luminance (`> 0.5f` → light page → dark icons), not from the app
theme. The reader paints its page color edge-to-edge, including behind the status bar, so a light
reading theme (LIGHT/SEPIA/MINT) inside a dark app theme previously rendered the system clock/
battery/wifi icons white-on-white — the status bar read as missing/broken rather than hidden.
Confirmed by pixel-sampling a screenshot: the strip behind the status bar was already `#FFFFFF`
(the page), so the bar was showing correctly all along and only its icons were invisible.

## Chrome show/hide

- Tapping the reading content toggles all chrome (top bar, seekbar, status bar) — same as Mihon's
  own image reader. Reference: `eu.kanade.presentation.reader.appbars.ReaderAppBars` +
  `ChapterNavigator.kt` in this repo (Mihon's actual reader, already proven/working) — mirror its
  patterns, don't reinvent:
  - Chrome is an **overlay Box on top of full-bleed content**, not `Scaffold` topBar/bottomBar
    slots — Scaffold reflows content as slots animate, which reads as a jarring shift.
  - Transitions are **explicit** `slideInVertically/slideOutVertically(tween(200)) + fadeIn/fadeOut
    (tween(150))` (top bar slides from top, bottom bar from bottom, seekbar from the side). Do
    **not** use `AnimatedVisibility`'s bare default (`fadeIn()+expandIn()`) — that expands from a
    corner and looks like it's "appearing from the side," which is exactly the bug this caused
    once already.
  - Chrome background is **translucent**, not a solid/opaque bar:
    `MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.9f/0.95f dark/light)` —
    same formula Mihon uses.
  - Controls start **hidden** when a chapter is opened (not shown by default) — opening a chapter
    should drop straight into the reading view.
- Tapping to hide the in-app chrome must **also hide the real Android system nav bar** (bottom
  gesture bar). The separate "Fullscreen" preference means both status *and* nav bar stay hidden
  always, regardless of tap state.
- **System status bar and nav bar move together, always tied only to `controlsVisible` (and
  `fullscreen`).** `NovelReaderActivity` uses the combined `WindowInsetsCompat.Type.systemBars()`
  — do not split them again. An earlier attempt made the status bar *also* stay visible whenever
  the "Battery & time" preference was on (independent of `controlsVisible`), reasoning that
  LNReader doesn't draw its own clock/battery widget and just leaves the real status bar up. In
  practice this meant the status bar was visible from the very first frame (the preference
  defaults on) and **never actually hid on tap at all** — reported back as "the navigation bar at
  top didn't disappear." Reverted.
- **The footer (`NovelReaderStatusBar`: reading progress / battery / clock) is a single unit with
  a single governing preference — `showBatteryAndTime`.** There used to be a second, separate
  "Reading progress" toggle that independently gated the progress segment's visibility. Removed on
  request — it's confusing to have one footer with two different visibility rules for its pieces.
  All 3 segments render together or not at all; turning "Battery & time" off hides the whole
  footer. Polls `BatteryManager`/clock itself inside `NovelReaderStatusBar`.
  - **The footer is NOT part of the hideable chrome** — its `AnimatedVisibility` is keyed on
    `showBatteryAndTime` alone, so it stays up even while the top bar and seekbar are tap-hidden.
    Turning the preference off is the only way to hide it.
    - Briefly changed to `showBatteryAndTime && showControls` on 2026-08-01 (after an LNReader
      comparison — LNReader draws no always-on footer widget) and **reverted the same day on
      explicit user request**. The always-visible behavior is deliberate; don't "clean it up"
      again. It's the reason the opaque page-colored backdrop below is required.
  - **Backdrop stays the solid reading-theme page color** (`backgroundColor`), *not* the
    translucent `chromeBackground` the top bar and seekbar use. Switching it to `chromeBackground`
    was tried and reverted the same day: the top bar/seekbar take their content color from the
    app's Material theme, but this footer's text uses the *reading* theme's `textColor`, so the
    two disagreeing (a white reading page inside a dark app theme, the default here) rendered the
    numbers dark-on-dark and essentially invisible. Both colors must come from the same reading
    theme. Page-colored also reads as seamless now that the footer hides on tap, so there's no
    "opaque band" cost to it anymore.
  - Clock uses the **device's own 12h/24h system format**
    (`android.text.format.DateFormat.getTimeFormat(context)`), not a hardcoded `SimpleDateFormat`
    pattern — matches whatever the user has their phone set to instead of always showing 24h time.
  - Segment order is **battery — progress — time** (progress in the middle), not the original
    left-to-right progress/battery/time order from the reference screenshot.
  - The row has a **solid backdrop matching the reading theme's page background color**
    (`Modifier.background(backgroundColor)`, the same `backgroundColor` the page content itself
    sits on — not the translucent `chromeBackground` used by the tap-hideable top bar/seekbar).
    Needed because this footer is always visible now (not gated by `showControls`), so without an
    opaque backing the last line of scrolling body text collides directly with the footer numbers
    instead of stopping short of them.
  - Yes, this contradicts the LNReader research (they really do just leave the OS status bar
    showing, no custom widget). Went with the self-contained widget anyway after two rounds of the
    system-bar-coordination approach reading as "not working" — a Compose-level `if` is trivial to
    verify with a screenshot; real `WindowInsetsController` state interacting with tap-gesture
    timing was not. If revisiting this, that's the tradeoff being made on purpose.

## Vertical seekbar

- Use the **real Material3 `VerticalSlider` + `SliderState`** (see Mihon's own
  `ChapterNavigator.kt` → `VerticalChapterNavigator`), not a hand-rolled `pointerInput`
  drag/tap detector. The hand-rolled version is what caused: gestures fighting with the
  tap-to-hide detector underneath, manual pixel-offset math that made labels overlap the rail,
  and "0"/"100" text sitting on top of the line.
- Visually **minimal**: thin pill (~40dp wide), ~60% of the available height between the top and
  bottom bars (not full length — explicitly called out as "too long" twice).
- Drive the slider off the **same percent value the status bar shows**
  (`readingProgressPercent()`), not the raw `listState.firstVisibleItemIndex` — a short trailing
  paragraph/footer item means the raw index can stall short of the true end, so the fill never
  visually reaches 100% even when the reader is actually at the bottom. Keep both numbers backed
  by one source of truth.
- Interacting with the seekbar (dragging) must force `showControls = true` — seeking must never
  cause the chrome (including the battery/time footer) to disappear mid-interaction.
- **The seekbar keeps its percentage label** above the rail. Yes, this is the same
  `readingProgressPercent()` value the footer also shows — the duplication is intentional and was
  asked for. Removed on 2026-08-01 as "duplicated chrome" and **reverted the same day on explicit
  user request**; leave it alone.

## Bottom navigation bar (added 2026-08-17, on request)

A five-action row — previous chapter, scroll to top, chapter list, bookmark, next chapter —
rendered by `NovelReaderNavBar` and modelled on LNReader's reader footer, which the user supplied
as a reference screenshot.

- **This is NOT the battery/time/progress footer**, and the two must not be merged. This bar is
  part of the **tap-hideable chrome**: same `showControls` gating and same translucent
  `chromeBackground` as the top bar, so it slides away with everything else. The status strip
  keeps its own rules (see the footer section above) — always-on, gated only by
  `showBatteryAndTime`, solid page-colored backdrop.
- **The nav bar is drawn *over* the status strip, not stacked above it** — both are anchored to
  the bottom edge inside `NovelReaderBottomChrome`, so raising the chrome covers the numbers and
  hiding it hands the slot straight back. Stacking them pushed the nav bar up off the bottom edge,
  which was explicitly rejected. Two earlier attempts also got this wrong in the other direction
  by gating the strip on `!showControls` (i.e. hiding it outright) — don't; its visibility rule is
  `showBatteryAndTime` and nothing else.
  - That composable exists as a separate function for a compiler reason, not a stylistic one: with
    a `ColumnScope` receiver in scope, `AnimatedVisibility` resolves to `ColumnScope`'s overload,
    which then can't be called inside the `Box` that does the layering.
  - The nav bar owns the `navigationBars` inset, since it's the bottom-most thing whenever it's up.
- Bookmark and settings are **deliberately swapped relative to LNReader's layout**: settings lives
  in the top bar (where it already was) and the bookmark toggle lives here in the nav bar. Asked
  for explicitly; don't "restore" LNReader's arrangement.
- Prev/next are disabled (dimmed to `DISABLED_ALPHA`) rather than hidden at the first/last chapter,
  so the row never reflows under the reader's thumb.
- The chapter-list button opens `NovelChapterListPanel` — a **slide-in panel over the page, not a
  Material bottom sheet** (asked for explicitly, with a reference screenshot). It's chrome in the
  same sense the bars are: scrim behind it, tap-outside/✕ to dismiss, slides in from the start
  edge.
  - **Painted from the CHROME palette** — `surfaceColorAtElevation(3.dp)` for the panel,
    `chromeContentColor` for its text — *not* the reading theme's colours. This was tried the other
    way first and reverted: the two palettes disagree whenever a light reading theme is used inside
    a dark app theme (the common case), which left a glaringly white panel over a dark UI. Only
    the accent (current chapter, bookmark icon, buttons) comes from `colorScheme.primary`.
  - **The panel is opaque; everything it doesn't cover sits under one uniform dim** — page text
    *and* the status strip alike (`CHAPTER_PANEL_SCRIM_ALPHA`). The bottom chrome is deliberately
    drawn *before* the panel so the strip is dimmed along with the page; drawing it after left the
    numbers punched through at full brightness, which was reported as "not covered".
  - **Opening it hides the top bar and the nav bar** (`showControls && !showChapterList`). The
    status strip is not hidden — it stays put and simply sits under the dim.
  - Needs its own `BackHandler`: it's a hand-rolled overlay, not a `ModalBottomSheet`, so Back
    would otherwise fall through and exit the reader.
  - Starts scrolled to the chapter being read — novels here run to thousands of chapters, so
    opening at the top would strand the reader. The two footer buttons ("Scroll to top" / "Scroll
    to current chapter") move the *list*, not the reader.
  - Rows show read chapters dimmed, a lock icon for chapters the source flagged paywalled
    (`Chapter.isLocked`, e.g. Webnovel VIP), and a bookmark icon where set. Row padding is 18dp
    vertical — deliberately roomier than Mihon's own 12dp chapter rows, since this is a full-height
    picker rather than a dense inline list.
- Bookmark state is read straight off `state.chapter.bookmark` rather than mirrored into a separate
  state field, so there's only one source of truth to keep in sync.
- **The nav bar's Next must not mark the outgoing chapter read.** Only the end-of-chapter pill does
  (`finishAndLoadNextChapter` vs `loadNextChapter`) — the pill is reachable only by scrolling to the
  bottom, whereas Next is reachable from anywhere, and marking-on-Next would flag a chapter read and
  push that to AniList/MAL two paragraphs in. `loadAdjacentChapter` takes `markCurrentRead` for
  exactly this reason; don't collapse the two entry points back together.
- `chapterList` is loaded once per novel, so anything that changes a chapter (progress, read,
  bookmark) has to go through `syncChapterInList` or the panel shows stale state and jumping back
  to a chapter reopens it at an old position.

## Status strip font (2026-08-17)

`NovelReaderStatusBar` renders in the **reader's own font preference** at `bodyMedium`, not the app
default at `labelSmall` — asked for explicitly ("follow the font the user chosen and make it a bit
bigger"). The preference→`FontFamily` mapping lives in one shared `toFontFamily()` helper used by
both the strip and the page body, so the two can't drift apart.

## Auto-scroll

Driven off `withFrameNanos`, not a fixed `delay`. It previously moved a whole `autoScrollSpeed`
jump every 32ms (~31 steps/sec, in lockstep with nothing), which on a 60/90/120Hz panel read as
visibly jagged. Scrolling once per drawn frame, scaled by real elapsed time, keeps it smooth at any
refresh rate and holds the same average speed when a frame is late. Three things there are load-
bearing:

- **The elapsed delta is clamped** (`MAX_AUTO_SCROLL_FRAME_SECONDS`). Compose parks the frame clock
  while the reader is backgrounded, so an unclamped delta replays the whole paused stretch as one
  enormous jump on resume.
- **`CancellationException` from `scrollBy` is swallowed** (re-checking via `ensureActive()`). A
  drag takes the scroll mutex at `UserInput` priority and cancels our `Default`-priority scroll;
  letting that escape ends the loop permanently, and since the `LaunchedEffect` keys don't change
  nothing restarts it — one flick would silently kill auto-scroll until the setting was toggled.
- **One `scrollBy` per frame, not a single long-lived `scroll {}` session** — a session, once
  cancelled by a drag, is gone for good.

Auto-scroll cannot trigger the pull-to-previous gesture: that connection rejects anything whose
`NestedScrollSource` isn't `UserInput`, and programmatic scrolls arrive as `SideEffect`.

## Chapter switching

- **The "no persistent prev/next row" rule below was reversed on 2026-08-17**: prev/next buttons
  now exist in the bottom navigation bar documented above. The inline pills and the pull-down
  gesture both stay as well — the nav bar is an addition, not a replacement. The original reasoning
  is kept below for context.
- **No persistent prev/next chapter button row.** Removed on request — replaced by two things:
  1. Inline pills inside the scrolling content itself: "Finished: {chapter name}" text +
     "Next: {name}" pill at the very end of the chapter (only when a next chapter exists).
  2. A **pull-down-past-the-top gesture** for the previous chapter, replacing what used to be a
     "Previous: {name}" pill at the top. Two-phase rubber-band resistance: smooth/easy pull for
     the first ~40dp, then noticeably harder resistance for the next ~30dp — reaching the hard
     limit **is** the confirmation, no release-to-confirm step. Hint text fades in while pulling
     ("Pull down for previous chapter" → "Release to go to previous chapter" once past the soft
     limit).
     - **Only `NestedScrollSource.UserInput` counts** — the connection must ignore every other
       source. Fling momentum is delivered to `onPostScroll` exactly like a drag is, so before this
       check a hard swipe up from the middle or bottom of a chapter would coast all the way into
       the top boundary and spend its remaining velocity on the pull gesture, navigating to the
       previous chapter when the reader only wanted to scroll back and re-read something. Reported
       and fixed 2026-08-01. Don't drop this check: the gesture is meant to require a deliberate,
       finger-on-screen pull, and momentum can't express intent.
     - Implemented via `NestedScrollConnection.onPostScroll` on the `LazyColumn`. **Known subtle
       bug already hit once:** Compose's scroll-delta sign convention is positive = advance/see
       later content, negative = go back/see earlier content. A downward pull at the top (wanting
       the *previous* chapter) arrives as **negative** `available.y`. Getting this sign backwards
       silently makes the gesture a no-op — no crash, no error, it just never fires. If this stops
       working again, check the sign check first before re-tuning the damping curve.
     - **Sign convention, confirmed via live logcat trace** (positive `available.y` = pulling
       backward/previous, negative = advancing/next — verified by logging real
       `onPostScroll(consumed, available, ...)` calls during actual on-device drags in both
       directions). A first "fix" attempt flipped this the wrong way based on reasoning alone
       without a trace, which made it worse, not better — the working condition is
       `available.y <= 0f -> reject` (i.e. only act on **positive** `available.y`), `pull =
       available.y` (no negation). Do not flip this without a fresh trace confirming which way is
       actually correct on that day's build.
     - **Known test-methodology trap, not an app bug:** on a chapter short enough to fit within
       one screen (`canScrollForward`/`canScrollBackward` both already false at rest), a full-screen
       swipe can land its release point on the "Next: …" pill below the last paragraph and get
       read as a tap on it instead of engaging the scroll/nested-scroll machinery at all — looks
       exactly like "the pull gesture went to the wrong chapter" but is really an artifact of
       testing on a too-short chapter. Test this gesture on a chapter with enough content to
       definitely scroll (check paragraph count, or watch `idx` climb in a debug trace).
     - **The actual root cause of "next/previous feel swapped" (confirmed, fix verified
       on-device): nothing to do with this gesture's code at all.** It was `chapterList`
       ordering. `tachiyomi.domain.chapter.service.ChapterSort`'s `CHAPTER_SORTING_SOURCE` branch
       has an inverted comparator relative to every other sort mode (`sortDescending=false` there
       means `c2.compareTo(c1)`, i.e. descending, unlike NUMBER/UPLOAD_DATE/ALPHABET) — it's built
       around the standard *manga-extension* convention of returning `chapterList()` newest-first
       (see the comment in `SyncChaptersWithSource.await()`: "Sources MUST return the chapters
       from most to less recent"), so `sourceOrder` (assigned by raw list index) naturally
       *decreases* with chapter number for a well-behaved manga extension, and the inverted
       comparator undoes that back to ascending. `NovelReaderViewModel`/Mihon's own
       `ReaderViewModel` both hardcode `getChapterSort(manga, sortDescending = false)` for
       prev/next regardless of the manga's own display-sort preference — this is intentional
       (reading order stays consistent no matter how the chapter list is displayed) and is *not*
       what needs to change. Novel-source JS plugins return `chapterList()` in natural reading
       order (chapter 1 first) — the opposite convention from manga extensions — so a fresh novel
       entry (which defaults to `CHAPTER_SORTING_SOURCE`, value `0x0`) got every prev/next lookup
       backwards. Fixed centrally in `JsNovelSource.kt`'s `getMangaUpdate()` with `.asReversed()`
       on the mapped chapter list, so every plugin can keep writing `chapterList()` in the natural
       order and never needs to know about this.
     - **This fix only applies going forward, on the next real chapter-list sync.** Existing
       library entries added before this fix have `sourceOrder` already stored the old (wrong)
       way, and `SyncChaptersWithSource` only recomputes it when a fresh fetch actually runs — a
       details-screen pull-to-refresh may not trigger one if `LibraryUpdateJob`'s fetch-interval
       throttling decides the manga was checked "recently enough" to skip. If prev/next still
       looks backwards for a novel added before this fix, that's why — needs an explicit
       chapter-list refetch, not a re-test of the gesture code. (Verified the actual fix logic is
       correct by hand-patching `source_order` in the on-device SQLite DB to what the reversed
       fetch would produce, then confirming both the inline "Next: …" pill and the pull-to-previous
       gesture navigated correctly in both directions against that corrected data.)
     - Side effect worth knowing about: this also changed the **manga details screen's default
       chapter list display** for novels, from "chapter 1 at top" to "highest chapter number at
       top" — because that screen sorts with `manga.sortDescending()` (defaults to `true`/
       descending, `CHAPTER_SORT_DESC = 0x0`), and previously-wrong-direction `sourceOrder`
       happened to cancel out against that default in a way that displayed as ascending by
       accident. User wanted chapter-1-first as the default for novels (matching NovelFire's own
       app behavior), not vanilla Mihon's newest-first — fixed properly, see below.
     - **Fixing the display-default took two attempts; the first one looked right but silently
       didn't work.** First attempt: `NetworkToLocalManga.applyDefaultChapterSort()`
       (`domain/.../manga/interactor/NetworkToLocalManga.kt`) ORs `CHAPTER_SORT_ASC` onto a
       novel's `chapterFlags` before the very first DB insert. This is necessary but **not
       sufficient** - confirmed via live logcat trace that the insert *does* set the flag
       correctly (verified `chapter_flags=1` in the DB for freshly-inserted, never-opened novels
       from a Browse grid) - but the flag gets **silently overwritten back to the global default**
       the moment the user actually opens that novel's details screen. Root cause:
       `MangaViewModel.init` (`app/.../ui/manga/MangaViewModel.kt:237-239`) unconditionally calls
       `setMangaDefaultChapterFlags.await(manga)` for any non-favorited manga, on *every single
       details-screen visit* - not just the first. `SetMangaDefaultChapterFlags.await()`
       (`domain/.../chapter/interactor/SetMangaDefaultChapterFlags.kt`) rebuilds `chapterFlags`
       from scratch using the user's *global* library preference
       (`libraryPreferences.sortChapterByAscendingOrDescending`, a manga-extension-oriented
       default) and writes it via a plain overwrite (`MangaUpdate(chapterFlags = ...)`, not a
       coalesce/merge) - clobbering whatever `NetworkToLocalManga` had set moments earlier. This
       is why the bug looked intermittent/nondeterministic during testing: passively-browsed grid
       items (never opened) kept their correct insert-time flag, but the exact item that got
       tapped and opened always lost it.
     - **Real fix**: made `SetMangaDefaultChapterFlags` itself novel-aware instead of relying on
       insert-time timing. It now takes a `SourceManager` dependency and checks
       `sourceManager.get(manga.source)?.contentType == SourceContentType.NOVEL`; if true it
       passes `sortingDirection = Manga.CHAPTER_SORT_ASC` instead of the global preference value.
       This is idempotent and fires on every visit regardless of race conditions, unlike patching
       only the insert path. `NetworkToLocalManga`'s insert-time default was left in place too
       (harmless, and covers the brief window before a details screen is ever opened) but is no
       longer the thing actually enforcing this for viewed novels.
     - Once a novel is favorited (`manga.favorite == true`), `SetMangaDefaultChapterFlags` stops
       being called at all (`MangaViewModel`'s `if (!manga.favorite)` guard), so the ascending
       default freezes at whatever it was at that point - consistent with "never overwrite a
       user's own sort choice on a manga already in the library" elsewhere in this codebase. The
       user can still flip it manually via the Sort tab same as any manga.

## Tracker sync

`NovelReaderViewModel.updateProgress()` mirrors `ReaderViewModel`'s tracker-sync call
(`updateTrackChapterRead`) when a chapter transitions from unread to read (`markRead && !wasRead`,
not on every scroll tick) - same `TrackChapter.await(context, manga.id, chapter.chapterNumber)`
call, same `incognitoMode`/`trackPreferences.autoUpdateTrack` guards, via the same
`GetIncognitoState`/`TrackPreferences`/`TrackChapter` interactors injected as extra constructor
params. Before this, finishing a novel chapter updated the local DB (`read = true`) but never
pushed "chapters read" to AniList/MAL/etc - found during a broader audit of manga-only assumptions
elsewhere in the app (Updates screen's "Page: N" progress label was also wrong for novels, chapter
downloads' download-queue UI showed a meaningless "N/1" for novel chapters - both fixed
separately, outside this file's scope, in `UpdatesUiItem.kt` and `DownloadHolder.kt`).

## General debugging note for this screen

Several rounds of fixes here were architecturally-plausible-but-wrong because they were tuned
without ground-truth data (e.g. the overscroll sign bug survived one full "fix" attempt that only
adjusted damping numbers, not the actual inverted condition). When a gesture/interaction bug is
reported as "still not working" after a fix, prefer adding temporary logcat output and reproducing
on the emulator over reasoning from first principles again.
