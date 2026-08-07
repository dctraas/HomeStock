package com.dtraas.homestock.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.ui.account.AccountLinkPromptDialog
import com.dtraas.homestock.ui.account.AccountLinkScreen
import com.dtraas.homestock.ui.household.HouseholdSettingsScreen
import com.dtraas.homestock.ui.inventory.InventoryScreen
import com.dtraas.homestock.ui.mealplan.MealPlanScreen
import com.dtraas.homestock.ui.more.MoreScreen
import com.dtraas.homestock.ui.notifications.NotificationsScreen
import com.dtraas.homestock.ui.premium.PremiumScreen
import com.dtraas.homestock.ui.productdetail.ProductDetailScreen
import com.dtraas.homestock.ui.receiptscan.ReceiptScanScreen
import com.dtraas.homestock.ui.recipes.RecipeDetailScreen
import com.dtraas.homestock.ui.recipes.RecipesScreen
import com.dtraas.homestock.ui.scan.ScanScreen
import com.dtraas.homestock.ui.scanresult.ScanResultScreen
import com.dtraas.homestock.ui.searchproduct.SearchProductScreen
import com.dtraas.homestock.ui.shoppinglist.ShoppingListScreen
import com.dtraas.homestock.ui.statistics.StatisticsScreen
import com.dtraas.homestock.ui.theme.SoftBadgeShape

