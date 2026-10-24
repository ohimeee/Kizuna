package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.tachiyomi.ui.reader.setting.NovelFontFamily
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.NovelReaderTheme
import eu.kanade.tachiyomi.ui.reader.setting.NovelTextAlign
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat
import kotlin.math.roundToInt
import tachiyomi.core.common.preference.Preference as PreferenceData

object SettingsReaderScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val readerPref = remember { context.appGraph.readerPreferences }
        val novelReaderPref = remember { context.appGraph.novelReaderPreferences }

        // Manga/manhwa/manhua reader settings below are Mihon's own and stay untouched by this
        // tab switcher - only which group of settings is SHOWN toggles, nothing about how either
        // reader type itself behaves. Lets novel reader defaults be configured from global
        // Settings instead of only from a bottom sheet inside an already-open chapter.
        var selectedTab by remember { mutableIntStateOf(0) }

        val tabSwitcher = Preference.PreferenceItem.CustomPreference(title = "Reader type") {
            BasePreferenceWidget(
                subcomponent = {
                    MultiChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(intrinsicSize = IntrinsicSize.Min)
                            .padding(horizontal = PrefsHorizontalPadding),
                    ) {
                        SegmentedButton(
                            modifier = Modifier.fillMaxHeight(),
                            checked = selectedTab == 0,
                            onCheckedChange = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) {
                            Text("Manga")
                        }
                        SegmentedButton(
                            modifier = Modifier.fillMaxHeight(),
                            checked = selectedTab == 1,
                            onCheckedChange = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) {
                            Text("Novel")
                        }
                    }
                },
            )
        }

        if (selectedTab == 1) {
            return listOf(
                tabSwitcher,
                getNovelReaderGroup(novelReaderPreferences = novelReaderPref),
                getNovelGeneralGroup(novelReaderPreferences = novelReaderPref),
            )
        }

        return listOf(
            tabSwitcher,
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.defaultReadingMode,
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) },
                title = stringResource(MR.strings.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.doubleTapAnimSpeed,
                entries = mapOf(
                    1 to stringResource(MR.strings.double_tap_anim_speed_0),
                    500 to stringResource(MR.strings.double_tap_anim_speed_normal),
                    250 to stringResource(MR.strings.double_tap_anim_speed_fast),
                ),
                title = stringResource(MR.strings.pref_double_tap_anim_speed),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showReadingMode,
                title = stringResource(MR.strings.pref_show_reading_mode),
                subtitle = stringResource(MR.strings.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showNavigationOverlayOnStart,
                title = stringResource(MR.strings.pref_show_navigation_mode),
                subtitle = stringResource(MR.strings.pref_show_navigation_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.pageTransitions,
                title = stringResource(MR.strings.pref_page_transitions),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.loadAheadOnlyOverWifi,
                title = stringResource(MR.strings.pref_load_ahead_only_over_wifi),
                subtitle = stringResource(MR.strings.pref_load_ahead_only_over_wifi_summary),
            ),
            getDisplayGroup(readerPreferences = readerPref),
            getEInkGroup(readerPreferences = readerPref),
            getReadingGroup(readerPreferences = readerPref),
            getPagedGroup(readerPreferences = readerPref),
            getWebtoonGroup(readerPreferences = readerPref),
            getNavigationGroup(readerPreferences = readerPref),
            getActionsGroup(readerPreferences = readerPref),
        )
    }

    /** Splits an ENUM_CONSTANT name into "Enum Constant" for display, matching the label style
     * NovelReaderScreen's own in-chapter settings sheet already uses for these same enums. */
    private fun enumLabel(name: String) = name.split("_").joinToString(" ") {
        it.lowercase().replaceFirstChar(Char::uppercase)
    }

    @Composable
    private fun getNovelReaderGroup(novelReaderPreferences: NovelReaderPreferences): Preference.PreferenceGroup {
        // The slider works in whole numbers, so line height (a Float) is shown in tenths: 14 = 1.4x
        val lineSpacingPref = remember(novelReaderPreferences) { TenthsPreference(novelReaderPreferences.lineSpacing) }
        val minLineSpacing = (NovelReaderPreferences.MIN_LINE_SPACING * 10).roundToInt()
        val maxLineSpacing = (NovelReaderPreferences.MAX_LINE_SPACING * 10).roundToInt()

        return Preference.PreferenceGroup(
            title = "Reader",
            preferenceItems = listOf(
                Preference.PreferenceItem.SliderPreference(
                    preference = novelReaderPreferences.fontSize,
                    valueRange = NovelReaderPreferences.MIN_FONT_SIZE..NovelReaderPreferences.MAX_FONT_SIZE,
                    title = "Text size",
                    valueText = { "${it}sp" },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = novelReaderPreferences.theme,
                    entries = NovelReaderTheme.entries.associateWith { enumLabel(it.name) },
                    title = "Color",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = novelReaderPreferences.textAlign,
                    entries = NovelTextAlign.entries.associateWith { enumLabel(it.name) },
                    title = "Text alignment",
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = lineSpacingPref,
                    valueRange = minLineSpacing..maxLineSpacing,
                    title = "Line height",
                    valueText = { "${"%.1f".format(it / 10f)}x" },
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = novelReaderPreferences.contentPadding,
                    valueRange = NovelReaderPreferences.MIN_PADDING..NovelReaderPreferences.MAX_PADDING,
                    title = "Padding",
                    valueText = { "${it}dp" },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = novelReaderPreferences.fontFamily,
                    entries = NovelFontFamily.entries.associateWith { enumLabel(it.name) },
                    title = "Font style",
                ),
            ),
        )
    }

    @Composable
    private fun getNovelGeneralGroup(novelReaderPreferences: NovelReaderPreferences): Preference.PreferenceGroup {
        val autoScroll by novelReaderPreferences.autoScroll.collectAsState()

        val minAutoScrollSpeed = NovelReaderPreferences.MIN_AUTO_SCROLL_SPEED
        val maxAutoScrollSpeed = NovelReaderPreferences.MAX_AUTO_SCROLL_SPEED

        return Preference.PreferenceGroup(
            title = "General",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.fullscreen,
                    title = "Fullscreen",
                    subtitle = "Hide the status bar and navigation bar while reading",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.showBatteryAndTime,
                    title = "Battery & time",
                    subtitle = "Show reading progress, battery level and clock over the reading screen, " +
                        "even when hidden",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.showVerticalSeekbar,
                    title = "Vertical seekbar",
                    subtitle = "Show a slider on the side to jump to any point in the chapter",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.removeExtraSpacing,
                    title = "Remove extra spacing",
                    subtitle = "Reduce blank space between paragraphs",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.bionicReading,
                    title = "Bionic reading",
                    subtitle = "Bold the first part of each word to help guide the eye",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.keepScreenOn,
                    title = "Keep screen on",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = novelReaderPreferences.autoScroll,
                    title = "Auto-scroll",
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = novelReaderPreferences.autoScrollSpeed,
                    valueRange = minAutoScrollSpeed..maxAutoScrollSpeed,
                    title = "Auto-scroll speed",
                    visible = autoScroll,
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val fullscreen by readerPreferences.fullscreen.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.defaultOrientationType,
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) },
                    title = stringResource(MR.strings.pref_rotation_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerTheme,
                    entries = mapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                    title = stringResource(MR.strings.pref_reader_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.fullscreen,
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.drawUnderCutout,
                    title = stringResource(MR.strings.pref_cutout_short),
                    visible = LocalView.current.hasDisplayCutout() && fullscreen,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.keepScreenOn,
                    title = stringResource(MR.strings.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber,
                    title = stringResource(MR.strings.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getEInkGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val flashPageState by readerPreferences.flashOnPageChange.collectAsState()

        val flashColorPref = readerPreferences.flashColor

        return Preference.PreferenceGroup(
            title = "E-Ink",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.flashOnPageChange,
                    title = stringResource(MR.strings.pref_flash_page),
                    subtitle = stringResource(MR.strings.pref_flash_page_summ),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.flashDurationMillis,
                    valueRange = ReaderPreferences.MILLI_CONVERSION.let { it..it * 15 step it },
                    title = stringResource(MR.strings.pref_flash_duration),
                    valueText = { stringResource(MR.strings.pref_flash_duration_summary, it) },
                    visible = flashPageState,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.flashPageInterval,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_flash_page_interval),
                    valueText = { pluralStringResource(MR.plurals.pref_pages, it, it) },
                    visible = flashPageState,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = flashColorPref,
                    entries = mapOf(
                        ReaderPreferences.FlashColor.BLACK to stringResource(MR.strings.pref_flash_style_black),
                        ReaderPreferences.FlashColor.WHITE to stringResource(MR.strings.pref_flash_style_white),
                        ReaderPreferences.FlashColor.WHITE_BLACK
                            to stringResource(MR.strings.pref_flash_style_white_black),
                    ),
                    title = stringResource(MR.strings.pref_flash_with),
                    visible = flashPageState,
                ),
            ),
        )
    }

    @Composable
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reading),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipRead,
                    title = stringResource(MR.strings.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipFiltered,
                    title = stringResource(MR.strings.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipDupe,
                    title = stringResource(MR.strings.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.alwaysShowChapterTransition,
                    title = stringResource(MR.strings.pref_always_show_chapter_transition),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager
        val imageScaleTypePref = readerPreferences.imageScaleType
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged
        val rotateToFitPref = readerPreferences.dualPageRotateToFit

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pager_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pagerNavInverted,
                    entries = listOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = imageScaleTypePref,
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_image_scale_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.zoomStart,
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_zoom_start),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBorders,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.landscapeZoom,
                    title = stringResource(MR.strings.pref_landscape_zoom),
                    visible = imageScaleType == 1,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.navigateToPan,
                    title = stringResource(MR.strings.pref_navigate_pan),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertPaged,
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    visible = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvert,
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    visible = rotateToFit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        val navModePref = readerPreferences.navigationModeWebtoon
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon
        val rotateToFitPref = readerPreferences.dualPageRotateToFitWebtoon

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.webtoon_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.webtoonNavInverted,
                    entries = listOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) },
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.webtoonSidePadding,
                    valueRange = ReaderPreferences.let {
                        it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX
                    },
                    title = stringResource(MR.strings.pref_webtoon_side_padding),
                    valueText = { numberFormat.format(it / 100f) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerHideThreshold,
                    entries = mapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(MR.strings.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(MR.strings.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(MR.strings.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(MR.strings.pref_lowest),
                    ),
                    title = stringResource(MR.strings.pref_hide_threshold),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersWebtoon,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertWebtoon,
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    visible = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvertWebtoon,
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    visible = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDoubleTapZoomEnabled,
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDisableZoomOut,
                    title = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
                ),
            ),
        )
    }

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val readWithVolumeKeysPref = readerPreferences.readWithVolumeKeys
        val readWithVolumeKeys by readWithVolumeKeysPref.collectAsState()

        val verticalNavigator by readerPreferences.verticalNavigator.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_navigation),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readWithVolumeKeysPref,
                    title = stringResource(MR.strings.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithVolumeKeysInverted,
                    title = stringResource(MR.strings.pref_read_with_volume_keys_inverted),
                    visible = readWithVolumeKeys,
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = readerPreferences.verticalNavigator,
                    entries = ReadingMode.entries.filter { it != ReadingMode.DEFAULT }
                        .associate { it to stringResource(it.stringRes) },
                    title = stringResource(MR.strings.pref_vertical_navigator),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.verticalNavigatorOnLeft,
                    title = stringResource(MR.strings.pref_webtoon_vertical_navigator_on_left),
                    visible = verticalNavigator.isNotEmpty(),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.verticalNavigatorHeight,
                    valueRange = 65..100,
                    steps = 6,
                    title = stringResource(MR.strings.pref_vertical_navigator_height),
                    visible = verticalNavigator.isNotEmpty(),
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_actions),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithLongTap,
                    title = stringResource(MR.strings.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.folderPerManga,
                    title = stringResource(MR.strings.pref_create_folder_per_manga),
                    subtitle = stringResource(MR.strings.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }
}

/**
 * Exposes a [Float] preference as an [Int] count of tenths (1.4f <-> 14) so it can drive an
 * integer slider. Reads and writes go straight through to [base], so the stored value, its key
 * and its type are untouched.
 */
private class TenthsPreference(private val base: PreferenceData<Float>) : PreferenceData<Int> {

    override fun key() = base.key()

    override fun get() = base.get().toTenths()

    override fun set(value: Int) = base.set(value / 10f)

    override fun isSet() = base.isSet()

    override fun delete() = base.delete()

    override fun defaultValue() = base.defaultValue().toTenths()

    override fun changes() = base.changes().map { it.toTenths() }

    override fun stateIn(scope: CoroutineScope) = changes().stateIn(scope, SharingStarted.Eagerly, get())

    private fun Float.toTenths() = (this * 10).roundToInt()
}
