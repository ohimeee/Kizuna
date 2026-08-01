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
  - **The footer is part of the hideable chrome** — its `AnimatedVisibility` is keyed on
    `showBatteryAndTime && showControls`, so it appears/disappears with the top bar and seekbar.
    **This reverses an earlier decision** (2026-08-01) where it was keyed on `showBatteryAndTime`
    alone and stayed pinned even while the rest of the chrome was tap-hidden. That version meant a
    permanent strip of numbers on every screen of reading, and it needed an opaque page-colored
    backdrop to stop body text colliding with it — which carved a solid band out of the page.
    Changed after comparing side-by-side against LNReader's reading mode, which draws no
    always-on footer widget at all. If you're reinstating the always-visible behavior, the opaque
    backdrop has to come back with it.
  - Backdrop is now the same translucent `chromeBackground` the top bar and seekbar use, not the
    solid reading-theme page color.
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
- **No percentage label on the seekbar.** The footer status bar already shows the exact same
  `readingProgressPercent()` value, so having both rendered the same number twice on screen at
  once. Removed 2026-08-01; don't add it back without also removing it from the footer.

## Chapter switching

- **No persistent prev/next chapter button row.** Removed on request — replaced by two things:
  1. Inline pills inside the scrolling content itself: "Finished: {chapter name}" text +
     "Next: {name}" pill at the very end of the chapter (only when a next chapter exists).
  2. A **pull-down-past-the-top gesture** for the previous chapter, replacing what used to be a
     "Previous: {name}" pill at the top. Two-phase rubber-band resistance: smooth/easy pull for
     the first ~40dp, then noticeably harder resistance for the next ~30dp — reaching the hard
     limit **is** the confirmation, no release-to-confirm step. Hint text fades in while pulling
     ("Pull down for previous chapter" → "Release to go to previous chapter" once past the soft
     limit).
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
