package eu.kanade.presentation.reader

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

private val HTML_ENTITIES = mapOf(
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
    "&apos;" to "'",
    "&nbsp;" to " ",
    "&mdash;" to "—",
    "&ndash;" to "–",
    "&hellip;" to "…",
)

private fun decodeHtmlEntities(text: String): String {
    var result = text
    HTML_ENTITIES.forEach { (entity, char) -> result = result.replace(entity, char) }
    return result
}

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
