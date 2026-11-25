package eu.kanade.presentation.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Matches *any* HTML tag, not just the styled ones - anything not recognized below (`<h2>`,
 * `<div>`, `<span>`, ...) still needs to be consumed and dropped rather than left as literal
 * angle-bracket text in the output. */
private val ANY_TAG_REGEX = Regex("<(/?)\\s*([a-zA-Z0-9]+)[^>]*>")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "mdash" to "—",
    "ndash" to "–",
    "hellip" to "…",
    // Curly quotes - very common in translated web novels, and their absence here was showing
    // up as literal "&ldquo;"/"&rdquo;" text mid-sentence in the reader.
    "ldquo" to "“",
    "rdquo" to "”",
    "lsquo" to "‘",
    "rsquo" to "’",
    "laquo" to "«",
    "raquo" to "»",
    "bull" to "•",
    "middot" to "·",
    "deg" to "°",
    "times" to "×",
    "divide" to "÷",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "prime" to "′",
    "Prime" to "″",
    "dagger" to "†",
    "Dagger" to "‡",
    "permil" to "‰",
    "euro" to "€",
    "pound" to "£",
    "yen" to "¥",
    "cent" to "¢",
    "sect" to "§",
    "para" to "¶",
    "ensp" to " ",
    "emsp" to " ",
    "thinsp" to " ",
    "zwj" to "‍",
    "zwnj" to "‌",
    "shy" to "­",
)

/** One entity occurrence: `&name;`, `&#123;`, or `&#xAb;`. */
private val ENTITY_REGEX = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);")

/**
 * Decodes HTML entities in a **single pass**, so an already-escaped entity survives intact:
 * replacing `&amp;` before the others (as a naive per-entity `replace` loop does) turns
 * `&amp;lt;` into a literal `<` instead of the `&lt;` text the source actually meant.
 *
 * Unknown named entities are left as-is rather than dropped - showing `&foo;` is less confusing
 * than silently deleting text.
 */
private fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    return ENTITY_REGEX.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.codePointString() ?: match.value
            body.startsWith("#") ->
                body.drop(1).toIntOrNull()?.codePointString() ?: match.value
            else -> NAMED_ENTITIES[body] ?: match.value
        }
    }
}

private fun Int.codePointString(): String? =
    if (this in 1..0x10FFFF) String(Character.toChars(this)) else null

/**
 * Parses a paragraph's inline HTML (`<b>`/`<strong>`, `<i>`/`<em>`, `<u>`, `<br>`) into a real
 * [AnnotatedString], so bold/italic emphasis from the source (e.g. NovelFire chapter bodies)
 * survives into the reader instead of being stripped to plain text as it was before. Any other
 * tag is silently dropped rather than shown as raw markup - block-level structure was already
 * removed a step earlier when the chapter was split into paragraphs.
 */
fun parseInlineHtml(html: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    var openSpans = 0

    for (match in ANY_TAG_REGEX.findAll(html)) {
        if (match.range.first > cursor) {
            append(decodeHtmlEntities(html.substring(cursor, match.range.first)))
        }

        val closing = match.groupValues[1] == "/"
        val tag = match.groupValues[2].lowercase()
        val isStyled = tag == "b" || tag == "strong" || tag == "i" || tag == "em" || tag == "u"

        when {
            tag == "br" -> append("\n")
            closing && isStyled -> {
                if (openSpans > 0) {
                    pop()
                    openSpans--
                }
            }
            tag == "b" || tag == "strong" -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                openSpans++
            }
            tag == "i" || tag == "em" -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                openSpans++
            }
            tag == "u" -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                openSpans++
            }
            // Any other tag (<h2>, <div>, <span>, ...) is dropped entirely - block-level
            // structure was already handled a step earlier when splitting into paragraphs.
        }

        cursor = match.range.last + 1
    }

    if (cursor < html.length) {
        append(decodeHtmlEntities(html.substring(cursor)))
    }
}

/** Translucent wash over every match, so they're findable while skimming without shouting. */
private val SEARCH_MATCH_COLOR = Color(0x66FFEB3B)

/** The one match the up/down chevrons are currently parked on - solid, with forced dark text so
 * it stays legible on light and dark reading themes alike. */
private val SEARCH_ACTIVE_MATCH_COLOR = Color(0xFFFFC107)
private val SEARCH_ACTIVE_MATCH_TEXT_COLOR = Color(0xFF1A1A1A)

/**
 * Overlays in-chapter search highlights on an already-parsed paragraph. Runs against [base]'s
 * *rendered* text rather than the source HTML on purpose - offsets taken from the raw markup would
 * be thrown off by every tag and entity that parsing collapses away.
 *
 * [activeMatchStart] is the offset (within this paragraph) of the currently-selected match, or
 * null when the selected match lives in some other paragraph.
 */
fun highlightSearchMatches(
    base: AnnotatedString,
    query: String,
    activeMatchStart: Int? = null,
): AnnotatedString {
    if (query.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        var index = base.text.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            val style = if (index == activeMatchStart) {
                SpanStyle(background = SEARCH_ACTIVE_MATCH_COLOR, color = SEARCH_ACTIVE_MATCH_TEXT_COLOR)
            } else {
                SpanStyle(background = SEARCH_MATCH_COLOR)
            }
            addStyle(style, index, index + query.length)
            index = base.text.indexOf(query, index + 1, ignoreCase = true)
        }
    }
}

private val WORD_REGEX = Regex("\\S+")

/**
 * Overlays "bionic reading" bold spans (roughly the first half of each word, a fixation-anchor
 * trick some readers find helps eyes track faster) on top of whatever spans [base] already has -
 * so it composes with real bold/italic from the source instead of replacing it.
 */
fun applyBionicReading(base: AnnotatedString): AnnotatedString = buildAnnotatedString {
    append(base)
    WORD_REGEX.findAll(base.text).forEach { word ->
        val length = word.value.length
        val boldLength = when {
            length <= 3 -> 1
            else -> (length * 0.5).toInt().coerceAtLeast(1)
        }
        addStyle(
            SpanStyle(fontWeight = FontWeight.Bold),
            word.range.first,
            word.range.first + boldLength,
        )
    }
}
