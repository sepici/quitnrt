package app.thelasthit.nrt.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.reminders.NoticeTarget
import app.thelasthit.nrt.ui.absorbed.AbsorbedScreen
import app.thelasthit.nrt.ui.advice.AdviceRoutes
import app.thelasthit.nrt.ui.advice.KeepScreenOnWhileTimersRun
import app.thelasthit.nrt.ui.advice.advice
import app.thelasthit.nrt.ui.advice.adviceLinks
import app.thelasthit.nrt.ui.game.UnlockHost
import app.thelasthit.nrt.ui.history.HistoryScreen
import app.thelasthit.nrt.ui.insights.InsightsScreen
import app.thelasthit.nrt.ui.lair.LairScreen
import app.thelasthit.nrt.ui.lair.LairTabIcon
import app.thelasthit.nrt.ui.lair.lairTabSpoken
import app.thelasthit.nrt.ui.lair.rememberLairHitsReady
import app.thelasthit.nrt.ui.link.LinkHost
import app.thelasthit.nrt.ui.plan.PlanRoutes
import app.thelasthit.nrt.ui.plan.PlanScreen
import app.thelasthit.nrt.ui.plan.SettleOnStart
import app.thelasthit.nrt.ui.plan.planPages
import app.thelasthit.nrt.ui.settings.AboutScreen
import app.thelasthit.nrt.ui.settings.CatalogueScreen
import app.thelasthit.nrt.ui.settings.DayStartScreen
import app.thelasthit.nrt.ui.settings.FontLicence
import app.thelasthit.nrt.ui.settings.LicenceScreen
import app.thelasthit.nrt.ui.settings.ProductsScreen
import app.thelasthit.nrt.ui.settings.QuickLogScreen
import app.thelasthit.nrt.ui.settings.RemindersScreen
import app.thelasthit.nrt.ui.settings.SettingsLinks
import app.thelasthit.nrt.ui.settings.SettingsScreen
import app.thelasthit.nrt.ui.settings.TagsScreen
import app.thelasthit.nrt.ui.settings.WidgetTileScreen
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.today.TodayScreen
import kotlinx.coroutines.launch

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

/** Settings and its pages. The tab bar hides on all of them. */
private object SettingsRoutes {
    const val MAIN = "settings"
    const val PRODUCTS = "settings/products"
    const val CATALOGUE = "settings/catalogue"
    const val QUICK_LOG = "settings/quick-log"
    const val TAGS = "settings/tags"
    const val DAY_START = "settings/day-start"
    const val ABOUT = "settings/about"
    const val LICENCE = "settings/licence/{font}"
    const val REMINDERS = "settings/reminders"
    const val WIDGET_TILE = "settings/widget-tile"
    const val ABSORBED = "settings/absorbed"

    fun licence(font: FontLicence) = "settings/licence/${font.name}"
}

@Composable
fun AppShell(notices: NoticeTargetViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbars = remember { SnackbarHostState() }
    // Outlives each page, for messages shown as a page closes.
    val shellScope = rememberCoroutineScope()
    val resources = LocalResources.current
    val showMessage: (Int) -> Unit = { res -> shellScope.launch { snackbars.showSnackbar(resources.getString(res)) } }
    val colors = LastHit.colors

    // Settings, the Plan tab's own pages and the advice pages hide the tab bar.
    val hideTabs = currentRoute?.startsWith(SettingsRoutes.MAIN) == true ||
        PlanRoutes.isPage(currentRoute) ||
        AdviceRoutes.isAdvice(currentRoute)
    val graph = (LocalContext.current.applicationContext as LastHitApp).graph
    // Results that fell due while the app was away arrive when it comes back (ROADMAP P3-3).
    SettleOnStart { graph.programmes.settle() }
    // True while the lair is to open at its trophy shelf, as an unlock's toast or card asks.
    var toShelf by remember { mutableStateOf(false) }
    val adviceLinks = remember(navController) { navController.adviceLinks() }

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = {
            SnackbarHost(snackbars) { data ->
                Snackbar(data, containerColor = colors.text, contentColor = colors.bg, actionColor = colors.bg)
            }
        },
        bottomBar = {
            if (!hideTabs) BottomBar(currentRoute, navController)
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Today.route,
            // Each page makes room for the status bar itself (see page), so the lair's stage can run up behind it.
            modifier = Modifier.padding(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding(),
            ),
        ) {
            page(TopLevelDestination.Today.route) {
                TodayScreen(
                    snackbars = snackbars,
                    onOpenSettings = { navController.navigate(SettingsRoutes.MAIN) },
                    onOpenLair = { navController.navigateTo(TopLevelDestination.Lair) },
                    onEditQuickLog = { navController.navigate(SettingsRoutes.QUICK_LOG) },
                    adviceLinks = adviceLinks,
                )
            }
            page(TopLevelDestination.History.route) {
                HistoryScreen(snackbars = snackbars)
            }
            page(TopLevelDestination.Insights.route) {
                InsightsScreen(snackbars = snackbars, adviceLinks = adviceLinks)
            }
            page(TopLevelDestination.Plan.route) {
                PlanScreen(
                    onUsualDay = { navController.navigate(PlanRoutes.usualDay(it)) },
                    onBuild = { navController.navigate(PlanRoutes.BUILD) },
                    onGoals = { navController.navigate(PlanRoutes.GOALS) },
                    onOpenLair = { navController.navigateTo(TopLevelDestination.Lair) },
                    onNewDemon = { navController.navigate(PlanRoutes.NEW_DEMON) },
                )
            }
            planPages(navController, onOpenCatalogue = { navController.navigate(SettingsRoutes.CATALOGUE) })
            composable(TopLevelDestination.Lair.route) { LairScreen(toShelf = toShelf, onShelfShown = { toShelf = false }) }
            settings(navController, snackbars, showMessage)
            advice(navController)
        }
        // A tapped notice opens its page: a weekly result the Plan tab, where a missed week's
        // choice waits, and a badge the lair's shelf. After the NavHost, so its graph is set.
        val noticeTarget by notices.target.collectAsStateWithLifecycle()
        LaunchedEffect(noticeTarget) {
            val target = noticeTarget ?: return@LaunchedEffect
            when (target) {
                NoticeTarget.PLAN -> navController.navigateTo(TopLevelDestination.Plan)
                NoticeTarget.SHELF -> {
                    toShelf = true
                    navController.navigateTo(TopLevelDestination.Lair)
                }
            }
            notices.opened(target)
        }
        // A running timer keeps the screen on, whichever page is showing.
        KeepScreenOnWhileTimersRun()
        // Links and shortcuts show their snackbar, question or sheet over whichever page is open.
        LinkHost(snackbars)
        // New badges and gear slide in over whichever page is open.
        UnlockHost(
            onOpenShelf = {
                toShelf = true
                navController.navigateTo(TopLevelDestination.Lair)
            },
        )
    }
}

