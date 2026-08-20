package com.dtraas.homestock.ui.navigation

import androidx.compose.foundation.draw.drawBehind
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dtraas.homestock.ui.onboarding.OnboardingTourScreen
import com.dtraas.homestock.ui.premium.PremiumScreen
import com.dtraas.homestock.ui.productdetail.ProductDetailScreen
import com.dtraas.homestock.ui.receiptscan.ReceiptScanScreen
import com.dtraas.homestock.ui.recipes.CookModeScreen
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
    val onboardingTourPreferences = application.container.onboardingTourPreferences
    val justJoinedHousehold by householdSession.justJoinedHousehold.collectAsState()
    var showAccountLinkPrompt by remember { mutableStateOf(false) }
    // Purely "has this device ever seen it", independent of justJoinedHousehold below — a
    // device joining via invite code, not just one creating/joining fresh from CHOOSE, is
    // just as much a first-time HomeStockApp landing, and an existing install updating to the
    // version that first ships this tour has never seen it either, so it gets the same
    // one-time introduction.
    var showOnboardingTour by remember { mutableStateOf(!onboardingTourPreferences.hasSeenTour) }

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
                        navController.navigate(Destination.ScanResult.createRoute(barcode, fromScan = true))
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
                    onNavigateToRecipes = { navController.navigate(Destination.Recipes.route) },
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
                arguments = listOf(
                    navArgument("barcode") { type = NavType.StringType },
                    navArgument("fromScan") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val barcode = entry.arguments?.getString("barcode").orEmpty()
                val fromScan = entry.arguments?.getBoolean("fromScan") ?: false
                ScanResultScreen(
                    barcode = barcode,
                    fromScan = fromScan,
                    onSaved = {
                        if (fromScan) {
                            // Reached mid-batch from the barcode camera (an unknown barcode
                            // needing confirmation): pop straight back to it instead of all the
                            // way out to Voorraad, so scanning the next item doesn't require
                            // re-opening the camera by hand. Known barcodes already never leave
                            // the camera screen at all (see ScanScreen/ScanViewModel); this
                            // closes the same gap for new ones.
                            navController.popBackStack()
                        } else {
                            // Inventory is the app's start destination, but it isn't always on
                            // the *current* back stack: switching to another bottom-nav tab pops
                            // everything up to and including it off with saveState = true (see
                            // HomeStockBottomBar), tucking it away in saved state instead of
                            // leaving it on the live stack. Scanning from the Scan tab after
                            // switching tabs that way left this popBackStack silently failing
                            // ("Ignoring popBackStack to route inventory..."), stranding the
                            // user on this screen. Try the cheap direct pop first (covers the
                            // common "scanned straight from Inventory" case); if Inventory
                            // genuinely isn't on the stack, fall back to a full navigate that's
                            // guaranteed to land there regardless of how this screen was reached.
                            val popped = navController.popBackStack(Destination.Inventory.route, inclusive = false)
                            if (!popped) {
                                navController.navigate(Destination.Inventory.route) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
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
                    onNavigateToPremium = { navController.navigate(Destination.Premium.route) },
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
                    onRecipeClick = { mealId ->
                        navController.navigate(Destination.RecipeDetail.createRoute(mealId))
                    },
                    onAddCustomRecipe = { navController.navigate(Destination.CustomRecipeEdit.createRoute()) },
                    onImportedRecipe = { importId ->
                        navController.navigate(Destination.CustomRecipeEdit.createRoute(importId = importId))
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
                    onEdit = { recipeId -> navController.navigate(Destination.CustomRecipeEdit.createRoute(recipeId)) },
                    onStartCookMode = { navController.navigate(Destination.CookMode.createRoute(mealId)) },
                )
            }
            composable(
                route = Destination.CookMode.route,
                arguments = listOf(navArgument("mealId") { type = NavType.StringType }),
            ) { entry ->
                val mealId = entry.arguments?.getString("mealId").orEmpty()
                CookModeScreen(
                    mealId = mealId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.CustomRecipeEdit.route,
                arguments = listOf(
                    navArgument("recipeId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("importId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val recipeId = entry.arguments?.getString("recipeId")
                val importId = entry.arguments?.getString("importId")
                CustomRecipeEditScreen(
                    recipeId = recipeId,
                    importId = importId,
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
                    onRecipeClick = { mealId -> navController.navigate(Destination.RecipeDetail.createRoute(mealId)) },
                    onProductClick = { barcode -> navController.navigate(Destination.ProductDetail.createRoute(barcode)) },
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

    // Tour first, account-link nudge only once it's out of the way — showing both overlays at
    // once (a brand new device that also just created/joined a household hits both conditions
    // together) would just be two modals fighting for attention on someone's very first screen.
    if (showAccountLinkPrompt && !showOnboardingTour) {
        AccountLinkPromptDialog(
            onLinkNow = {
                showAccountLinkPrompt = false
                navController.navigate(Destination.AccountLink.route)
            },
            onDismiss = { showAccountLinkPrompt = false },
        )
    }

    if (showOnboardingTour) {
        OnboardingTourScreen(
            onFinish = {
                onboardingTourPreferences.markTourSeen()
                showOnboardingTour = false
            },
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

    val borderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val borderWidthPx = with(LocalDensity.current) { 1.dp.toPx() }

    // "Keuken" redesign: a plain surfaceContainer bar (not the coral-tinted indicator Material3
    // defaults to) with a hairline top border, since coral is the app's CTA color from here on
    // and shouldn't double as nav-selection state too (see the 2026-08 handoff doc). The active
    // item's own sage pill + dark ink (rather than the primary-tinted default) does the same job
    // without that clash.
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(
                color = borderColor,
                start = Offset.Zero,
                end = Offset(size.width, 0f),
                strokeWidth = borderWidthPx,
            )
        },
    ) {
        topLevelDestinations.forEach { destination ->
            val label = stringResource(destination.labelRes)
            val isLockedRecipes = destination.destination == Destination.Recipes && !isPremium
            val selected = currentRoute == destination.destination.route
            NavigationBarItem(
                selected = selected,
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
                icon = { Icon(destination.icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
