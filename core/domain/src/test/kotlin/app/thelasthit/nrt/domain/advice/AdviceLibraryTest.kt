package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.product.ProductForm
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
    fun `after a slip doesn't assume a slip on an ordinary dose`() {
        val slip = library.find("after_a_slip")!!
        assertFalse(slip.suitsAnyTime)
        assertTrue(slip.suits("low", TagGroup.FEELINGS))
        for (key in listOf("happy", "bored", "after_meal", "coffee_tea", "strong_craving")) {
            assertFalse(slip.suits(key, BuiltInTags.find(key)!!.group), "after_a_slip suits $key")
        }
        assertFalse(slip.suits("my_own_tag", TagGroup.ROUTINE))
    }

    /** PLAN §9, "What goes with what": each trigger and the strategies that must suit it. */
    private val planTable: Map<String, List<String>> = buildMap {
        val calming = listOf("breathing_timer", "short_walk", "muscle_relaxation", "ride_the_wave")
        listOf("stressed", "anxious", "angry", "after_argument").forEach { put(it, calming) }
        // Brush your teeth is a step of leave_the_table, and the coffee rule for gum and lozenges is gum_and_drinks.
        put("after_meal", listOf("dose_before_meal", "leave_the_table", "short_walk", "gum_and_drinks"))
        put("coffee_tea", listOf("leave_the_table", "short_walk", "gum_and_drinks", "swap_drink"))
        listOf("happy", "excited", "celebrating").forEach { put(it, listOf("another_treat", "milestone_treats")) }
        listOf("bored", "screen_time", "autopilot").forEach { put(it, listOf("busy_hands", "change_rooms", "dose_decision")) }
        listOf("drink", "socialising").forEach { put(it, listOf("drinks_limit")) }
        put("woke_up", listOf("morning_routine"))
        listOf("withdrawal", "strong_craving").forEach { put(it, listOf("four_ds", "ask_about_pace")) }
    }

    @Test
    fun `each trigger in PLAN's table is suited by the strategies the table gives it`() {
        val missing = planTable.flatMap { (key, expected) ->
            val group = BuiltInTags.find(key)!!.group
            expected.filterNot { library.find(it)!!.suits(key, group) }.map { "$key: $it" }
        }
        assertTrue(missing.isEmpty(), "not suited: $missing")
    }

    @Test
    fun `the 4 Ds suits any tag, built-in or added, in any group`() {
        val fourDs = library.find("four_ds")!!
        BuiltInTags.all.forEach { assertTrue(fourDs.suits(it.key, it.group), it.key) }
        TagGroup.entries.forEach { assertTrue(fourDs.suits("my_own_tag", it), "$it") }
    }

    @Test
    fun `stress advice stays off the good moments, and pace advice off hunger and tiredness`() {
        val breathing = library.find("breathing_timer")!!
        for (key in listOf("happy", "excited", "celebrating", "relieved")) {
            assertFalse(breathing.suits(key, TagGroup.FEELINGS), "breathing_timer suits $key")
        }
        val pace = library.find("ask_about_pace")!!
        for (key in listOf("hungry", "tired", "cant_focus")) {
            assertFalse(pace.suits(key, TagGroup.BODY), "ask_about_pace suits $key")
        }
        assertTrue(pace.suits("withdrawal", TagGroup.BODY) && pace.suits("strong_craving", TagGroup.BODY))
    }

    @Test
    fun `gum and lozenge advice applies only to gum and lozenges`() {
        val gum = library.find("gum_and_drinks")!!
        assertEquals(setOf(ProductForm.GUM, ProductForm.LOZENGE, ProductForm.MINI_LOZENGE), gum.forms)
        assertTrue(gum.appliesTo(ProductForm.MINI_LOZENGE))
        assertFalse(gum.appliesTo(ProductForm.MOUTH_SPRAY))
        assertFalse(gum.appliesTo(ProductForm.POUCH))
        val fourDs = library.find("four_ds")!!
        assertTrue(fourDs.forms.isEmpty())
        ProductForm.entries.forEach { assertTrue(fourDs.appliesTo(it), "$it") }
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
        library.quitlines.mapNotNull { it.web }.forEach { assertTrue(it.startsWith("https://"), it) }
        assertEquals(
            "https://www.stopsmokingni.info/ways-quit/local-help-and-support",
            library.quitlinesFor("GB").single { it.region == "Northern Ireland" }.web,
        )
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

    @Test
    fun `a file with an unknown or repeated form is refused`() {
        val source = """{"publisher": "NHS", "title": "Page", "url": "https://www.nhs.uk/", "read": "2026-10-23"}"""
        fun file(forms: String) = """{"version": 1, "strategies": [{"key": "formed", "title": "Title", "body": "Body.",
            "steps": ["Step."], "tags": ["bored"], "groups": [], "forms": $forms, "sources": [$source]}], "quitlines": []}"""

        assertEquals(setOf(ProductForm.GUM), AdviceLibrary.parse(file("[\"GUM\"]")).strategies.single().forms)
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file("[\"CHEWING_GUM\"]")) }
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file("[\"GUM\", \"GUM\"]")) }
    }

    @Test
    fun `a quitline site that isn't a full https address is refused`() {
        val source = """{"publisher": "NHS", "title": "Page", "url": "https://www.nhs.uk/", "read": "2026-10-23"}"""
        fun file(web: String) = """{"version": 1, "strategies": [], "quitlines": [{"country": "GB", "region": null,
            "name": "Line", "phone": null, "web": "$web", "source": $source}]}"""

        assertEquals("https://example.org/help", AdviceLibrary.parse(file("https://example.org/help")).quitlines.single().web)
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file("example.org")) }
        assertThrows<IllegalArgumentException> { AdviceLibrary.parse(file("http://example.org")) }
    }
}
