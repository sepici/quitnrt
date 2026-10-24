package app.thelasthit.nrt.ui.advice

import androidx.navigation.NavController
import app.thelasthit.nrt.domain.advice.Interactive

/**
 * The ways into advice from Today's advice line, the tip after logging and Insights' triggers:
 * the full list, a strategy's page, a timer, and a minute of box breathing started at once.
 */
data class AdviceLinks(
    val all: () -> Unit,
    val strategy: (String) -> Unit,
    val timer: (Interactive) -> Unit,
    val breatheNow: () -> Unit,
) {
    companion object {
        /** Links that go nowhere, for a screen shown on its own, as in tests. */
        val None = AdviceLinks(all = {}, strategy = {}, timer = {}, breatheNow = {})
    }
}

/** The advice links for the app's navigation. */
fun NavController.adviceLinks(): AdviceLinks = AdviceLinks(
    all = { openAdvice() },
    strategy = { openStrategy(it) },
    timer = { openTimer(it) },
    breatheNow = { openBreathing(minutes = 1, start = true) },
)
