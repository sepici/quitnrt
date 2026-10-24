package app.thelasthit.nrt.domain.reminders

import app.thelasthit.nrt.domain.programme.WeekResult
import app.thelasthit.nrt.domain.programme.dueFor
import app.thelasthit.nrt.domain.programme.planExample
import app.thelasthit.nrt.domain.programme.play
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ResultNoticesTest {

    private val plan = planExample()

    @Test
    fun `a met week comes with its hit`() {
        val played = play(plan, "m").programme
        val due = dueFor(plan.week(1)).now
        val notices = ResultNotices.since(played, notifiedUpTo = null, now = due)
        assertEquals(1, notices.size)
        val notice = notices.single()
        assertEquals(1, notice.week.number)
        assertTrue(notice.met)
        assertEquals(1, notice.hits)
        assertEquals(emptyList<Int>(), notice.paidBack)
    }

    @Test
    fun `a result once notified isn't notified again`() {
        val played = play(plan, "m").programme
        val due = dueFor(plan.week(1)).now
        val notices = ResultNotices.since(played, null, due)
        val upTo = ResultNotices.notifiedUpTo(notices, before = null)
        assertEquals(due, upTo)
        assertEquals(emptyList<ResultNotice>(), ResultNotices.since(played, upTo, due.plusSeconds(60)))
    }

    @Test
    fun `a missed week, then the week that pays it back`() {
        val missed = play(plan, "x").programme
        val firstDue = dueFor(plan.week(1)).now
        val first = ResultNotices.since(missed, null, firstDue).single()
        assertFalse(first.met)
        assertEquals(WeekResult.MISSED, first.week.result)
        assertEquals(0, first.hits)

        val caughtUp = play(plan, "xm").programme
        val secondDue = dueFor(caughtUp.week(2)).now
        val second = ResultNotices.since(caughtUp, firstDue, secondDue).single()
        assertEquals(2, second.week.number)
        assertEquals(2, second.hits)
        assertEquals(listOf(1), second.paidBack)
    }

    @Test
    fun `a week missed and paid back since shows only as the catch-up`() {
        val caughtUp = play(plan, "xm").programme
        val notices = ResultNotices.since(caughtUp, null, dueFor(caughtUp.week(2)).now)
        assertEquals(listOf(2), notices.map { it.week.number })
    }

    @Test
    fun `several results at once come oldest first`() {
        val played = play(plan, "mm").programme
        val notices = ResultNotices.since(played, null, dueFor(played.week(2)).now)
        assertEquals(listOf(1, 2), notices.map { it.week.number })
    }

    @Test
    fun `results more than 10 days old aren't news`() {
        val played = play(plan, "m").programme
        val due = dueFor(plan.week(1)).now
        assertEquals(1, ResultNotices.since(played, null, due.plus(Duration.ofDays(9))).size)
        assertEquals(emptyList<ResultNotice>(), ResultNotices.since(played, null, due.plus(Duration.ofDays(11))))
    }

    @Test
    fun `nothing before any result is in`() {
        assertEquals(emptyList<ResultNotice>(), ResultNotices.since(plan, null, dueFor(plan.week(1)).now))
        assertEquals(null, ResultNotices.notifiedUpTo(emptyList(), before = null))
    }
}