/** A page that starts below the status bar, as every page but the lair does. */
private fun NavGraphBuilder.page(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { entry ->
        Box(Modifier.fillMaxSize().statusBarsPadding()) { content(entry) }
    }
}

private fun NavGraphBuilder.settings(navController: NavController, snackbars: SnackbarHostState, showMessage: (Int) -> Unit) {
    val back: () -> Unit = { navController.popBackStack() }
    page(SettingsRoutes.MAIN) {
        SettingsScreen(
            snackbars = snackbars,
            onBack = back,
            links = SettingsLinks(
                products = { navController.navigate(SettingsRoutes.PRODUCTS) },
                quickLog = { navController.navigate(SettingsRoutes.QUICK_LOG) },
                tags = { navController.navigate(SettingsRoutes.TAGS) },
                dayStart = { navController.navigate(SettingsRoutes.DAY_START) },
                about = { navController.navigate(SettingsRoutes.ABOUT) },
                reminders = { navController.navigate(SettingsRoutes.REMINDERS) },
                widgetTile = { navController.navigate(SettingsRoutes.WIDGET_TILE) },
                absorbed = { navController.navigate(SettingsRoutes.ABSORBED) },
            ),
        )
    }
    page(SettingsRoutes.PRODUCTS) {
        ProductsScreen(onBack = back, onAddFromCatalogue = { navController.navigate(SettingsRoutes.CATALOGUE) })
    }
    page(SettingsRoutes.CATALOGUE) { CatalogueScreen(onBack = back) }
    page(SettingsRoutes.QUICK_LOG) { QuickLogScreen(onBack = back) }
    page(SettingsRoutes.TAGS) { TagsScreen(onBack = back) }
    page(SettingsRoutes.DAY_START) {
        DayStartScreen(onBack = back, onSaved = {
            showMessage(R.string.day_saved)
            back()
        })
    }
    page(SettingsRoutes.ABOUT) {
        AboutScreen(onBack = back, onLicence = { navController.navigate(SettingsRoutes.licence(it)) })
    }
    page(SettingsRoutes.REMINDERS) { RemindersScreen(onBack = back) }
    page(SettingsRoutes.WIDGET_TILE) { WidgetTileScreen(onBack = back) }
    page(SettingsRoutes.ABSORBED) { AbsorbedScreen(onBack = back) }
    page(SettingsRoutes.LICENCE) { entry ->
        val font = entry.arguments?.getString("font")?.let { name -> FontLicence.entries.firstOrNull { it.name == name } }
        if (font != null) LicenceScreen(font, onBack = back)
    }
}

@Composable
private fun BottomBar(currentRoute: String?, navController: NavController) {
    val colors = LastHit.colors
    val lairReady = rememberLairHitsReady()
    val lairSpoken = lairTabSpoken(lairReady)
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
                icon = {
                    // The Lair tab carries a badge for the hits ready to spend.
                    if (destination == TopLevelDestination.Lair) {
                        LairTabIcon(painterResource(destination.icon), lairReady)
                    } else {
                        Icon(painterResource(destination.icon), contentDescription = null)
                    }
                },
                label = {
                    val spoken = if (destination == TopLevelDestination.Lair) lairSpoken else null
                    Text(
                        stringResource(destination.label),
                        modifier = if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier,
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
}

private fun NavController.navigateTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
