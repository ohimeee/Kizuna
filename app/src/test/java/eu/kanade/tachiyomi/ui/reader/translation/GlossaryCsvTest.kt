package eu.kanade.tachiyomi.ui.reader.translation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GlossaryCsvTest {

    @Test
    fun `parses opennovel's documented example`() {
        val csv = """
            type,raw_term,translated_term,gender
            character,花城,Hua Cheng,male
            character,师青玄,Shi Qingxuan,undecided
            term,修炼,cultivation
            title,师兄,Senior Brother
        """.trimIndent()

        GlossaryCsv.parse(csv) shouldBe listOf(
            GlossaryEntry(GlossaryType.CHARACTER, "花城", "Hua Cheng", GlossaryGender.MALE),
            GlossaryEntry(GlossaryType.CHARACTER, "师青玄", "Shi Qingxuan", GlossaryGender.UNDECIDED),
            GlossaryEntry(GlossaryType.TERM, "修炼", "cultivation"),
            GlossaryEntry(GlossaryType.TITLE, "师兄", "Senior Brother"),
        )
    }

    @Test
    fun `export then parse round trips, including commas and quotes`() {
        val entries = listOf(
            GlossaryEntry(GlossaryType.CHARACTER, "강소희", "Kang So-hee", GlossaryGender.FEMALE),
            GlossaryEntry(GlossaryType.TERM, "길드, 본부", "Guild \"HQ\""),
        )
        GlossaryCsv.parse(GlossaryCsv.export(entries)) shouldBe entries
    }

    @Test
    fun `tolerates a byte order mark, crlf line ends and blank lines`() {
        val csv = "\uFEFFtype,raw_term,translated_term,gender\r\n\r\ncharacter,제국,Jeguk,male\r\n"
        GlossaryCsv.parse(csv) shouldBe listOf(
            GlossaryEntry(GlossaryType.CHARACTER, "제국", "Jeguk", GlossaryGender.MALE),
        )
    }

    @Test
    fun `rows without a raw term are skipped and unknown types become terms`() {
        val csv = "character,,Nobody,male\nskill,火球,Fireball"
        GlossaryCsv.parse(csv) shouldBe listOf(GlossaryEntry(GlossaryType.TERM, "火球", "Fireball"))
    }
}
