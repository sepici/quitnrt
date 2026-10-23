package app.thelasthit.nrt.domain.matcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WordsTest {

    @Test
    fun `splits numbers from letters and keeps decimals whole`() {
        assertEquals(listOf("sainsburys", "2", "mg", "gums"), Words.split("Sainsbury's 2mg gums."))
        assertEquals(listOf("0.5", "mg", "nasal", "spray"), Words.split("0,5mg nasal-spray"))
        assertEquals(listOf("0.5", "mg"), Words.split(".5mg"))
        assertEquals(listOf("gum", "x", "2"), Words.split("gum ×2"))
        assertEquals(listOf("24", "hour", "patch"), Words.split("24-hour patch"))
        assertEquals(emptyList<String>(), Words.split(" ... "))
    }

    @Test
    fun `plurals stem the same way on both sides`() {
        mapOf(
            "patches" to "patch",
            "pouches" to "pouch",
            "lozenges" to "lozenge",
            "minis" to "mini",
            "sessions" to "session",
            "sainsburys" to "sainsbury",
            "gum" to "gum",
            "cvs" to "cvs",
            "glass" to "glass",
        ).forEach { (word, stem) -> assertEquals(stem, Words.stem(word), word) }
    }

    @Test
    fun `plain text for names`() {
        assertEquals("after a meal", Words.plain(" After-a_meal "))
        assertEquals("cant focus", Words.plain("Can’t focus"))
        assertEquals("", Words.plain(",;"))
    }
}
