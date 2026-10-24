package app.thelasthit.nrt.domain.agent

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.log.BuiltInTags
import app.thelasthit.nrt.domain.matcher.ProductMatcher
import app.thelasthit.nrt.domain.matcher.TagMatcher
import app.thelasthit.nrt.domain.matcher.TagName
import app.thelasthit.nrt.domain.product.DoseUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AgentLogReaderTest {

    private val catalogue = Catalogue.bundled()
    private fun item(id: String, quickLog: Int? = null) = requireNotNull(catalogue.find(id)) { "missing $id" }.toUserProduct(quickLog)

    private val gum2 = item("nicorette-gum-uk-2mg", 0)
    private val gum4 = item("nicorette-gum-uk-4mg", 1)
    private val mist = item("nicorette-quickmist-uk-1mg", 2)
    private val inhalator = item("nicorette-inhalator-uk-15mg")
    private val patch = item("niquitin-clear-patch-uk-21mg")
    private val hiddenPatch = item("niquitin-clear-patch-uk-14mg").copy(isHidden = true)

    private val reader = AgentLogReader(
        ProductMatcher(listOf(gum2, gum4, mist, inhalator, patch, hiddenPatch), catalogue),
        TagMatcher(
            BuiltInTags.all.map { TagName(it.key) } +
                TagName("stressed", listOf("Stressed")) +
                TagName("after_meal", listOf("After a meal")) +
                TagName("custom_1", listOf("Gym")) +
                TagName("custom_2", listOf("Gym")),
        ),
    )

    private val london = ZoneId.of("Europe/London")

    /** Friday 23 October 2026, 15:05 in London, as in the app's tests. */
    private val now = Instant.parse("2026-10-23T14:05:00Z")

    private fun read(ask: AgentLogAsk) = reader.read(ask, now, london)

    private fun ready(ask: AgentLogAsk): AgentLogRequest =
        (read(ask) as? AgentLogReading.Ready)?.request ?: fail("$ask wasn't ready: ${read(ask)}")

    private fun refused(ask: AgentLogAsk): AgentRefusal =
        (read(ask) as? AgentLogReading.Refused)?.reason ?: fail("$ask wasn't refused: ${read(ask)}")

    private fun ask(
        product: String,
        quantity: Int? = null,
        time: String? = null,
        tags: List<String> = emptyList(),
        strength: Int? = null,
    ) = AgentLogAsk(product, quantity, time?.let(LocalDateTime::parse), tags, strength)

    @Test
    fun `the requests from the plan`() {
        // "Log a 2 mg nicotine gum"
        assertEquals(AgentLogRequest(gum2, 1, at = null, tagKeys = emptySet(), cravingStrength = null), ready(ask("a 2 mg nicotine gum")))
        // "Log an inhalator session at 3 pm, stressed"
        assertEquals(
            AgentLogRequest(
                inhalator,
                quantity = 1,
                at = Instant.parse("2026-10-23T14:00:00Z"),
                tagKeys = setOf("stressed"),
                cravingStrength = null,
            ),
            ready(ask("inhalator session", time = "2026-10-23T15:00", tags = listOf("stressed"))),
        )
    }

    @Test
    fun `the matcher's phrases work, with the number in the description`() {
        assertEquals(gum4 to 2, ready(ask("two 4 mg gums")).let { it.product to it.quantity })
        assertEquals(gum4 to 1, ready(ask("nicorette 4")).let { it.product to it.quantity })
        assertEquals(patch to 1, ready(ask("21mg patch")).let { it.product to it.quantity })
        assertEquals(mist to 2, ready(ask("two sprays of QuickMist")).let { it.product to it.quantity })
    }

    @Test
    fun `a product ID names exactly that product`() {
        assertEquals(gum4, ready(ask("nicorette-gum-uk-4mg")).product)
    }

    @Test
    fun `the quantity counts what the description names`() {
        assertEquals(3, ready(ask("2 mg gum", quantity = 3)).quantity)
        // A cartridge is 8 sessions on an inhalator logged by session.
        assertEquals(16, ready(ask("inhalator cartridge", quantity = 2)).quantity)
        // The same number twice is fine.
        assertEquals(2, ready(ask("two 4 mg gums", quantity = 2)).quantity)
    }

    @Test
    fun `a quantity out of range, or one that disagrees with the description, is refused`() {
        assertEquals(AgentRefusal.BadQuantity(0), refused(ask("2 mg gum", quantity = 0)))
        assertEquals(AgentRefusal.BadQuantity(100), refused(ask("2 mg gum", quantity = 100)))
        assertEquals(AgentRefusal.ConflictingQuantity(inProduct = 2, quantity = 3), refused(ask("two 4 mg gums", quantity = 3)))
        assertEquals(AgentRefusal.TooMany(104, DoseUnit.SESSION), refused(ask("inhalator cartridges", quantity = 13)))
    }

    @Test
    fun `a time on the phone's clock, with a few minutes' leeway`() {
        assertEquals(Instant.parse("2026-10-22T19:30:00Z"), ready(ask("2 mg gum", time = "2026-10-22T20:30")).at)
        assertEquals(Instant.parse("2026-10-23T14:09:00Z"), ready(ask("2 mg gum", time = "2026-10-23T15:09")).at)
        // Tokyo's 23:05 is the same moment as London's 15:05.
        assertEquals(now, (reader.read(ask("2 mg gum", time = "2026-10-23T23:05"), now, ZoneId.of("Asia/Tokyo")) as AgentLogReading.Ready).request.at)
    }

    @Test
    fun `a future time is refused`() {
        assertEquals(
            AgentRefusal.TimeInFuture(LocalDateTime.parse("2026-10-23T15:15")),
            refused(ask("2 mg gum", time = "2026-10-23T15:15")),
        )
        assertEquals(
            AgentRefusal.TimeInFuture(LocalDateTime.parse("2026-10-24T09:00")),
            refused(ask("2 mg gum", time = "2026-10-24T09:00")),
        )
    }

    @Test
    fun `tags by key or name`() {
        assertEquals(
            setOf("stressed", "after_meal", "coffee_tea"),
            ready(ask("2 mg gum", tags = listOf("Stressed", "after a meal", "coffee-tea"))).tagKeys,
        )
        // Blanks are skipped.
        assertEquals(setOf("bored"), ready(ask("2 mg gum", tags = listOf(" ", "bored"))).tagKeys)
    }

    @Test
    fun `an unknown tag is refused, before a name several tags share`() {
        assertEquals(AgentRefusal.UnknownTags(listOf("Grumpy")), refused(ask("2 mg gum", tags = listOf("Grumpy", "Gym"))))
        assertEquals(
            AgentRefusal.AmbiguousTags(mapOf("Gym" to listOf("custom_1", "custom_2"))),
            refused(ask("2 mg gum", tags = listOf("stressed", "Gym"))),
        )
    }

    @Test
    fun `craving strength is 1 to 5`() {
        assertEquals(4, ready(ask("2 mg gum", strength = 4)).cravingStrength)
        assertEquals(AgentRefusal.BadCravingStrength(0), refused(ask("2 mg gum", strength = 0)))
        assertEquals(AgentRefusal.BadCravingStrength(6), refused(ask("2 mg gum", strength = 6)))
    }

    @Test
    fun `an unknown, blank or hidden product is refused`() {
        assertEquals(AgentRefusal.UnknownProduct("boots gum"), refused(ask("boots gum")))
        assertEquals(AgentRefusal.MissingProduct, refused(ask("  ")))
        assertEquals(AgentRefusal.HiddenProduct(hiddenPatch), refused(ask("niquitin-clear-patch-uk-14mg")))
    }

    @Test
    fun `an ambiguous product returns its candidates, most likely first`() {
        val reading = read(ask("nicorette gum", quantity = 2, tags = listOf("bored")))
        val ambiguous = reading as? AgentLogReading.Ambiguous ?: fail("not ambiguous: $reading")
        assertEquals("nicorette gum", ambiguous.text)
        assertEquals(listOf(gum2, gum4), ambiguous.choices.map { it.product })
        assertTrue(ambiguous.choices.all { it.quantity == 2 && it.tagKeys == setOf("bored") })
    }

    @Test
    fun `an ambiguous product is only a question once the rest is fine`() {
        assertEquals(AgentRefusal.TimeInFuture(LocalDateTime.parse("2026-10-24T09:00")), refused(ask("nicorette gum", time = "2026-10-24T09:00")))
        assertEquals(AgentRefusal.UnknownTags(listOf("Grumpy")), refused(ask("nicorette gum", tags = listOf("Grumpy"))))
    }

    @Test
    fun `undo reaches back a day by when an entry was written`() {
        assertTrue(AgentUndo.canUndo(now, now))
        assertTrue(AgentUndo.canUndo(now.minus(Duration.ofHours(24)), now))
        assertFalse(AgentUndo.canUndo(now.minus(Duration.ofHours(24)).minusMillis(1), now))
        assertEquals(now.minus(Duration.ofHours(24)), AgentUndo.since(now))
    }
}
