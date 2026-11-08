package eu.kanade.tachiyomi.ui.reader

/** Block-level wrappers dropped when splitting; inline tags (`<b>`, `<i>`, ...) are kept. */
private val STRIP_BLOCK_TAGS = Regex("(?i)</?(p|h[1-6])[^>]*>")

/** A tag that only ever appears in markup meant to be read as HTML, not as plain text. */
private val STRUCTURAL_TAG = Regex("(?i)</?(?:p|br|h[1-6]|div)\\b")

private const val BR = "<br\\b[^>]*>"
private val BR_REGEX = Regex("(?i)$BR")

/** Paragraph boundaries in HTML mode: block ends, 2+ consecutive `<br>` (whitespace between them
 * allowed), or a blank line. */
private val HTML_BREAK_REGEX = Regex("(?i)</p\\s*>|</h[1-6]\\s*>|$BR(?:\\s*$BR)+|\n{2,}")

/** Plain-text paragraph boundaries - unchanged from before HTML mode existed. */
private val TEXT_BREAK_REGEX = Regex("(?i)</p>|</h[1-6]>|<br\\s*/?>\\s*<br\\s*/?>|\n{2,}")

private val WHITESPACE_REGEX = Regex("\\s+")
private val BR_WITH_SPACE_REGEX = Regex("(?i)\\s*$BR\\s*")
private val EDGE_JUNK = "(?:\\s|\u00A0|<br>|&nbsp;|&#160;|&#xa0;)+"
private val LEADING_JUNK_REGEX = Regex("(?i)^$EDGE_JUNK")
private val TRAILING_JUNK_REGEX = Regex("(?i)$EDGE_JUNK$")
private val ANY_TAG_REGEX = Regex("<[^>]*>")
private val INLINE_TAG_REGEX = Regex("(?i)<(/?)(b|strong|i|em|u)\\b[^>]*>")

/** Lines in one block at which it is treated as a "one line per paragraph" chapter. */
private const val LINE_PER_PARAGRAPH_THRESHOLD = 3

/**
 * Splits chapter content into paragraphs, keeping *inline* tags (`<b>`, `<i>`, etc.) intact so
 * [eu.kanade.presentation.reader.parseInlineHtml] can render them as real bold/italic spans
 * instead of losing formatting - only block-level `<p>` wrappers and paragraph boundaries are
 * stripped here.
 *
 * Two modes, picked per chapter by [STRUCTURAL_TAG] (a `<p>`, `<br>`, `<h1-6>` or `<div>` anywhere):
 *
 * - **Plain text** (no such tag) splits on blank lines only, exactly as before. Single newlines
 *   stay in the paragraph and render as line breaks.
 * - **HTML** follows HTML semantics: runs of whitespace (source newlines and Jsoup's pretty-print
 *   indentation included) collapse to one space, and `<br>` is the only line break. See
 *   [splitHtmlParagraphs].
 *
 * Reading progress is stored as an index into this list, so for ordinary one-`<p>`-per-paragraph
 * markup the output must stay identical to the plain `</p>` split.
 */
internal fun splitNovelParagraphs(text: String): List<String> {
    if (!STRUCTURAL_TAG.containsMatchIn(text)) return splitPlainTextParagraphs(text)
    return splitHtmlParagraphs(text).ifEmpty { listOf("") }
}

private fun splitPlainTextParagraphs(text: String): List<String> {
    // A closing heading tag ends a paragraph just like </p> does. Without it, a chapter body
    // shaped like "<h4>Title</h4><p>First sentence...</p>" - which is what NovelArrow and
    // Webnovel both produce, since they prepend the chapter name as a heading - collapsed the
    // title and the opening sentence into a single chunk. That chunk is index 0, which the
    // reader renders force-bolded as the chapter title, so the first sentence of every chapter
    // got swallowed into the bold title line.
    val split = text.split(TEXT_BREAK_REGEX)
        .map { it.replace(STRIP_BLOCK_TAGS, "").trim() }
        .filter { it.isNotEmpty() }
    return split.ifEmpty { listOf(text.replace(STRIP_BLOCK_TAGS, "").trim()) }
}

/**
 * HTML-mode splitting. Jsoup's pretty-printed `outerHtml` (what `selectHtml` returns) puts a
 * newline and indentation around every `<br>`, so one `<p>` holding a whole chapter arrives as
 * `<p>Line one\n  <br>\n  Line two\n  <br>...</p>`. Treating that whitespace as real text rendered
 * each line break as a blank line plus an indent and left the chapter as a single paragraph.
 *
 * Per block: collapse whitespace, drop whitespace around `<br>`, trim edges, discard it if blank.
 * A block with [LINE_PER_PARAGRAPH_THRESHOLD] or more `<br>` is a one-line-per-paragraph chapter
 * and is split at each one (keeps progress granularity, search and title de-duplication working);
 * with fewer they stay as in-paragraph line breaks ("Q. ...<br>A. No.").
 */
private fun splitHtmlParagraphs(text: String): List<String> {
    val result = mutableListOf<String>()
    for (chunk in text.split(HTML_BREAK_REGEX)) {
        val block = normalizeHtmlBlock(chunk.replace(STRIP_BLOCK_TAGS, ""))
        if (block.isBlankParagraph()) continue
        if (BR_REGEX.findAll(block).count() >= LINE_PER_PARAGRAPH_THRESHOLD) {
            result += splitAtLineBreaks(block)
        } else {
            result += block
        }
    }
    return result
}

/** Collapses whitespace to single spaces (a non-breaking space is not collapsed), normalizes every
 * `<br>` to a bare one with no whitespace around it, and trims whitespace/nbsp/`<br>` off both ends. */
private fun normalizeHtmlBlock(block: String): String = block
    .replace(WHITESPACE_REGEX, " ")
    .replace(BR_WITH_SPACE_REGEX, "<br>")
    .replace(LEADING_JUNK_REGEX, "")
    .replace(TRAILING_JUNK_REGEX, "")

/** True when nothing but tags, whitespace and non-breaking spaces is left. */
private fun String.isBlankParagraph(): Boolean = this
    .replace(ANY_TAG_REGEX, "")
    .replace(Regex("(?i)&nbsp;|&#160;|&#xa0;"), "")
    .replace('\u00A0', ' ')
    .isBlank()

/**
 * Splits [block] at each `<br>`. An inline tag still open at a split is closed at the end of that
 * paragraph and reopened at the start of the next, so `<i>one<br>two</i>` keeps both lines italic
 * instead of leaving the second one plain. Unbalanced markup stays harmless either way: the
 * renderer ignores a stray close tag and closes a dangling open one.
 */
private fun splitAtLineBreaks(block: String): List<String> {
    val open = ArrayDeque<String>()
    val result = mutableListOf<String>()
    for (line in block.split(BR_REGEX)) {
        val prefix = open.joinToString("") { "<$it>" }
        for (tag in INLINE_TAG_REGEX.findAll(line)) {
            val name = tag.groupValues[2].lowercase()
            if (tag.groupValues[1].isEmpty()) {
                open.addLast(name)
            } else {
                val at = open.lastIndexOf(name)
                if (at >= 0) open.removeAt(at)
            }
        }
        val suffix = open.reversed().joinToString("") { "</$it>" }
        val paragraph = prefix + line + suffix
        if (!paragraph.isBlankParagraph()) result += paragraph.trim()
    }
    return result
}
