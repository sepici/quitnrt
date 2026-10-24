package app.thelasthit.nrt.ui.lair

import android.provider.Settings
import app.thelasthit.nrt.GUM_4
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.programme.UsualDay
import app.thelasthit.nrt.setUp
import app.thelasthit.nrt.ui.game.GameSound
import app.thelasthit.nrt.ui.game.GameSounds
import app.thelasthit.nrt.ui.game.HitVoice
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** The first day of a 12-week plan built today, Friday 23 October: it starts tomorrow. */
val PLAN_START: LocalDate = LocalDate.of(2026, 10, 24)

/**
 * Gary in [week] of a 12-week plan down from 8 pieces of 4 mg gum, with every week before it met
 * (nothing logged meets a week), and [spent] of those hits spent. The clock is at 15:05 on the
 * week's third day, once the week before's result is in. Week 13 means the plan is over, with
 * all 12 met.
 */
fun TestApp.battleAt(week: Int, spent: Int) = runBlocking {
    setUp()
    val gum = checkNotNull(graph.products.get(GUM_4))
    graph.programmes.keepUsualDay(UsualDay.typed(gum to 8))
    graph.programmes.startPlan(checkNotNull(graph.programmes.buildPlan()))
    clock.now = london("${PLAN_START.plusWeeks(week - 1L).plusDays(2)}T15:05")
    graph.programmes.settle()
    repeat(spent) { graph.game.spendHit() }
}

/** The lair as lair.html has it: week 4 of 12, 100 of 120 HP, and 1 hit banked. */
fun TestApp.battleAsMockup() = battleAt(week = 4, spent = 2)

/** Week 10, roughed up at 80 of 120 HP, with 5 hits banked for the 5× attack. */
fun TestApp.battleWithCombo() = battleAt(week = 10, spent = 4)

/** Week 12, barely standing at 40 of 120 HP, with 3 banked. */
fun TestApp.battleBarely() = battleAt(week = 12, spent = 8)

/** Every week met and 11 hits spent: 10 HP left and the last hit banked. */
fun TestApp.battleLastHit() = battleAt(week = 13, spent = 11)

/**
 * Gary defeated: all 12 weeks met and every hit spent, the last one today. In the plan's first
 * week, gum was taken with coffee or tea in the mornings and after lunch, the triggers the
 * victory names (the usual day was typed, so they come from that week). Logged once the results
 * are in, so they change none.
 */
fun TestApp.defeatedWithTriggers() = runBlocking {
    battleLastHit()
    for (day in 25..28) {
        graph.entries.log(LogRequest(GUM_4, at = london("2026-10-${day}T08:00"), tagKeys = setOf("coffee_tea")))
        graph.entries.log(LogRequest(GUM_4, at = london("2026-10-${day}T13:00"), tagKeys = setOf("after_meal")))
    }
    graph.game.spendHit()
    graph.game.markDefeated()
}

/** Turns the phone's animations off ("remove animations"), or back to [scale]. */
fun TestApp.setAnimatorScale(scale: Float) {
    Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
}

/** Sounds the lair asked for, in order, instead of playing them. */
class RecordedSounds : GameSounds {
    val played = mutableListOf<GameSound>()

    override fun play(sound: GameSound, voice: HitVoice) {
        synchronized(played) { played += sound }
    }

    fun snapshot(): List<GameSound> = synchronized(played) { played.toList() }
}
