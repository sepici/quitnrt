package app.thelasthit.nrt.ui.advice

import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.advice.BreathingPattern
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDate

/**
 * The two weeks the advice mockups show, 9 to 22 October: 71 doses, 19 after a meal (27%),
 * 11 on waking (15%) and 7 with coffee (10%). After-meal doses come around 13:00 on 12 days
 * and 19:00 on 7, so the tip moves them to 12:30 and 18:30. The first dose comes around 09:00
 * on waking for the last 11 days, so the tip moves it to 09:15.
 */
fun TestApp.seedAdviceWeeks() = runBlocking {
    val first = LocalDate.parse("2026-10-09")
    fun log(day: Int, time: String, product: String = GUM_2, vararg tags: String) = runBlocking {
        graph.entries.log(LogRequest(product, at = london("${first.plusDays(day - 1L)}T$time"), tagKeys = tags.toSet()))
    }
    val minutes = listOf("00", "05", "55", "10", "50", "00", "15", "05", "00", "10", "55", "00", "05", "50")
    for (day in 1..14) {
        val wobble = minutes[day - 1]
        if (day >= 4) log(day, if (wobble.toInt() > 30) "08:$wobble" else "09:$wobble", GUM_4, "woke_up")
        if (day <= 3) {
            log(day, "10:00", QUICKMIST)
            log(day, "15:00")
        }
        if (day <= 7) log(day, "10:30", GUM_2, "coffee_tea")
        if (day <= 12) log(day, if (wobble.toInt() > 30) "12:$wobble" else "13:$wobble", GUM_2, "after_meal")
        if (day >= 8) log(day, "19:$wobble".replace(":5", ":0"), GUM_2, "after_meal")
        log(day, "16:00", QUICKMIST)
        log(day, "21:30")
    }
}

/**
 * Two weeks with an after-meal dose at 10:30 and another at 12:05 each day, so the timing tip
 * moves them to 10:00, before breakfast, and 11:30, before lunch: two times 90 minutes apart.
 * A first dose at 08:00 lets both move earlier.
 */
fun TestApp.seedCloseMeals() = runBlocking {
    val first = LocalDate.parse("2026-10-09")
    for (day in 0L..13L) {
        val date = first.plusDays(day)
        fun log(time: String, vararg tags: String) = runBlocking {
            graph.entries.log(LogRequest(GUM_2, at = london("${date}T$time"), tagKeys = tags.toSet()))
        }
        log("08:00", "woke_up")
        log("10:30", "after_meal")
        log("12:05", "after_meal")
        log("16:00")
    }
}

/** [count] breathing sessions that ran to the end, before today. */
fun TestApp.seedBreathing(count: Int) = runBlocking {
    repeat(count) { index ->
        graph.breathing.record(BreathingPattern.BOX, london("2026-10-${10 + index}T20:00"), Duration.ofSeconds(64), rounds = 4, ranToEnd = true)
    }
}

/** A buzzer that counts what it was asked to do. */
class FakeBuzzer : Buzzer {
    var ticks = 0
    var finishes = 0
    var alarms = 0

    override fun tick() {
        ticks++
    }

    override fun finish() {
        finishes++
    }

    override fun alarm() {
        alarms++
    }
}

/** A wake hold that remembers its timeout while it's held. */
class FakeWakeHold : WakeHold {
    /** How long the hold in place lasts at most, or null when nothing holds the processor on. */
    var heldFor: Duration? = null

    override fun hold(timeout: Duration) {
        heldFor = timeout
    }

    override fun release() {
        heldFor = null
    }
}
