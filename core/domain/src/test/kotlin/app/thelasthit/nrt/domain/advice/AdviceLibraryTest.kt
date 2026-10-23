package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.ZoneOffset

class AdviceLibraryTest {

    private val library = AdviceLibrary.bundled()
    private val strategies = library.strategies

    private companion object {
        val NBSP = Char(0xA0)
    }

    @Test
    fun `the bundled library loads about 30 strategies with unique keys`() {
        assertTrue(strategies.size in 28..40, "${strategies.size} strategies")
        assertEquals(strategies.size, strategies.map { it.key }.toSet().size)
        assertEquals("four_ds", library.find("four_ds")?.key)
        assertNull(library.find("not_a_strategy"))
    }

    @Test
    fun `every tag key a strategy names exists`() {
        for (strategy in strategies) {
            strategy.tags.forEach { key -> assertTrue(BuiltInTags.find(key) != null, "${strategy.key} names \"$key\"") }
        }
    }

    @Test
    fun `every strategy has a source and at least one trigger or group`() {
        for (strategy in strategies) {
            assertTrue(strategy.sources.isNotEmpty(), "${strategy.key} has no source")
            assertTrue(strategy.tags.isNotEmpty() || strategy.groups.isNotEmpty(), "${strategy.key} suits nothing")
            assertTrue(strategy.steps.isNotEmpty(), "${strategy.key} has no steps")
        }
    }

    @Test
    fun `every source names the publisher, the page, its address and when it was read`() {
        val latest = LocalDate.now(ZoneOffset.MAX)
        val sources = strategies.flatMap { it.sources } + library.quitlines.map { it.source }
        for (source in sources) {
            assertTrue(source.publisher.isNotBlank() && source.title.isNotBlank(), source.url)
            assertTrue(source.url.startsWith("https://"), source.url)
            assertTrue(source.read.year >= 2026 && !source.read.isAfter(latest), "${source.url} read ${source.read}")
        }
    }

    @Test
    fun `every built-in tag has at least one strategy that names it`() {
        val named = strategies.flatMap { it.tags }.toSet()
        val missing = BuiltInTags.all.map { it.key }.filterNot { it in named }
        assertTrue(missing.isEmpty(), "no strategy names $missing")
    }

    @Test
    fun `every group has strategies of its own beyond the any-time ones`() {
        for (group in TagGroup.entries) {
            val own = strategies.filterNot { it.suitsAnyTime }
                .filter { strategy -> group in strategy.groups || strategy.tags.any { BuiltInTags.find(it)?.group == group } }
            assertTrue(own.size >= 2, "$group has ${own.size}")
        }
    }

    @Test
    fun `the any-time strategies name every group, and the 4 Ds is one`() {
        val anyTime = strategies.filter { it.suitsAnyTime }.map { it.key }
        assertTrue("four_ds" in anyTime, "$anyTime")
        assertTrue("delay_timer" in anyTime, "$anyTime")
        assertFalse(library.find("dose_before_meal")!!.suitsAnyTime)
        assertTrue(anyTime.size < strategies.size / 3, "too many any-time strategies: $anyTime")
    }

    @Test
    fun `the breathing and delay timers are the two interactive strategies`() {
        val breathing = library.opening(Interactive.BREATHING_TIMER)
        val delay = library.opening(Interactive.DELAY_TIMER)
        assertEquals("breathing_timer", breathing.key)
        assertEquals("delay_timer", delay.key)
        assertEquals(2, strategies.count { it.interactive != null })

        val breathingText = breathing.steps.joinToString(" ")
        assertTrue("Box breathing: in for 4, hold for 4, out for 4, hold for 4" in breathingText, breathingText)
        assertTrue("4-7-8" in breathingText && "hold for 7" in breathingText && "for 8" in breathingText, breathingText)
        assertTrue("stressed" in breathing.tags)
        assertTrue("5 to 10${NBSP}minutes" in delay.steps.joinToString(" "), delay.steps.toString())
    }

    @Test
    fun `strategies point to a pharmacist, a GP or a quitline where they should`() {
        assertTrue(library.find("ask_about_pace")!!.body.contains("pharmacist or GP"))
        assertTrue(library.find("quit_coach")!!.steps.first().contains("quitline"))
        assertTrue(library.find("low_mood")!!.steps.any { "GP" in it })
        assertTrue(library.find("low_mood")!!.steps.any { "emergency number" in it })
    }

    @Test
    fun `every market the app covers has a quitline, and anywhere else has none`() {
        for (country in Markets.supported) {
            val lines = library.quitlinesFor(country.lowercase())
            assertTrue(lines.isNotEmpty(), "no quitline for $country")
            lines.forEach { assertTrue(it.phone != null || it.web != null, "${it.name} has no way to reach it") }
        }
        assertTrue(library.quitlinesFor(Markets.ELSEWHERE).isEmpty())
        val uk = library.quitlinesFor("GB").map { it.region }
        assertEquals(listOf("England", "Scotland", "Wales", "Northern Ireland"), uk)
        assertEquals("0300 123 1044", library.quitlinesFor("GB").first().phone)
        assertEquals("1-800-784-8669", library.quitlinesFor("US").single().phone)
    }

    @Test
    fun `a file with an unknown tag, a duplicate key or no source is refused`() {
        val source = """{"publisher": "NHS", "title": "Page", "url": "https://www.nhs.uk/", "read": "2026-10-23"}"""
        fun file(vararg strategies: String) = """{"version": 1, "strategies": [${strategies.joinToString()}], "quitlines": []}"""
        fun strategy(key: String, tags: String = "[\"bored\"]", groups: String = "[]", sources: String = "[$source]") =
            """{"key": "$key", "title": "Title", "body": "Body.", "steps": ["Step."], "tags": $tags, "groups": $groups, "sources": $sources}"""

        assertEquals(1, AdviceLibrary.parse(file(strategy("fine"))).strategies.size)
        assertEquals(setOf(TagGroup.BODY), AdviceLibrary.parse(file(strategy("grouped", tags = "[]", groups = "[\"BODY\"]"))).strategies.single().groups)
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file(strategy("odd", tags = "[\"not_a_tag\"]"))) }
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file(strategy("same"), strategy("same"))) }
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file(strategy("loose", tags = "[]"))) }
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file(strategy("unsourced", sources = "[]"))) }
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file(strategy("Not-Snake"))) }
    }
}
