package app.thelasthit.nrt.ui.absorbed

import app.thelasthit.nrt.GUM_2
import app.thelasthit.nrt.QUICKMIST
import app.thelasthit.nrt.TestApp
import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.seedWeek
import kotlinx.coroutines.runBlocking

/** Turns "Show estimated absorbed amounts" on, or off again. */
fun TestApp.showAbsorbed(on: Boolean = true) = runBlocking { graph.settings.setShowAbsorbed(on) }

/**
 * [seedWeek] with what History's own look adds: a dose after midnight that counts toward the
 * evening before, and a dose of more than one, which make the longest name and time lines.
 */
fun TestApp.seedWeekForHistory() {
    seedWeek()
    runBlocking {
        graph.entries.log(LogRequest(GUM_2, at = london("2026-10-23T01:20"), tagKeys = setOf("before_bed")))
        graph.entries.log(LogRequest(QUICKMIST, quantity = 2, at = london("2026-10-22T21:05"), tagKeys = setOf("drink")))
    }
}

/** A non-breaking space, as the strings put between a number and its unit. */
const val NB = " "
