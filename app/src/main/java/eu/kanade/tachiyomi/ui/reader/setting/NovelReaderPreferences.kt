package eu.kanade.tachiyomi.ui.reader.setting

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NovelReaderPreferences(
    preferenceStore: PreferenceStore,
) {

    val fontSize: Preference<Int> = preferenceStore.getInt("pref_novel_reader_font_size", 16)

    val lineSpacing: Preference<Float> = preferenceStore.getFloat("pref_novel_reader_line_spacing", 1.4f)

    val contentPadding: Preference<Int> = preferenceStore.getInt("pref_novel_reader_padding", 16)

    val theme: Preference<NovelReaderTheme> = preferenceStore.getEnum(
        "pref_novel_reader_theme",
        NovelReaderTheme.FOLLOW_SYSTEM,
    )

    val textAlign: Preference<NovelTextAlign> = preferenceStore.getEnum(
        "pref_novel_reader_text_align",
        NovelTextAlign.LEFT,
    )

    val fontFamily: Preference<NovelFontFamily> = preferenceStore.getEnum(
        "pref_novel_reader_font_family",
        NovelFontFamily.ORIGINAL,
    )

    // General

    val fullscreen: Preference<Boolean> = preferenceStore.getBoolean("pref_novel_reader_fullscreen", false)

    val keepScreenOn: Preference<Boolean> = preferenceStore.getBoolean("pref_novel_reader_keep_screen_on", true)

    val showBatteryAndTime: Preference<Boolean> =
        preferenceStore.getBoolean("pref_novel_reader_show_battery_time", true)

    val showVerticalSeekbar: Preference<Boolean> =
        preferenceStore.getBoolean("pref_novel_reader_show_seekbar", true)

    val removeExtraSpacing: Preference<Boolean> =
        preferenceStore.getBoolean("pref_novel_reader_remove_extra_spacing", false)

    val bionicReading: Preference<Boolean> = preferenceStore.getBoolean("pref_novel_reader_bionic_reading", false)

    val autoScroll: Preference<Boolean> = preferenceStore.getBoolean("pref_novel_reader_auto_scroll", false)

    val autoScrollSpeed: Preference<Int> = preferenceStore.getInt("pref_novel_reader_auto_scroll_speed", 5)

    companion object {
        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 32
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.6f
        const val MIN_PADDING = 0
        const val MAX_PADDING = 32
        const val MIN_AUTO_SCROLL_SPEED = 1
        const val MAX_AUTO_SCROLL_SPEED = 10
    }
}

enum class NovelReaderTheme(val backgroundColor: Long, val textColor: Long) {
    FOLLOW_SYSTEM(0x00000000, 0x00000000), // resolved from the app theme instead of these fields
    LIGHT(0xFFFFFFFF, 0xFF1A1A1A),
    SEPIA(0xFFF4ECD8, 0xFF5B4636),
    MINT(0xFFE0EDE4, 0xFF203A2C),
    GRAY(0xFF2B2B2B, 0xFFE0E0E0),
    BLACK(0xFF000000, 0xFFD0D0D0),
}

enum class NovelTextAlign {
    LEFT,
    CENTER,
    JUSTIFY,
    RIGHT,
}

/**
 * Font families available in the reader. `ORIGINAL`/`SERIF`/`SANS_SERIF`/`MONOSPACE` map to
 * Android's built-in generic families; `LORA`/`NUNITO` are actual bundled fonts (variable-weight
 * OFL-licensed TTFs under `res/font/`, license text under `docs/third-party-licenses/fonts/`) -
 * see the `fontFamily` resolution in `NovelReaderScreen.kt` for how each is built into a real
 * Compose `FontFamily`.
 */
enum class NovelFontFamily {
    ORIGINAL,
    SERIF,
    SANS_SERIF,
    MONOSPACE,
    LORA,
    NUNITO,
}
