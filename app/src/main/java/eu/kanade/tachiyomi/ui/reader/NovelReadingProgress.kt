package eu.kanade.tachiyomi.ui.reader

/**
 * The reader's reading-progress percent, 0..100. Shared by the reader's status strip and by what
 * [NovelReaderViewModel] saves for the History screen, so the two always show the same number.
 *
 * [index] is the first visible paragraph and [total] the paragraph count. Reports 100 once the list
 * can no longer scroll forward ([isAtEnd]) regardless of the raw index/total ratio, since the trailing
 * "Finished"/next-chapter footer item pads the list past the last paragraph and would otherwise make
 * the percentage stall short of 100 at the true end of the chapter. Truncated, not rounded.
 */
fun novelProgressPercent(index: Int, total: Int, isAtEnd: Boolean): Int {
    if (total <= 1) return 100
    if (isAtEnd) return 100
    return ((index.toFloat() / (total - 1)) * 100).toInt().coerceIn(0, 100)
}
