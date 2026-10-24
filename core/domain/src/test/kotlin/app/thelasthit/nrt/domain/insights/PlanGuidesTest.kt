package app.thelasthit.nrt.domain.insights

import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.programme.Ladder
import app.thelasthit.nrt.domain.programme.Programme
import app.thelasthit.nrt.domain.programme.ProgrammeLength
import app.thelasthit.nrt.domain.programme.UsualDay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PlanGuidesTest {

    private fun date(text: String) = LocalDate.parse(text)

    // PLAN's first example, 12 pieces of 2 mg gum a day, from Saturday 17 October: a 20 mg guide
    // that week and 18 mg from Saturday 24 October.
    private val programme: Programme = run {
        val catalogue = Catalogue.bundled()
        val gum = checkNotNull(catalogue.find("nicorette-gum-uk-2mg")).toUserProduct()
        val ladder = Ladder.build(UsualDay.typed(gum to 12), listOf(gum), catalogue, "GB")
        Programme.create(ladder, ProgrammeLength.WEEKS_12, date("2026-10-17"))
    }
    private val guides = PlanGuides.of(programme)

    private fun intake(from: String, to: String, vararg doses: Pair<String, Int>): DailyIntake {
        val window = InsightWindow(date(from), date(to))
        val entries = doses.map { (at, mg) -> entry(at, perUnit = Dose.mg(mg)) }
        return DailyIntake.of(window, date(to), date(from), entries)
    }

    @Test
    fun `the guide line has a gap before the plan`() {
        val days = intake("2026-10-15", "2026-10-25")
        assertEquals(
            listOf(null, null) + List(7) { Dose.mg(20) } + List(2) { Dose.mg(18) },
            guides.dailyLine(days),
        )
    }

    @Test
    fun `a week's column averages the guides of the days it averages`() {
        // Monday 12 to Sunday 18 October has days before the plan, so no guide. Monday 19 to
        // Sunday 25 October averages 5 days at 20 mg and Saturday at 18 mg, with Sunday today.
        val days = intake("2026-10-12", "2026-10-25")
        assertEquals(listOf(null, Dose(19_667)), guides.weeklyLine(days))
        // A Monday that's today alone takes today's guide.
        assertEquals(listOf(Dose.mg(18)), guides.weeklyLine(intake("2026-10-26", "2026-10-26")))
    }

    @Test
    fun `days within the guide count only finished days in the plan`() {
        val days = intake(
            "2026-10-15", "2026-10-19",
            "2026-10-15T09:00" to 30, // before the plan
            "2026-10-17T09:00" to 20, // at the guide
            "2026-10-18T09:00" to 21, // over it
            "2026-10-19T09:00" to 40, // today, not over yet
        )
        assertEquals(DaysWithin(within = 1, days = 2), guides.daysWithin(days))
        assertNull(guides.daysWithin(intake("2026-10-16", "2026-10-17")))
    }

    @Test
    fun `the calendar shades against the guide, the usual day before it, and nothing after`() {
        assertEquals(Dose.mg(24), guides.shadeAgainst(date("2026-10-16")))
        assertEquals(Dose.mg(20), guides.shadeAgainst(date("2026-10-17")))
        assertEquals(Dose.ZERO, guides.shadeAgainst(date("2027-02-01")))
        assertTrue(guides.hasDaysBefore(listOf(date("2026-10-16"), date("2026-10-17"))))
        assertFalse(guides.hasDaysBefore(listOf(date("2026-10-17"))))

        val tracked = CalendarHeat.Day(date("2026-10-17"), Dose.mg(20), CalendarHeat.State.TRACKED)
        assertEquals(3, guides.step(tracked))
        assertEquals(0, guides.step(tracked.copy(state = CalendarHeat.State.NO_DATA)))
    }

    @Test
    fun `steps are quarters of the guide, and every step from 4 is over it`() {
        val guide = Dose.mg(20)
        assertEquals(0, GuideShade.step(Dose.ZERO, guide))
        assertEquals(1, GuideShade.step(Dose.mg(1), guide))
        assertEquals(1, GuideShade.step(Dose.mg(10), guide))
        assertEquals(2, GuideShade.step(Dose(10_001), guide))
        assertEquals(2, GuideShade.step(Dose.mg(15), guide))
        assertEquals(3, GuideShade.step(Dose.mg(20), guide))
        assertEquals(4, GuideShade.step(Dose(20_001), guide))
        assertEquals(4, GuideShade.step(Dose.mg(25), guide))
        assertEquals(5, GuideShade.step(Dose(25_001), guide))
    }

    @Test
    fun `against an NRT-free guide, nothing is empty and anything is the top step`() {
        assertEquals(0, GuideShade.step(Dose.ZERO, Dose.ZERO))
        assertEquals(HeatScale.STEPS, GuideShade.step(Dose(1), Dose.ZERO))
    }
}
