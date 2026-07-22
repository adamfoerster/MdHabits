package com.adamfoerster.mdhabits.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.ui.components.GearIcon
import com.adamfoerster.mdhabits.ui.components.GiftTabIcon
import com.adamfoerster.mdhabits.ui.components.HomeTabIcon
import com.adamfoerster.mdhabits.ui.components.ListTabIcon
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.screens.home.HomeScreen
import com.adamfoerster.mdhabits.ui.screens.redeem.RedeemScreen
import com.adamfoerster.mdhabits.ui.screens.register.RegisterScreen
import com.adamfoerster.mdhabits.ui.screens.review.ReviewScreen
import com.adamfoerster.mdhabits.ui.screens.settings.SettingsScreen
import com.adamfoerster.mdhabits.ui.screens.theme.ThemeScreen
import com.adamfoerster.mdhabits.ui.theme.Paper
import kotlin.reflect.KClass

private data class TabItem(
    val route: Any,
    val kClass: KClass<*>,
    val label: String,
    val icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
)

@Composable
fun MainScaffold() {
    val strings = LocalStrings.current
    val navController = rememberNavController()
    val tabs = listOf(
        TabItem(HomeTab, HomeTab::class, strings.tabHome) { color -> HomeTabIcon(color) },
        TabItem(RedeemTab, RedeemTab::class, strings.tabRedeem) { color -> GiftTabIcon(color) },
        TabItem(RegisterTab, RegisterTab::class, strings.tabRegister) { color -> ListTabIcon(color) },
        TabItem(SettingsTab, SettingsTab::class, strings.tabSettings) { color -> GearIcon(color, 21.dp) },
    )

    fun switchTab(route: Any) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = Paper.bg,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val current = backStackEntry?.destination
            Row(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(Paper.divider, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
                    }
                    .background(Paper.card)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 9.dp),
            ) {
                tabs.forEach { tab ->
                    val selected = current?.hierarchyHasRoute(tab.kClass) == true ||
                        // Theme and Review live on the Home stack; keep Home lit for them.
                        (tab.kClass == HomeTab::class && current?.hierarchy?.any {
                            it.hasRoute(ThemeScreenRoute::class) || it.hasRoute(ReviewRoute::class)
                        } == true)
                    val color = if (selected) Paper.accent else Paper.muted
                    Column(
                        Modifier
                            .weight(1f)
                            .paperClick { switchTab(tab.route) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            tab.icon(color)
                        }
                        Text(
                            tab.label,
                            style = sansStyle(10.5.sp, color, if (selected) FontWeight.Bold else FontWeight.Medium),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeTab,
            modifier = Modifier
                .fillMaxSize()
                .background(Paper.bg)
                .padding(innerPadding)
                .statusBarsPadding(),
        ) {
            composable<HomeTab> {
                HomeScreen(
                    onOpenTheme = { navController.navigate(ThemeScreenRoute) },
                    onOpenSettings = { switchTab(SettingsTab) },
                    onOpenRedeem = { switchTab(RedeemTab) },
                    onOpenReview = { navController.navigate(ReviewRoute("current")) },
                )
            }
            composable<RedeemTab> { RedeemScreen() }
            composable<RegisterTab> { RegisterScreen() }
            composable<SettingsTab> {
                SettingsScreen(onOpenTheme = { navController.navigate(ThemeScreenRoute) })
            }
            composable<ThemeScreenRoute> { ThemeScreen(onBack = { navController.popBackStack() }) }
            composable<ReviewRoute> { ReviewScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun androidx.navigation.NavDestination.hierarchyHasRoute(kClass: KClass<*>): Boolean =
    hierarchy.any { it.hasRoute(kClass) }
