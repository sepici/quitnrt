package app.thelasthit.nrt.domain.agent

import app.thelasthit.nrt.domain.advice.AdviceLibrary
import app.thelasthit.nrt.domain.advice.AdviceMatcher
import app.thelasthit.nrt.domain.advice.AdviceWindow
import app.thelasthit.nrt.domain.advice.TODAY
import app.thelasthit.nrt.domain.advice.daily
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.matcher.TagMatcher
import app.thelasthit.nrt.domain.matcher.TagName
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class AgentAdviceTest {

    private val library = AdviceLibrary.bundled()
    private val advice = AgentAdvice(library)
    private val gum = setOf(ProductForm.GUM)

    private val tags = TagMatcher(
        BuiltInTags.all.map { TagName(it.key) } +
            TagName("after_meal", listOf("After a meal")) +
            TagName("custom_gaming", listOf("Gaming")) +
            TagName("custom_1", listOf("Gym")) +
            TagName("custom_2", listOf("Gym")),
    )

    private val groupOf: (String) -> TagGroup? = { key ->
        if (key == "custom_gaming") TagGroup.HABIT else BuiltInTags.find(key)?.group
    }

    private fun answer(
        tag: String?,
        entries: List<app.thelasthit.nrt.domain.log.Entry> = emptyList(),
        forms: Set<ProductForm> = gum,
        country: String? = null,
    ) =
        when (val result = advice.pick(tag, tags, groupOf, entries, TODAY, forms, country)) {
            is AgentAdviceResult.Answer -> result.answer
            is AgentAdviceResult.Refused -> fail("$tag was refused: ${result.reason}")
        }

    private fun refused(tag: String) = when (val result = advice.pick(tag, tags, groupOf, emptyList(), TODAY, gum)) {
        is AgentAdviceResult.Refused -> result.reason
        is AgentAdviceResult.Answer -> fail("$tag was answered: ${result.answer}")
    }

    @Test
    fun `a tag asked about gets its best strategy, by PLAN's table`() {
        assertEquals(AgentAdviceAnswer(library.find("breathing_timer")!!, AgentAdviceAnswer.Reason.AskedTag("stressed")), answer("stressed"))
        assertEquals("dose_before_meal", answer("After a meal").strategy.key)
        assertEquals("busy_hands", answer("bored").strategy.key)
        assertEquals("four_ds", answer("strong-craving").strategy.key)
    }

    @Test
    fun `it's the same as the first strategy Insights and the advice list give the tag`() {
        val matcher = AdviceMatcher(library)
        for (tag in BuiltInTags.all) {
            val expected = matcher.matches(tag.key, tag.group, gum).first().strategy
            assertEquals(expected, answer(tag.key).strategy, tag.key)
        }
    }

    @Test
    fun `a tag of the user's own gets its group's advice`() {
        val answer = answer("Gaming")
        assertEquals(AgentAdviceAnswer.Reason.AskedTag("custom_gaming"), answer.reason)
        assertTrue(answer.strategy.suits("custom_gaming", TagGroup.HABIT))
    }

    @Test
    fun `an unknown tag, or a name two tags share, is refused`() {
        assertEquals(AgentRefusal.UnknownTags(listOf("Grumpy")), refused("Grumpy"))
        assertEquals(AgentRefusal.AmbiguousTags(mapOf("Gym" to listOf("custom_1", "custom_2"))), refused("Gym"))
    }

    @Test
    fun `with no tag, it's for the top trigger of the last 14 days`() {
        val entries = daily("10:00", 6, tags = setOf("bored")) + daily("15:00", 5, tags = setOf("stressed"))
        val answer = answer(null, entries)
        val reason = answer.reason as? AgentAdviceAnswer.Reason.ForTopTrigger ?: fail("not for the top trigger: ${answer.reason}")
        assertEquals("bored", reason.trigger.key)
        assertEquals(6, reason.trigger.doses)
        assertEquals(AdviceWindow.of(TODAY), reason.window)
        assertEquals("busy_hands", answer.strategy.key)
        // A blank tag is no tag.
        assertEquals(answer, answer("  ", entries))
    }

    @Test
    fun `with too little logged, it's the 4 Ds`() {
        val few = daily("10:00", 4, tags = setOf("bored"))
        assertEquals(AgentAdviceAnswer(library.find("four_ds")!!, AgentAdviceAnswer.Reason.AnyTime), answer(null, few))
        assertEquals(AgentAdviceAnswer.Reason.AnyTime, answer(null).reason)
    }

    @Test
    fun `it comes with the quitlines for the user's country, as PLAN's limits say`() {
        val gb = answer("strong_craving", country = "GB").quitlines
        assertEquals(library.quitlinesFor("GB"), gb)
        assertEquals(listOf("England", "Scotland", "Wales", "Northern Ireland"), gb.map { it.region })
        assertEquals("Smokefree National Helpline", gb.first().name)
        assertEquals(library.quitlinesFor("IE"), answer(null, country = "ie").quitlines)
        // Before a country is picked, or where the library has none.
        assertEquals(emptyList<Any>(), answer("stressed").quitlines)
        assertEquals(emptyList<Any>(), answer("stressed", country = "FR").quitlines)
    }

    @Test
    fun `advice about one kind of product goes only to people who use it`() {
        val forms = mapOf(
            "gum" to setOf(ProductForm.GUM),
            "patch" to setOf(ProductForm.PATCH_24H),
            "spray" to setOf(ProductForm.MOUTH_SPRAY),
        )
        for ((name, using) in forms) {
            for (tag in BuiltInTags.all) {
                val strategy = answer(tag.key, forms = using).strategy
                assertTrue(strategy.forms.isEmpty() || strategy.forms.any { it in using }, "$name, ${tag.key}: ${strategy.key}")
            }
        }
    }

    @Test
    fun `it never gives the advice for after a slip, or anything that suggests taking more`() {
        val more = listOf(
            "more nicotine", "take more", "use more", "have more", "extra dose", "extra piece", "another dose",
            "another piece", "higher dose", "higher strength", "stronger dose", "stronger product", "increase your dose",
            "add a dose", "double up", "top up",
        ).map { Regex("\\b$it\\b", RegexOption.IGNORE_CASE) }
        val everyForm = ProductForm.entries.map { setOf(it) } + setOf(emptySet())
        val picks = everyForm.flatMap { forms ->
            BuiltInTags.all.map { answer(it.key, forms = forms).strategy } + answer(null, forms = forms).strategy
        }.distinct()
        for (strategy in picks) {
            assertNotEquals(AdviceMatcher.AFTER_A_SLIP, strategy.key)
            val text = listOf(strategy.title, strategy.body) + strategy.steps
            text.forEach { line -> assertTrue(more.none { it.containsMatchIn(line) }, "${strategy.key}: $line") }
        }
    }
}