@Composable
fun HomeStockApp(pendingRoute: String? = null, onPendingRouteConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.destination.route == currentRoute }

    val application = LocalContext.current.applicationContext as HomeStockApplication
    val householdSession = application.container.householdSession
    val accountLinkRepository = application.container.accountLinkRepository
    val justJoinedHousehold by householdSession.justJoinedHousehold.collectAsState()
    var showAccountLinkPrompt by remember { mutableStateOf(false) }

    // Fires once, right when this composable first mounts after creating/joining a household
    // (see HouseholdSession.setHousehold) — a one-time nudge rather than a blocking step in
    // that flow, and never shown again afterward regardless of how the user responds.
    LaunchedEffect(justJoinedHousehold) {
        if (justJoinedHousehold) {
            if (!accountLinkRepository.hasShownLinkPrompt) {
                showAccountLinkPrompt = true
                accountLinkRepository.markLinkPromptShown()
            }
            householdSession.consumeJustJoinedHousehold()
        }
    }

    // A launcher shortcut (see MainActivity/shortcuts.xml) was tapped — jump straight there,
    // on top of the normal Inventory start destination so back navigation still lands there.
    // Keyed on pendingRoute itself (not Unit) so re-tapping the same shortcut while already on
    // that screen still re-fires this, matching MainActivity's onNewIntent semantics.
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            onPendingRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                HomeStockBottomBar(navController, currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Inventory.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Scan.route) {
                ScanScreen(
                    isActive = currentRoute == Destination.Scan.route,
                    onNeedsConfirmation = { barcode ->
                        navController.navigate(Destination.ScanResult.createRoute(barcode))
                    },
                    onSearchClick = { navController.navigate(Destination.SearchProduct.route) },
                )
            }
            composable(Destination.Inventory.route) {
                InventoryScreen(
                    onProductClick = { barcode ->
                        navController.navigate(Destination.ProductDetail.createRoute(barcode))
                    },
                )
            }
            composable(Destination.ShoppingList.route) {
                ShoppingListScreen()
            }
            composable(Destination.Statistics.route) {
                StatisticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Notifications.route) {
                NotificationsScreen()
            }
            composable(Destination.More.route) {
                MoreScreen(
                    onNavigateToRecipes = { navController.navigate(Destination.Recipes.route) },
                    onNavigateToReceiptScan = { navController.navigate(Destination.ReceiptScan.route) },
                    onNavigateToMealPlan = { navController.navigate(Destination.MealPlan.route) },
                    onNavigateToStatistics = { navController.navigate(Destination.Statistics.route) },
                    onNavigateToPremium = { navController.navigate(Destination.Premium.route) },
                    onNavigateToHousehold = { navController.navigate(Destination.Household.route) },
                    onNavigateToAccountLink = { navController.navigate(Destination.AccountLink.route) },
                )
            }
            composable(
                route = Destination.ScanResult.route,
                arguments = listOf(navArgument("barcode") { type = NavType.StringType }),
            ) { entry ->
                val barcode = entry.arguments?.getString("barcode").orEmpty()
                ScanResultScreen(
                    barcode = barcode,
                    onSaved = {
                        // Inventory is the app's start destination, but it isn't always on the
                        // *current* back stack: switching to another bottom-nav tab pops
                        // everything up to and including it off with saveState = true (see
                        // HomeStockBottomBar), tucking it away in saved state instead of
                        // leaving it on the live stack. Scanning from the Scan tab after
                        // switching tabs that way left this popBackStack silently failing
                        // ("Ignoring popBackStack to route inventory..."), stranding the user
                        // on this screen. Try the cheap direct pop first (covers the common
                        // "scanned straight from Inventory" case); if Inventory genuinely isn't
                        // on the stack, fall back to a full navigate that's guaranteed to land
                        // there regardless of how this screen was reached.
                        val popped = navController.popBackStack(Destination.Inventory.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(Destination.Inventory.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.ProductDetail.route,
                arguments = listOf(navArgument("barcode") { type = NavType.StringType }),
            ) { entry ->
                val barcode = entry.arguments?.getString("barcode").orEmpty()
                ProductDetailScreen(
                    barcode = barcode,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.SearchProduct.route) {
                SearchProductScreen(
                    onBack = { navController.popBackStack() },
                    onResultClick = { barcode ->
                        navController.navigate(Destination.ScanResult.createRoute(barcode))
                    },
                )
            }
            composable(Destination.Recipes.route) {
                RecipesScreen(
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { mealId ->
                        navController.navigate(Destination.RecipeDetail.createRoute(mealId))
                    },
                )
            }
            composable(
                route = Destination.RecipeDetail.route,
                arguments = listOf(navArgument("mealId") { type = NavType.StringType }),
            ) { entry ->
                val mealId = entry.arguments?.getString("mealId").orEmpty()
                RecipeDetailScreen(
                    mealId = mealId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.ReceiptScan.route) {
                ReceiptScanScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.MealPlan.route) {
                MealPlanScreen(
                    onBack = { navController.popBackStack() },
                    onRecipeClick = { mealId -> navController.navigate(Destination.RecipeDetail.createRoute(mealId)) },
                )
            }
            composable(Destination.Premium.route) {
                PremiumScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Household.route) {
                HouseholdSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.AccountLink.route) {
                AccountLinkScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    if (showAccountLinkPrompt) {
        AccountLinkPromptDialog(
            onLinkNow = {
                showAccountLinkPrompt = false
                navController.navigate(Destination.AccountLink.route)
            },
            onDismiss = { showAccountLinkPrompt = false },
        )
    }
}

@Composable
private fun HomeStockBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        topLevelDestinations.forEach { destination ->
            val isScan = destination.destination == Destination.Scan
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = currentRoute == destination.destination.route,
                onClick = {
                    val startRoute = navController.graph.findStartDestination().route
                    if (destination.destination.route == startRoute) {
                        // navigate(startRoute) { popUpTo(startRoute) { ... } } is a known
                        // no-op edge case in Navigation Compose when the target IS the
                        // popUpTo anchor — it silently fails to update the displayed
                        // screen. Popping back to it directly sidesteps that entirely.
                        navController.popBackStack(
                            route = startRoute!!,
                            inclusive = false,
                        )
                    } else {
                        navController.navigate(destination.destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    if (isScan) {
                        // The scan action is the app's primary action, so it gets a
                        // filled circular badge to stand out from the plain icons.
                        Surface(
                            shape = SoftBadgeShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = label,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    } else {
                        Icon(destination.icon, contentDescription = label)
                    }
                },
                label = { Text(label) },
                colors = if (isScan) {
                    NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                } else {
                    NavigationBarItemDefaults.colors()
                },
            )
        }
    }
}
