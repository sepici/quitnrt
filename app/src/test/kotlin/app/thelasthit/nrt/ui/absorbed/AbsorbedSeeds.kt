package app.thelasthit.nrt.ui.absorbed

import app.thelasthit.nrt.TestApp
import kotlinx.coroutines.runBlocking

/** Turns "Show estimated absorbed amounts" on, or off again. */
fun TestApp.showAbsorbed(on: Boolean = true) = runBlocking { graph.settings.setShowAbsorbed(on) }

/** A non-breaking space, as the strings put between a number and its unit. */
const val NB = " "
