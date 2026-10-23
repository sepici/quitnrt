package app.thelasthit.nrt.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.today.TodayScreen

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Today("today", R.string.nav_today, R.drawable.ic_nav_today),
    History("history", R.string.nav_history, R.drawable.ic_nav_history),
    Insights("insights", R.string.nav_insights, R.drawable.ic_nav_insights),
    Plan("plan", R.string.nav_plan, R.drawable.ic_nav_plan),
    Lair("lair", R.string.nav_lair, R.drawable.ic_nav_lair),
}

private const val SETTINGS_ROUTE = "settings"

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbars = remember { SnackbarHostState() }
    val colors = LastHit.colors

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = {
            SnackbarHost(snackbars) { data ->
                Snackbar(data, containerColor = colors.text, contentColor = colors.bg, actionColor = colors.bg)
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = colors.bg,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                },
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateTo(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = {
                            Text(
                                stringResource(destination.label),
                                style = LastHit.type.caption.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                ),
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.text,
                            selectedTextColor = colors.text,
                            indicatorColor = colors.iconTile,
                            unselectedIconColor = colors.text3,
                            unselectedTextColor = colors.text3,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.Today.route) {
                TodayScreen(
                    snackbars = snackbars,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onOpenLair = { navController.navigateTo(TopLevelDestination.Lair) },
                )
            }
            TopLevelDestination.entries.filter { it != TopLevelDestination.Today }.forEach { destination ->
                composable(destination.route) {
                    PlaceholderScreen(title = stringResource(destination.label))
                }
            }
            composable(SETTINGS_ROUTE) {
                PlaceholderScreen(title = stringResource(R.string.settings_title))
            }
        }
    }
}

private fun NavController.navigateTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.placeholder_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
