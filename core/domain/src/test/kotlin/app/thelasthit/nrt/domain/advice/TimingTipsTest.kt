package app.thelasthit.nrt.domain.advice

import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.domain.log.entry
import app.thelasthit.nrt.domain.product.ProductForm
import app.thelasthit.nrt.domain.time.DayBoundary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId

class TimingTipsTest {

    private val meal = setOf("after_meal")

    private fun tips(entries: List<Entry>, dayStart: LocalTime = DAY_START) = TimingTips.of(entries, TODAY, dayStart)

    private fun tip(entries: List<Entry>, tag: String = "after_meal", dayStart: LocalTime = DAY_START): TimingTip? =
        tips(entries, dayStart).singleOrNull { it.tag == tag }

    private fun time(text: String): LocalTime = LocalTime.parse(text)

    /** Untagged doses at 10:00 and 16:00 every day, so a meal dose is never the first of its day. */
    private val background = daily("10:00", 14) + daily("16:00", 14)

    @Test
    fun `after-meal doses around 1 pm and 7 pm move half an hour earlier, before lunch and dinner`() {
        val lunch = daily(
            "12:50", "13:00", "13:10", "13:05", "12:55", "13:00", "13:15", "13:00", "12:45", "13:20", "13:00", "13:05", "12:55", "13:00",
            tags = meal,
        )
        val tip = tip(lunch + daily("19:05", 10, meal) + background)!!

        assertEquals("dose_before_meal", tip.strategy)
        assertEquals(Shift.EARLIER, tip.shift)
        assertEquals(24, tip.doses)
        assertEquals(
            listOf(
                TimingSlot(usual = time("13:00"), suggested = time("12:30"), doses = 14, days = 14, meal = Meal.LUNCH),
                TimingSlot(usual = time("19:00"), suggested = time("18:30"), doses = 10, days = 10, meal = Meal.DINNER),
            ),
            tip.slots,
        )
        assertEquals(time("12:30"), tip.lead.suggested)
    }

    @Test
    fun `a tip moves doses the logs already have, one for each usual time, and never adds one`() {
        val tip = tip(daily("13:00", 14, meal) + daily("19:00", 14, meal) + daily("08:30", 14, meal) + background)!!

        // Breakfast is the day's first dose here, so only lunch and dinner move.
        assertEquals(listOf(time("12:30"), time("18:30")), tip.slots.map { it.suggested })
        for (slot in tip.slots) {
            assertTrue(slot.suggested < slot.usual, "$slot")
            assertTrue(slot.doses >= TimingTips.MIN_DAYS, "$slot")
        }
        assertTrue(tip.slots.sumOf { it.doses } <= tip.doses)
    }

    @Test
    fun `the usual time rounds to the half hour, with halfway going earlier`() {
        fun moved(vararg times: String) = tip(daily(*times, tags = meal) + daily("10:00", times.size))!!.lead.let { it.usual to it.suggested }
        assertEquals(time("13:00") to time("12:30"), moved("13:10", "13:10", "13:10", "13:10"))
        assertEquals(time("13:30") to time("13:00"), moved("13:20", "13:20", "13:20", "13:20"))
        assertEquals(time("13:00") to time("12:30"), moved("13:15", "13:15", "13:15", "13:15"))
        // The median, so one late lunch doesn't drag the time.
        val usual = tip(daily("12:55", "13:00", "13:05", "14:20", "13:00", tags = meal) + daily("10:00", 5))!!.lead
        assertEquals(time("13:00"), usual.usual)
    }

    @Test
    fun `after a meal needs a quarter of all doses`() {
        val few = daily("13:00", 8, meal) + background
        assertEquals(36, few.size)
        assertNull(tip(few))
        assertTrue(tip(daily("13:00", 10, meal) + background) != null)
    }

    @Test
    fun `a usual time needs doses on 4 different days`() {
        assertNull(tip(daily("13:00", 3, meal) + daily("10:00", 3)))
        val twoDays = (0..2).flatMap { listOf(entry("2026-10-21T13:0$it", tags = meal), entry("2026-10-22T13:0$it", tags = meal)) }
        assertNull(tip(twoDays + daily("10:00", 2)))
        assertTrue(tip(daily("13:00", 4, meal) + daily("10:00", 4)) != null)
    }

