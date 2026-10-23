package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.TagGroup
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TriggersTest {

    private val groups = mapOf("after_meal" to TagGroup.ROUTINE, "coffee_tea" to TagGroup.ROUTINE, "stressed" to TagGroup.FEELINGS)

    @Test
    fun `shares are of doses, and a dose with two tags counts for both`() {
        val triggers = Triggers.of(
            listOf(
                entry("2026-10-21T08:00", tags = setOf("coffee_tea", "stressed")),
                entry("2026-10-21T13:00", tags = setOf("after_meal")),
                entry("2026-10-21T19:00", tags = setOf("after_meal")),
                entry("2026-10-21T21:00"),
            ),
            previous = null,
            groupOf = groups::get,
        )
        assertEquals(4, triggers.doses)
        assertEquals(listOf("after_meal", "coffee_tea", "stressed"), triggers.tags.map { it.key })
        assertEquals(0.5, triggers.tags.first().share)
        assertEquals(1, triggers.untagged)
        assertEquals(0.25, triggers.untaggedShare)
        assertTrue(triggers.tags.all { it.change == null })
    }

    @Test
    fun `a dose counts once for its group, and tags with no known group stay out`() {
        val triggers = Triggers.of(
            listOf(
                entry("2026-10-21T08:00", tags = setOf("coffee_tea", "after_meal")),
                entry("2026-10-21T09:00", tags = setOf("my_own")),
            ),
            previous = null,
            groupOf = groups::get,
        )
        assertEquals(TagGroup.ROUTINE, triggers.groups.first().group)
        assertEquals(1, triggers.groups.first().doses)
        assertEquals(1, triggers.groups.sumOf { it.doses })
    }

    @Test
    fun `patches are left out`() {
        val triggers = Triggers.of(
            listOf(
                entry("2026-10-21T07:30", form = ProductForm.PATCH_24H, perUnit = Dose.mg(14), tags = setOf("just_woke")),
                entry("2026-10-21T08:00", tags = setOf("coffee_tea")),
            ),
            previous = null,
            groupOf = groups::get,
        )
        assertEquals(1, triggers.doses)
        assertEquals(listOf("coffee_tea"), triggers.tags.map { it.key })
    }

    @Test
    fun `tags split by part of the day`() {
        val triggers = Triggers.of(
            listOf(
                entry("2026-10-21T08:00", tags = setOf("coffee_tea")),
                entry("2026-10-21T13:00", tags = setOf("after_meal")),
                entry("2026-10-21T23:30", tags = setOf("stressed")),
            ),
            previous = null,
            groupOf = groups::get,
        )
        assertEquals(listOf("coffee_tea"), triggers.byTimeOfDay.getValue(TimeOfDay.MORNING).map { it.key })
        assertEquals(listOf("stressed"), triggers.byTimeOfDay.getValue(TimeOfDay.NIGHT).map { it.key })
        assertTrue(triggers.byTimeOfDay.getValue(TimeOfDay.EVENING).isEmpty())
    }

    @Test
    fun `the change is in points against the previous period`() {
        val previous = listOf(
            entry("2026-10-14T08:00", tags = setOf("coffee_tea")),
            entry("2026-10-14T13:00", tags = setOf("after_meal")),
            entry("2026-10-14T19:00", tags = setOf("after_meal")),
            entry("2026-10-14T21:00", tags = setOf("after_meal")),
        )
        val now = listOf(
            entry("2026-10-21T08:00", tags = setOf("coffee_tea")),
            entry("2026-10-21T13:00", tags = setOf("after_meal")),
        )
        val tags = Triggers.of(now, previous, groups::get).tags.associateBy { it.key }
        // After a meal went from 75% of doses to 50%, coffee or tea from 25% to 50%.
        assertEquals(-25, tags.getValue("after_meal").change)
        assertEquals(25, tags.getValue("coffee_tea").change)
    }

    @Test
    fun `with nothing before to compare with, there's no change`() {
        val tags = Triggers.of(listOf(entry("2026-10-21T08:00", tags = setOf("coffee_tea"))), previous = emptyList(), groupOf = groups::get).tags
        assertNull(tags.single().change)
    }
}
