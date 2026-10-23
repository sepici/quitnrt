package app.thelasthit.nrt

import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.creature.Creature
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.random.Random

const val GUM_2 = "nicorette-gum-uk-2mg"
const val GUM_4 = "nicorette-gum-uk-4mg"
const val QUICKMIST = "nicorette-quickmist-uk-1mg"
const val PATCH_14 = "niquitin-clear-patch-uk-14mg"

/** Set up as if onboarding is done: tags, the mockups' four quick-log products and Gary. */
fun TestApp.setUp() {
    runBlocking {
        graph.tags.ensureBuiltIns()
        graph.settings.setCountry("GB")
        listOf(GUM_2, GUM_4, QUICKMIST, PATCH_14).forEach { graph.products.addFromCatalogue(it) }
        graph.settings.setDemon("Gary", Creature.DRAGON, "forest")
        graph.settings.completeOnboarding()
    }
}

/** The week in the Today mockup: 14, 13, 12 and 14 mg Monday to Thursday, and 8 mg so far today. */
fun TestApp.seedWeek() = runBlocking {
    fun log(product: String, at: String, quantity: Int = 1, vararg tags: String) {
        runBlocking { graph.entries.log(LogRequest(product, quantity, at = london(at), tagKeys = tags.toSet())) }
    }
    log(PATCH_14, "2026-10-19T07:30")
    log(GUM_4, "2026-10-20T08:00", 2)
    log(GUM_4, "2026-10-20T13:00")
    log(QUICKMIST, "2026-10-20T19:00")
    log(GUM_4, "2026-10-21T08:15", 3)
    log(PATCH_14, "2026-10-22T07:20")
    log(GUM_4, "2026-10-23T08:10", 1, "coffee_tea")
    log(GUM_2, "2026-10-23T10:55", 1, "work_break")
    log(GUM_2, "2026-10-23T13:40", 1, "after_meal")
}

/**
 * Plausible use before this week, for Insights: four to six doses a day of gum and mouth spray
 * at the usual times, a patch some mornings with fewer doses, and the odd day with nothing
 * logged. It runs from [days] − 1 days before today to last Sunday, and [seedWeek] fills in this
 * week. Seeded, so every run logs the same doses.
 */
fun TestApp.seedHistory(days: Int = 60) = runBlocking {
    val random = Random(23)
    val slots = listOf(8 to "woke_up", 11 to "work_break", 13 to "after_meal", 16 to "coffee_tea", 19 to "drink", 22 to "before_bed")
    var date = LocalDate.parse("2026-10-23").minusDays(days - 1L)
    while (date <= LocalDate.parse("2026-10-18")) {
        val day = date
        when {
            random.nextInt(25) == 0 -> Unit
            random.nextInt(6) == 0 -> {
                graph.entries.log(LogRequest(PATCH_14, at = london("${day}T07:30")))
                slots.shuffled(random).take(2).forEach { (hour, tag) -> logGum(random, day, hour, tag) }
            }
            else -> slots.filter { random.nextInt(10) < 8 }.forEach { (hour, tag) -> logGum(random, day, hour, tag) }
        }
        date = date.plusDays(1)
    }
}

private suspend fun TestApp.logGum(random: Random, date: LocalDate, hour: Int, tag: String) {
    val product = when (random.nextInt(6)) {
        0, 1 -> GUM_4
        2 -> QUICKMIST
        else -> GUM_2
    }
    val at = "%sT%02d:%02d".format(date, (hour + random.nextInt(-1, 2)).coerceIn(6, 23), random.nextInt(60))
    graph.entries.log(LogRequest(product, at = london(at), tagKeys = setOf(tag)))
}