    @Test
    fun `doses spread through the day have no usual time`() {
        val spread = daily("08:00", "09:40", "11:20", "13:00", "14:40", "16:20", "18:00", "19:40", tags = meal) + daily("07:00", 8)
        assertNull(tip(spread))
    }

    @Test
    fun `a tip never brings the day's first dose earlier`() {
        assertNull(tip(daily("08:30", 14, meal) + daily("13:00", 14)))
        // Not even on one day in six.
        assertNull(tip(daily("13:00", 6, meal) + daily("10:00", 5)))
        // A breakfast dose that's the first on half the days.
        assertNull(tip(daily("08:30", 6, meal) + daily("07:00", 3)))
        // Nor when the new time would come before an earlier dose that day.
        assertNull(tip(daily("08:30", 6, meal) + daily("08:10", 6)))

        // A meal dose that was the day's first, but before the new time, is moved later: that's fine.
        val earlyLunch = daily("13:00", 13, meal) + daily("10:00", 13) + daily("12:10", 1, meal, skip = 13)
        assertEquals(time("12:30"), tip(earlyLunch)!!.lead.suggested)
    }

    @Test
    fun `one meal dose a day at a time that varies is one slot, not two`() {
        val spread = daily(
            "12:00", "12:10", "12:20", "12:30", "12:40", "12:50", "13:00", "13:10", "13:20", "13:30", "13:40", "13:50", "14:00", "14:10",
            tags = meal,
        )
        val tip = tip(spread + background)!!
        assertEquals(1, tip.slots.size, "${tip.slots}")
        assertEquals(Meal.LUNCH, tip.lead.meal)

        // Two clusters a meal apart on alternate days are still the one lunch.
        val twoTimes = daily("12:00", "13:40", "12:00", "13:40", "12:00", "13:40", "12:00", "13:40", "12:00", "13:40", "12:00", "13:40", "12:00", "13:40", tags = meal)
        val lunch = tip(twoTimes + background)!!
        assertEquals(listOf(Meal.LUNCH), lunch.slots.map { it.meal })
        assertEquals(time("11:30"), lunch.lead.suggested)
    }

    @Test
    fun `a usual time needs doses on at least half the days with doses`() {
        // After a meal on 4 of 12 days is a quarter of the doses, but not a daily habit.
        val someDays = daily("13:00", 4, meal) + daily("10:00", 12)
        assertEquals(16, someDays.size)
        assertNull(tip(someDays))
        assertEquals(time("12:30"), tip(daily("13:00", 6, meal) + daily("10:00", 12))!!.lead.suggested)
    }

    @Test
    fun `just woke up moves the first dose 15 minutes later`() {
        val mornings = daily("09:00", "09:05", "08:55", "09:00", "09:10", "08:50", "09:00", tags = setOf("woke_up"))
        val tip = tip(mornings + daily("13:00", 7) + daily("17:00", 7) + daily("21:00", 7), tag = "woke_up")!!

        assertEquals("morning_routine", tip.strategy)
        assertEquals(Shift.LATER, tip.shift)
        assertEquals(listOf(TimingSlot(time("09:00"), time("09:15"), doses = 7, days = 7, meal = null)), tip.slots)
        // It rounds to the quarter hour.
        assertEquals(time("09:30"), tip(daily("09:08", 5, setOf("woke_up")), tag = "woke_up")!!.lead.suggested)
        assertEquals(time("09:15"), tip(daily("09:07", 5, setOf("woke_up")), tag = "woke_up")!!.lead.suggested)
        assertNull(tip(daily("09:00", 3, setOf("woke_up")), tag = "woke_up"))
    }

    private val wokeUp = setOf("woke_up")

    @Test
    fun `the wake-up tip never brings the usual first dose earlier`() {
        // Woke up at 9 on the last 4 days, but the first dose came at 11 on the 10 before.
        assertNull(tip(daily("09:00", 4, wokeUp) + daily("11:00", 10, skip = 4), tag = "woke_up"))
        // The same 4 mornings spread through the fortnight.
        val mornings = setOf(1, 5, 9, 13)
        val spread = (1..14).map { day ->
            val woke = day in mornings
            entry("${TODAY.minusDays(day.toLong())}T${if (woke) "09:00" else "11:00"}", tags = if (woke) wokeUp else emptySet())
        }
        assertNull(tip(spread, tag = "woke_up"))
    }

