package tachiyomi.domain.entrylink.service

import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TitleMatchingTest {

    @Test
    fun `normalising lowercases, drops noise suffixes and punctuation`() {
        normalizeTitleForMatching("Solo Leveling (Novel)") shouldBe "solo leveling"
        normalizeTitleForMatching("Solo Leveling (Light Novel)") shouldBe "solo leveling"
        normalizeTitleForMatching("Solo Leveling (Web Novel) [Official]") shouldBe "solo leveling"
        normalizeTitleForMatching("Tower of God (Manhwa)") shouldBe "tower of god"
        normalizeTitleForMatching("  Re:Zero -  Starting   Life!  ") shouldBe "re zero starting life"
    }

    @Test
    fun `parentheses that are part of the title survive`() {
        normalizeTitleForMatching("Attack on Titan (Final Season)") shouldBe "attack on titan final season"
        normalizeTitleForMatching("Title (2020)") shouldBe "title 2020"
    }

    @Test
    fun `a title with a novel suffix matches its plain form exactly`() {
        titleMatchScore("Solo Leveling", "Solo Leveling (Novel)") shouldBe 1.0
        titleMatchScore("Solo Leveling (Manhwa)", "Solo Leveling (Light Novel)") shouldBe 1.0
    }

    @Test
    fun `titles differing only in case or punctuation match exactly`() {
        titleMatchScore("RE:ZERO", "re zero") shouldBe 1.0
        titleMatchScore("Omniscient Reader's Viewpoint", "omniscient reader s viewpoint!") shouldBe 1.0
        titleMatchScore("Re:Zero", "Rezero") shouldBe 1.0
    }

    @Test
    fun `starts with and contains are strong but below exact`() {
        val startsWith = titleMatchScore("Solo Leveling", "Solo Leveling Ragnarok")
        val contains = titleMatchScore("Solo Leveling", "The Solo Leveling Chronicle")
        startsWith shouldBeGreaterThanOrEqual STRONG_TITLE_MATCH_SCORE
        contains shouldBeGreaterThanOrEqual STRONG_TITLE_MATCH_SCORE
        (startsWith > contains) shouldBe true
        (startsWith < 1.0) shouldBe true
    }

    @Test
    fun `containment needs a word boundary and a long enough shorter title`() {
        // "leveling" sits inside "levelings" but not as a word
        titleMatchScore("Leveling", "Levelings Of Doom") shouldBeLessThan MIN_SUGGESTION_SCORE
        // Four characters is too short to count as contained
        titleMatchScore("Hero", "Hero Academia Story") shouldBeLessThan MIN_SUGGESTION_SCORE
    }

    @Test
    fun `token overlap is the weakest match and not strong`() {
        val score = titleMatchScore("The Dungeon Of Black Company", "Dungeon Black Company Story")
        score shouldBeGreaterThanOrEqual MIN_SUGGESTION_SCORE
        score shouldBeLessThan STRONG_TITLE_MATCH_SCORE
        // Filler words are ignored, so reordering a particle still agrees
        titleMatchScore("Tower God", "Tower of God") shouldBeGreaterThanOrEqual MIN_SUGGESTION_SCORE
    }

    @Test
    fun `titles sharing one common word do not match`() {
        titleMatchScore("The Beginning After The End", "The Legendary Moon Sculptor") shouldBe 0.0
        titleMatchScore("The System Administrator", "System Of A Down Fan Club") shouldBe 0.0
        titleMatchScore("My Dragon System", "Rise Of The System Hunter") shouldBe 0.0
        titleMatchScore("The", "The Beginning After The End") shouldBe 0.0
    }

    @Test
    fun `unrelated and empty titles score zero`() {
        titleMatchScore("Solo Leveling", "One Piece") shouldBe 0.0
        titleMatchScore("", "Solo Leveling") shouldBe 0.0
        titleMatchScore("(Novel)", "Solo Leveling") shouldBe 0.0
    }

    @Test
    fun `matches come back best first and weak ones are dropped`() {
        val library = listOf(
            "One Piece",
            "Solo Leveling Ragnarok",
            "Solo Leveling (Novel)",
            "Solo",
            "Solo Leveling Side Story Archive",
        )
        val matches = findTitleMatches("Solo Leveling", library) { it }
        matches.map { it.item } shouldBe listOf(
            "Solo Leveling (Novel)",
            "Solo Leveling Ragnarok",
            "Solo Leveling Side Story Archive",
        )
        matches.all { it.isStrong } shouldBe true
    }

    @Test
    fun `the other content type breaks a tie but never beats a stronger tier`() {
        val exactSame = rankingScore(1.0, isOppositeType = false)
        val containsOpposite = rankingScore(0.8, isOppositeType = true)
        (exactSame > containsOpposite) shouldBe true
        (rankingScore(0.9, isOppositeType = true) > rankingScore(0.9, isOppositeType = false)) shouldBe true
        (rankingScore(0.8, isOppositeType = true) < rankingScore(0.9, isOppositeType = false)) shouldBe true
    }
}
