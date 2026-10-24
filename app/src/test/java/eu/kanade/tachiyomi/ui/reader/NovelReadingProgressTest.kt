package eu.kanade.tachiyomi.ui.reader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelReadingProgressTest {

    @Test
    fun `single paragraph chapter is complete`() {
        novelProgressPercent(index = 0, total = 1, isAtEnd = false) shouldBe 100
        novelProgressPercent(index = 0, total = 0, isAtEnd = false) shouldBe 100
    }

    @Test
    fun `reaching the end reports 100 whatever the index`() {
        novelProgressPercent(index = 3, total = 10, isAtEnd = true) shouldBe 100
    }

    @Test
    fun `percent is truncated not rounded`() {
        novelProgressPercent(index = 0, total = 10, isAtEnd = false) shouldBe 0
        // 1 / 2 paragraphs-after-first = 50
        novelProgressPercent(index = 1, total = 3, isAtEnd = false) shouldBe 50
        // 2 / 6 = 33.33
        novelProgressPercent(index = 2, total = 7, isAtEnd = false) shouldBe 33
        // 5 / 6 = 83.33
        novelProgressPercent(index = 5, total = 7, isAtEnd = false) shouldBe 83
        // 199 / 200 = 99.5 stays 99
        novelProgressPercent(index = 199, total = 201, isAtEnd = false) shouldBe 99
    }

    @Test
    fun `out of range index is clamped`() {
        novelProgressPercent(index = -2, total = 10, isAtEnd = false) shouldBe 0
        novelProgressPercent(index = 50, total = 10, isAtEnd = false) shouldBe 100
    }
}
