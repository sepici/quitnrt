package app.thelasthit.nrt.ui.advice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.thelasthit.nrt.domain.advice.BreathingPattern
import app.thelasthit.nrt.domain.advice.Interactive

/** The advice pages. The tab bar hides on all of them, as on Settings. */
object AdviceRoutes {
    const val LIST = "advice"
    const val STRATEGY = "advice/strategy/{key}"
    const val BREATHING = "advice/breathing?pattern={pattern}&minutes={minutes}&start={start}"
    const val DELAY = "advice/delay"

    const val KEY = "key"
    const val PATTERN = "pattern"
    const val MINUTES = "minutes"
    const val START = "start"

    fun strategy(key: String) = "advice/strategy/$key"

    fun breathing(pattern: BreathingPattern, minutes: Int, start: Boolean) =
        "advice/breathing?pattern=${pattern.name}&minutes=$minutes&start=$start"

    /** Whether [route] is an advice page, which hides the tab bar. */
    fun isAdvice(route: String?): Boolean = route == LIST || route?.startsWith("$LIST/") == true
}

// The ways in, for Today's card, the tip after logging and Insights.

/** Opens the full advice list. */
fun NavController.openAdvice() = navigate(AdviceRoutes.LIST) { launchSingleTop = true }

/** Opens a strategy's page by its key in the library. */
fun NavController.openStrategy(key: String) = navigate(AdviceRoutes.strategy(key))

/**
 * Opens the breathing timer set to [pattern] for [minutes] (1 to 3). With [start], the session
 * starts at once, as "Breathe for 1 minute" on the tip after logging does.
 */
fun NavController.openBreathing(minutes: Int = 1, pattern: BreathingPattern = BreathingPattern.BOX, start: Boolean = false) =
    navigate(AdviceRoutes.breathing(pattern, minutes, start))

/** Opens the delay timer. */
fun NavController.openDelay() = navigate(AdviceRoutes.DELAY) { launchSingleTop = true }

/** Opens the timer an interactive strategy has. */
fun NavController.openTimer(interactive: Interactive) = when (interactive) {
    Interactive.BREATHING_TIMER -> openBreathing()
    Interactive.DELAY_TIMER -> openDelay()
}

/** The advice pages in the app's navigation. */
fun NavGraphBuilder.advice(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }
    val allAdvice: () -> Unit = {
        if (!navController.popBackStack(AdviceRoutes.LIST, inclusive = false)) navController.openAdvice()
    }
    page(AdviceRoutes.LIST) {
        AdviceScreen(
            onBack = back,
            onStrategy = { navController.openStrategy(it) },
            onTimer = { navController.openTimer(it) },
        )
    }
    page(AdviceRoutes.STRATEGY, arguments = listOf(navArgument(AdviceRoutes.KEY) { type = NavType.StringType })) { entry ->
        val key = entry.arguments?.getString(AdviceRoutes.KEY).orEmpty()
        StrategyScreen(
            strategyKey = key,
            onBack = back,
            onAllAdvice = allAdvice,
            onStrategy = { navController.openStrategy(it) },
            onTimer = { navController.openTimer(it) },
        )
    }
    page(
        AdviceRoutes.BREATHING,
        arguments = listOf(
            navArgument(AdviceRoutes.PATTERN) { type = NavType.StringType; defaultValue = BreathingPattern.BOX.name },
            navArgument(AdviceRoutes.MINUTES) { type = NavType.IntType; defaultValue = 1 },
            navArgument(AdviceRoutes.START) { type = NavType.BoolType; defaultValue = false },
        ),
    ) {
        BreathingScreen(onBack = back)
    }
    page(AdviceRoutes.DELAY) {
        DelayScreen(onBack = back, onBreathe = { navController.openBreathing() })
    }
}

/** A page that starts below the status bar, as the shell's own pages do. */
private fun NavGraphBuilder.page(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        Box(Modifier.fillMaxSize().statusBarsPadding()) { content(entry) }
    }
}
