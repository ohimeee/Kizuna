package eu.kanade.tachiyomi.ui.reader.setting

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class NovelReaderPreferences(
    preferenceStore: PreferenceStore,
) {

    val fontSize: Preference<Int> = preferenceStore.getInt("pref_novel_reader_font_size", 16)

    val lineSpacing: Preference<Float> = preferenceStore.getFloat("pref_novel_reader_line_spacing", 1.4f)

    val theme: Preference<NovelReaderTheme> = preferenceStore.getEnum(
        "pref_novel_reader_theme",
        NovelReaderTheme.FOLLOW_SYSTEM,
    )

    companion object {
        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 32
        const val MIN_LINE_SPACING = 1.0f
        const val MAX_LINE_SPACING = 2.2f
    }
}

enum class NovelReaderTheme(val backgroundColor: Long, val textColor: Long) {
    FOLLOW_SYSTEM(0x00000000, 0x00000000), // resolved from the app theme instead of these fields
    LIGHT(0xFFFFFFFF, 0xFF1A1A1A),
    DARK(0xFF121212, 0xFFE0E0E0),
    SEPIA(0xFFF4ECD8, 0xFF5B4636),
}
