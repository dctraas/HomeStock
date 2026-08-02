package com.dtraas.boodschapbeheer.ui.navigation

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
import androidx.compose.runtime.getValue
import com.dtraas.boodschapbeheer.ui.inventory.InventoryScreen
import com.dtraas.boodschapbeheer.ui.more.MoreScreen
import com.dtraas.boodschapbeheer.ui.notifications.NotificationsScreen
import com.dtraas.boodschapbeheer.ui.premium.PremiumScreen
import com.dtraas.boodschapbeheer.ui.productdetail.ProductDetailScreen
import com.dtraas.boodschapbeheer.ui.receiptscan.ReceiptScanScreen
import com.dtraas.boodschapbeheer.ui.recipes.RecipeDetailScreen
import com.dtraas.boodschapbeheer.ui.recipes.RecipesScreen
import com.dtraas.boodschapbeheer.ui.scan.ScanScreen
import com.dtraas.boodschapbeheer.ui.scanresult.ScanResultScreen
import com.dtraas.boodschapbeheer.ui.searchproduct.SearchProductScreen
import com.dtraas.boodschapbeheer.ui.shoppinglist.ShoppingListScreen
import com.dtraas.boodschapbeheer.ui.statistics.StatisticsScreen
import com.dtraas.boodschapbeheer.ui.theme.SoftBadgeShape

@Composable
fun BoodschapBeheerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.destination.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BoodschapBeheerBottomBar(navController, currentRoute)
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
                    onNavigateToStatistics = { navController.navigate(Destination.Statistics.route) },
                    onNavigateToPremium = { navController.navigate(Destination.Premium.route) },
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
                        // Inventory is the app's start destination, so it's always on the
                        // back stack — popping straight to it discards the scan detour
                        // (scan_result, and scan itself if it was pushed) in one go.
                        navController.popBackStack(Destination.Inventory.route, inclusive = false)
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
            composable(Destination.Premium.route) {
                PremiumScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BoodschapBeheerBottomBar(navController: NavHostController, currentRoute: String?) {
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
