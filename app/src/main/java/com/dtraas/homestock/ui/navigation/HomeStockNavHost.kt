package com.dtraas.homestock.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.dtraas.homestock.ui.airecognize.AiRecognizeScreen
import com.dtraas.homestock.ui.account.AccountLinkScreen
import com.dtraas.homestock.ui.household.HouseholdSettingsScreen
import com.dtraas.homestock.ui.inventory.InventoryScreen
import com.dtraas.homestock.ui.mealplan.MealPlanScreen
import com.dtraas.homestock.ui.more.LicensesScreen
import com.dtraas.homestock.ui.more.MoreScreen
import com.dtraas.homestock.ui.more.PrivacyPolicyScreen
import com.dtraas.homestock.ui.notifications.NotificationsScreen
import com.dtraas.homestock.ui.premium.PremiumScreen
import com.dtraas.homestock.ui.productdetail.ProductDetailScreen
import com.dtraas.homestock.ui.receiptscan.ReceiptScanScreen
import com.dtraas.homestock.ui.recipes.CustomRecipeEditScreen
import com.dtraas.homestock.ui.recipes.RecipeDetailScreen
import com.dtraas.homestock.ui.recipes.RecipesScreen
import com.dtraas.homestock.ui.scan.ScanScreen
import com.dtraas.homestock.ui.scanresult.ScanResultScreen
import com.dtraas.homestock.ui.searchproduct.SearchProductScreen
import com.dtraas.homestock.ui.shoppinglist.ShoppingListScreen
import com.dtraas.homestock.ui.statistics.StatisticsScreen

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
        // This Scaffold has no topBar of its own — every route below provides its own via
        // HomeStockTopAppBar, which already fully draws through/pads for the status bar
        // (see MainActivity's edge-to-edge + status bar color sync). Left at the Scaffold
        // default, its top content padding falls back to the status bar inset (since there's
        // no topBar height to base it on instead) and gets applied here to the whole NavHost
        // — stacking a second, redundant status-bar-height gap on top of what each screen's
        // own top app bar already accounts for, pushing every title bar down from the actual
        // top edge. Only bottom/horizontal safe-area insets are still needed here.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
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
                    onBack = { navController.popBackStack() },
                    onNeedsConfirmation = { barcode ->
                        navController.navigate(Destination.ScanResult.createRoute(barcode))
                    },
                    onSearchClick = { navController.navigate(Destination.SearchProduct.route) },
                    onAiRecognizeClick = { navController.navigate(Destination.AiRecognize.route) },
                    onNavigateToPremium = { navController.navigate(Destination.Premium.route) },
                )
            }
            composable(Destination.Inventory.route) {
                InventoryScreen(
                    onProductClick = { barcode ->
                        navController.navigate(Destination.ProductDetail.createRoute(barcode))
                    },
                    onNavigateToScan = { navController.navigate(Destination.Scan.route) },
                    onNavigateToSearch = { navController.navigate(Destination.SearchProduct.route) },
                    onNavigateToReceiptScan = { navController.navigate(Destination.ReceiptScan.route) },
                    onNavigateToAiRecognize = { navController.navigate(Destination.AiRecognize.route) },
                    onNavigateToPremium = { navController.navigate(Destination.Premium.route) },
                    onNavigateToNotifications = { navController.navigate(Destination.Notifications.route) },
                )
            }
            composable(Destination.ShoppingList.route) {
                ShoppingListScreen()
            }
            composable(Destination.Statistics.route) {
                StatisticsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Notifications.route) {
                NotificationsScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.More.route) {
                MoreScreen(
                    onNavigateToStatistics = { navController.navigate(Destination.Statistics.route) },
                    onNavigateToPremium = { navController.navigate(Destination.Premium.route) },
                    onNavigateToHousehold = { navController.navigate(Destination.Household.route) },
                    onNavigateToAccountLink = { navController.navigate(Destination.AccountLink.route) },
                    onNavigateToPrivacyPolicy = { navController.navigate(Destination.PrivacyPolicy.route) },
                    onNavigateToLicenses = { navController.navigate(Destination.Licenses.route) },
                )
            }
            composable(Destination.PrivacyPolicy.route) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.Licenses.route) {
                LicensesScreen(onBack = { navController.popBackStack() })
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
                    onAddCustomRecipe = { navController.navigate(Destination.CustomRecipeEdit.createRoute()) },
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
                    onEdit = { recipeId -> navController.navigate(Destination.CustomRecipeEdit.createRoute(recipeId)) },
                )
            }
            composable(
                route = Destination.CustomRecipeEdit.route,
                arguments = listOf(navArgument("recipeId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val recipeId = entry.arguments?.getString("recipeId")
                CustomRecipeEditScreen(
                    recipeId = recipeId,
                    onBack = { navController.popBackStack() },
                    onSaved = { savedId ->
                        // Always a fresh navigate rather than popping back to an existing
                        // RecipeDetailScreen instance (the edit flow) — that screen's ViewModel
                        // already fetched detail before this save and has no reason to refresh
                        // on its own, so it would otherwise show stale pre-edit content. Popping
                        // up to Recipes first clears both the editor and any such stale detail
                        // screen; the fresh RecipeDetailScreen this pushes reads the just-saved
                        // detail straight from RecipeRepository's cache, no network round trip.
                        navController.navigate(Destination.RecipeDetail.createRoute(savedId)) {
                            popUpTo(Destination.Recipes.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onDeleted = {
                        // Pop both the editor and the detail screen underneath it — the recipe
                        // it showed no longer exists.
                        val popped = navController.popBackStack(Destination.Recipes.route, inclusive = false)
                        if (!popped) navController.popBackStack()
                    },
                )
            }
            composable(Destination.ReceiptScan.route) {
                ReceiptScanScreen(onBack = { navController.popBackStack() })
            }
            composable(Destination.AiRecognize.route) {
                AiRecognizeScreen(
                    onBack = { navController.popBackStack() },
                    onNeedsConfirmation = { barcode ->
                        navController.navigate(Destination.ScanResult.createRoute(barcode))
                    },
                )
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
    val application = LocalContext.current.applicationContext as HomeStockApplication
    // Recepten is premium-only, like it already was as a Meer entry — this tab needs the same
    // check since it's now reachable directly from the bar instead of always going through
    // that gated row first.
    val isPremium by application.container.householdMembersRepository
        .observeHouseholdIsPremium()
        .collectAsState(initial = false)

    NavigationBar {
        topLevelDestinations.forEach { destination ->
            val label = stringResource(destination.labelRes)
            val isLockedRecipes = destination.destination == Destination.Recipes && !isPremium
            NavigationBarItem(
                selected = currentRoute == destination.destination.route,
                onClick = {
                    if (isLockedRecipes) {
                        navController.navigate(Destination.Premium.route)
                    } else {
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
                    }
                },
                icon = { Icon(destination.icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
