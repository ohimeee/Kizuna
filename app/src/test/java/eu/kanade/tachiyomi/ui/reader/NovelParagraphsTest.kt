package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelParagraphsTest {

    /** The splitter as it was before HTML mode existed - the oracle for "nothing changed". */
    private fun legacySplit(text: String): List<String> {
        val stripBlockTags = Regex("(?i)</?(p|h[1-6])[^>]*>")
        val split = text.split(Regex("(?i)</p>|</h[1-6]>|<br\\s*/?>\\s*<br\\s*/?>|\n{2,}"))
            .map { it.replace(stripBlockTags, "").trim() }
            .filter { it.isNotEmpty() }
        return split.ifEmpty { listOf(text.replace(stripBlockTags, "").trim()) }
    }

    private val paragraphs = listOf(
        "Chapter 1",
        "He opened the <i>door</i> slowly.",
        "“Who is there?” she asked.",
        "<b>Nobody</b> answered.",
    )

    @Test
    fun `compact one p per paragraph chapter splits as before`() {
        val html = paragraphs.joinToString("") { "<p>$it</p>" }
        splitNovelParagraphs(html) shouldBe paragraphs
        splitNovelParagraphs(html) shouldBe legacySplit(html)
    }

    @Test
    fun `jsoup pretty printed one p per paragraph chapter splits as before`() {
        val html = paragraphs.joinToString("\n") { "  <p>$it</p>" } + "\n"
        splitNovelParagraphs(html) shouldBe paragraphs
        splitNovelParagraphs(html) shouldBe legacySplit(html)
    }

    @Test
    fun `a wrapping div keeps the same paragraph count and order`() {
        // The div's open tag stays glued to the first paragraph (the renderer drops it), so that
        // one just loses its indent. The old splitter also kept the lone "</div>" after the last
        // paragraph as an empty trailing paragraph; that is dropped now, after every real one.
        val html = "<div class=\"chapter\">\n" +
            paragraphs.joinToString("\n") { "  <p>$it</p>" } + "\n</div>"
        val now = splitNovelParagraphs(html)
        val old = legacySplit(html)
        old.last() shouldBe "</div>"
        now.size shouldBe old.size - 1
        now.drop(1) shouldBe old.drop(1).dropLast(1)
        now[0] shouldBe "<div class=\"chapter\"> Chapter 1"
    }

    @Test
    fun `headings still end a paragraph`() {
        val html = "<h4>Chapter 2</h4><p>First sentence.</p><p>Second.</p>"
        splitNovelParagraphs(html) shouldBe listOf("Chapter 2", "First sentence.", "Second.")
        splitNovelParagraphs(html) shouldBe legacySplit(html)
    }

    @Test
    fun `double br is still a paragraph break`() {
        val html = "<p>One.<br><br>Two.<br>\n  <br>\n  Three.</p>"
        splitNovelParagraphs(html) shouldBe listOf("One.", "Two.", "Three.")
        splitNovelParagraphs(html) shouldBe legacySplit(html)
    }

    @Test
    fun `pretty printed one p with many br gives one clean paragraph per line`() {
        val html = "<p>Chapter 1\n  <br>\n  “What a mess.”\n  <br>\n  He sighed.\n  <br>\n  <i>Not again.</i></p>"
        splitNovelParagraphs(html) shouldBe listOf(
            "Chapter 1",
            "“What a mess.”",
            "He sighed.",
            "<i>Not again.</i>",
        )
    }

    @Test
    fun `an inline tag spanning br lines is closed and reopened per line`() {
        val html = "<p>a<br><i>one<br>two<br>three</i><br>four</p>"
        splitNovelParagraphs(html) shouldBe listOf("a", "<i>one</i>", "<i>two</i>", "<i>three</i>", "four")
    }

    @Test
    fun `one or two br stay as inline line breaks`() {
        splitNovelParagraphs("<p>Q. Why?\n  <br>\n  A. No.</p>") shouldBe listOf("Q. Why?<br>A. No.")
        splitNovelParagraphs("<p>a<br>b<br>c</p>") shouldBe listOf("a<br>b<br>c")
    }

    @Test
    fun `whitespace runs collapse and source newlines are not line breaks`() {
        splitNovelParagraphs("<p>  one \n\t two   three  </p>") shouldBe listOf("one two three")
        splitNovelParagraphs("<p>one\n  two</p>") shouldBe listOf("one two")
    }

    @Test
    fun `non breaking spaces inside text survive and blank paragraphs are dropped`() {
        splitNovelParagraphs("<p>a\u00A0\u00A0b</p>") shouldBe listOf("a\u00A0\u00A0b")
        splitNovelParagraphs("<p>a&nbsp;&nbsp;b</p>") shouldBe listOf("a&nbsp;&nbsp;b")
        splitNovelParagraphs("<p>One</p><p>&nbsp;</p><p>\u00A0 </p><p><br></p><p>Two</p>") shouldBe
            listOf("One", "Two")
    }

    @Test
    fun `empty html yields a single blank paragraph for the caller's empty check`() {
        splitNovelParagraphs("<p></p>").all { it.isBlank() } shouldBe true
    }

    @Test
    fun `plain text is split on blank lines only and keeps single newlines`() {
        val text = "Chapter 1\nline two\n\nSecond paragraph, with <i>italics</i> and a < b.\n\n\nThird."
        splitNovelParagraphs(text) shouldBe listOf(
            "Chapter 1\nline two",
            "Second paragraph, with <i>italics</i> and a < b.",
            "Third.",
        )
        splitNovelParagraphs(text) shouldBe legacySplit(text)
    }

    @Test
    fun `plain text with many newlines is not mistaken for line per paragraph html`() {
        val text = "a\nb\nc\nd\ne"
        splitNovelParagraphs(text) shouldBe listOf(text)
        splitNovelParagraphs(text) shouldBe legacySplit(text)
    }
}