    @Test
    fun `the wake-up tip only counts a dose that was the day's first`() {
        // An earlier dose comes first each day, so the 9 o'clock one isn't the first dose to push later.
        assertNull(tip(daily("09:00", 7, wokeUp) + daily("06:30", 7), tag = "woke_up"))
    }

    @Test
    fun `once followed for a week, the wake-up tip moves on another 15 minutes`() {
        val lastWeek = daily("09:15", 7, wokeUp)
        val weekBefore = daily("09:00", 7, wokeUp, skip = 7)
        val tip = tip(lastWeek + weekBefore, tag = "woke_up")!!
        assertEquals(listOf(TimingSlot(time("09:15"), time("09:30"), doses = 7, days = 7, meal = null)), tip.slots)
        assertEquals(14, tip.doses)
    }

    @Test
    fun `only after a meal and just woke up have timing tips`() {
        val regular = daily("15:00", 14, setOf("coffee_tea")) + daily("12:30", 14, setOf("work_break")) +
            daily("18:00", 14, setOf("commuting")) + daily("22:30", 14, setOf("before_bed"))
        assertTrue(tips(regular).isEmpty())
        assertEquals(setOf("after_meal", "woke_up"), TimingTips.tags)
    }

    @Test
    fun `times are read off the dose's own clock`() {
        val newYork = ZoneId.of("America/New_York")
        val tip = tip(daily("13:00", 5, meal, zone = newYork) + daily("10:00", 5, zone = newYork))!!
        assertEquals(time("12:30"), tip.lead.suggested)
    }

    @Test
    fun `a dose after midnight sits at the end of the evening before`() {
        // Late snacks at 00:40, counted towards the evening before, after that day's 10:00 dose.
        val snacks = (1..5).map { entry("${TODAY.minusDays(it.toLong())}T00:40", tags = meal) }
        val mornings = (2..6).map { entry("${TODAY.minusDays(it.toLong())}T10:00") }
        val tip = tip(snacks + mornings + daily("19:00", 5, meal, skip = 1))!!

        assertEquals(listOf(time("18:30"), time("00:00")), tip.slots.map { it.suggested })
        assertEquals(TimingSlot(time("00:30"), time("00:00"), doses = 5, days = 5, meal = null), tip.slots.last())
        // 00:40 is 20 hours 40 minutes after a 04:00 day start: the 42nd half hour.
        assertEquals(5, tip.halfHours[41])
    }

    @Test
    fun `the half-hour counts start at the day start`() {
        val sixAm = LocalTime.of(6, 0)
        val boundary = DayBoundary(sixAm)
        fun dayStartsAtSix(entries: List<Entry>) = entries.map { it.copy(logicalDate = boundary.logicalDate(it.occurredAt, it.zone)) }
        val tip = tip(dayStartsAtSix(daily("07:10", 5, meal) + daily("06:20", 5)), dayStart = sixAm)!!

        assertEquals(48, tip.halfHours.size)
        assertEquals(5, tip.halfHours[2])
        assertEquals(5, tip.halfHours.sum())
        assertEquals(time("06:30"), tip.lead.suggested)
        assertEquals(Meal.BREAKFAST, tip.lead.meal)
    }

    @Test
    fun `patches and doses outside the window are left out`() {
        val patches = daily("07:00", 14, form = ProductForm.PATCH_24H)
        assertEquals(time("12:30"), tip(daily("13:00", 4, meal) + daily("10:00", 4) + patches)!!.lead.suggested)

        val old = (15..30).map { entry("${TODAY.minusDays(it.toLong())}T13:00", tags = meal) }
        val today = entry("${TODAY}T13:00", tags = meal)
        assertNull(tip(old + today + daily("13:00", 3, meal) + daily("10:00", 3)))
    }

    @Test
    fun `meals by the clock`() {
        assertNull(Meal.at(time("04:30")))
        assertEquals(Meal.BREAKFAST, Meal.at(time("05:00")))
        assertEquals(Meal.LUNCH, Meal.at(time("11:00")))
        assertEquals(Meal.LUNCH, Meal.at(time("14:30")))
        assertNull(Meal.at(time("15:30")))
        assertEquals(Meal.DINNER, Meal.at(time("17:00")))
        assertNull(Meal.at(time("22:00")))
    }
}
