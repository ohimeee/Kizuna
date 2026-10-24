package tachiyomi.domain.entrylink.service

/**
 * Title similarity for suggesting entry links (a comic and its novel adaptation, or the reverse).
 * Sources decorate the same title differently ("Solo Leveling", "Solo Leveling (Novel)",
 * "Solo Leveling [Official]"), so titles are normalised first and then scored in three tiers.
 */

/** Scores at or above this are a confident match: the titles are equal, or one contains the other. */
const val STRONG_TITLE_MATCH_SCORE = 0.8

/** Scores below this are not worth suggesting. It is the lowest a token overlap can reach. */
const val MIN_SUGGESTION_SCORE = 0.42

/** The shorter title must be at least this long to count as "contained", so a stub can't match. */
private const val MIN_CONTAINED_LENGTH = 6

private const val MIN_JACCARD = 0.6

private const val SCORE_EXACT = 1.0
private const val SCORE_STARTS_WITH = 0.9
private const val SCORE_CONTAINS = 0.8

/** Token overlap tops out here so it never outranks a title that actually contains the other. */
private const val TOKEN_OVERLAP_WEIGHT = 0.7

/** Nudges an entry of the other content type ahead of an otherwise equal one. */
private const val OPPOSITE_TYPE_BONUS = 0.05

// Words that carry no identity on their own, ignored when comparing token sets so that
// "Tower of God" and "Tower God" still agree, and "The X" never matches "The Y" on "The" alone.
private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "to", "in", "on")

private val NOISE_WORDS = setOf(
    "novel", "light novel", "web novel", "ln", "wn", "manga", "manhwa", "manhua", "webtoon",
    "comic", "official", "english", "raw", "digital",
)

// A trailing "[anything]" group, or a trailing "(...)" group; the latter is only dropped when its
// content is a noise word, since "(Final Season)" or "(2020)" can be part of what tells titles apart
private val TRAILING_BRACKETS = Regex("""\s*\[[^\[\]]*]\s*$""")
private val TRAILING_PARENS = Regex("""\s*\(([^()]*)\)\s*$""")

/**
 * Lowercases [title], drops trailing noise such as "(Novel)" or "[Official]", turns punctuation and
 * symbols into spaces and collapses whitespace.
 */
fun normalizeTitleForMatching(title: String): String {
    var text = title.trim()
    while (true) {
        val withoutBrackets = text.replace(TRAILING_BRACKETS, "")
        if (withoutBrackets != text) {
            text = withoutBrackets
            continue
        }
        val parens = TRAILING_PARENS.find(text)
        val inner = parens?.groupValues?.get(1)?.let { collapse(it.lowercase()) }
        if (parens != null && inner in NOISE_WORDS) {
            text = text.removeRange(parens.range)
            continue
        }
        break
    }
    return collapse(text.lowercase())
}

private val WHITESPACE = Regex("""\s+""")

private fun collapse(text: String): String {
    return text.map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .replace(WHITESPACE, " ")
}

/**
 * How alike two titles are, from 0 (nothing in common) to 1 (the same title once normalised):
 *
 * - 1.0: equal after normalising (also ignoring spaces, so "Re:Zero" meets "Rezero")
 * - 0.9 / 0.8: one starts with / contains the other at a word boundary, the shorter being at least
 *   six characters
 * - up to 0.7: the word sets overlap by a Jaccard index of at least 0.6, ignoring filler words
 *
 * A score of [STRONG_TITLE_MATCH_SCORE] or more is a strong match; below it only the token overlap
 * remains, and a lone shared word like "System" never reaches the threshold.
 */
fun titleMatchScore(a: String, b: String): Double {
    val left = normalizeTitleForMatching(a)
    val right = normalizeTitleForMatching(b)
    if (left.isEmpty() || right.isEmpty()) return 0.0
    if (left == right || left.replace(" ", "") == right.replace(" ", "")) return SCORE_EXACT

    val (short, long) = if (left.length <= right.length) left to right else right to left
    if (short.length >= MIN_CONTAINED_LENGTH) {
        if (long.startsWith("$short ")) return SCORE_STARTS_WITH
        if (" $long ".contains(" $short ")) return SCORE_CONTAINS
    }

    val leftTokens = left.split(' ').filter { it !in STOP_WORDS }.toSet()
    val rightTokens = right.split(' ').filter { it !in STOP_WORDS }.toSet()
    val union = (leftTokens + rightTokens).size
    if (union == 0) return 0.0
    val jaccard = leftTokens.intersect(rightTokens).size.toDouble() / union
    return if (jaccard >= MIN_JACCARD) jaccard * TOKEN_OVERLAP_WEIGHT else 0.0
}

class TitleMatch<T>(val item: T, val score: Double) {
    val isStrong: Boolean
        get() = score >= STRONG_TITLE_MATCH_SCORE
}

/** Every candidate whose title matches [title] at or above [MIN_SUGGESTION_SCORE], best first. */
fun <T> findTitleMatches(title: String, candidates: List<T>, titleOf: (T) -> String): List<TitleMatch<T>> {
    return candidates
        .mapNotNull { candidate ->
            val score = titleMatchScore(title, titleOf(candidate))
            if (score >= MIN_SUGGESTION_SCORE) TitleMatch(candidate, score) else null
        }
        .sortedByDescending { it.score }
}

/**
 * Orders matches for display: a small bonus puts an entry of the other content type (a comic's
 * novel, a novel's comic) ahead of an equally good one of the same type, without letting a weaker
 * tier overtake a stronger one.
 */
fun rankingScore(score: Double, isOppositeType: Boolean): Double {
    return score + if (isOppositeType) OPPOSITE_TYPE_BONUS else 0.0
}
