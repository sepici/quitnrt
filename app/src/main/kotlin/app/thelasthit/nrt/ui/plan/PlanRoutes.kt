package app.thelasthit.nrt.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.thelasthit.nrt.ui.minuteTicks
import java.time.Clock

/** The Plan tab's pages under its main one. The tab bar hides on all of them. */
object PlanRoutes {
    const val MAIN = "plan"
    const val USUAL_DAY = "plan/usual-day/{mode}"
    const val BUILD = "plan/build"
    const val GOALS = "plan/goals"
    const val NEW_DEMON = "plan/new-demon"

    fun usualDay(mode: UsualDayMode) = "plan/usual-day/${mode.name}"

    /** Whether [route] is one of the pages under the tab, where the tab bar hides. */
    fun isPage(route: String?): Boolean = route?.startsWith("$MAIN/") == true
}

/** The pages under the Plan tab, each below the status bar as the shell's own pages are. */
fun NavGraphBuilder.planPages(navController: NavController, onOpenCatalogue: () -> Unit) {
    val back: () -> Unit = { navController.popBackStack() }
    val home: () -> Unit = { navController.popBackStack(PlanRoutes.MAIN, inclusive = false) }
    composable(PlanRoutes.USUAL_DAY) { entry ->
        val mode = entry.arguments?.getString("mode")?.let { name -> UsualDayMode.entries.firstOrNull { it.name == name } } ?: UsualDayMode.TYPED
        Page {
            UsualDayScreen(
                mode = mode,
                onBack = back,
                onKept = { navController.navigate(PlanRoutes.BUILD) },
                onOpenCatalogue = onOpenCatalogue,
            )
        }
    }
    composable(PlanRoutes.BUILD) { Page { BuildScreen(onBack = back, onStarted = home) } }
    composable(PlanRoutes.GOALS) { Page { GoalsScreen(onBack = back) } }
    composable(PlanRoutes.NEW_DEMON) { Page { NewDemonScreen(onBack = back, onStarted = home) } }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }
}

/**
 * Runs [settle] each time the app comes to the foreground, so a weekly result that fell due
 * while the app sat in the background arrives when it's looked at again (ROADMAP P3-3), rather
 * than waiting for the next dose to be logged.
 *
 * While the app stays in the foreground it runs again at the start of every minute by [clock],
 * as the screens' clocks tick ([minuteTicks]), so a result that falls due with the app open comes
 * in as the screens move on to the new day. Otherwise the Plan tab would go on saying late logs
 * still count for a week whose result was already due.
 */
@Composable
fun SettleOnStart(clock: Clock, settle: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(settle)
    LaunchedEffect(lifecycle, clock) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            minuteTicks(clock).collect { latest() }
        }
    }
}
