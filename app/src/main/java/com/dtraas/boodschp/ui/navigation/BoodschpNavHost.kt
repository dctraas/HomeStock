package com.dtraas.boodschp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import com.dtraas.boodschp.ui.activitylog.ActivityLogScreen
import com.dtraas.boodschp.ui.inventory.InventoryScreen
import com.dtraas.boodschp.ui.productdetail.ProductDetailScreen
import com.dtraas.boodschp.ui.scan.ScanScreen
import com.dtraas.boodschp.ui.scanresult.ScanResultScreen
import com.dtraas.boodschp.ui.shoppinglist.ShoppingListScreen
import com.dtraas.boodschp.ui.statistics.StatisticsScreen

@Composable
fun BoodschpApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.destination.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BoodschpBottomBar(navController, currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Scan.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Scan.route) {
                ScanScreen(
                    isActive = currentRoute == Destination.Scan.route,
                    onBarcodeScanned = { barcode ->
                        navController.navigate(Destination.ScanResult.createRoute(barcode))
                    },
                )
            }
            composable(Destination.Inventory.route) {
                InventoryScreen(
                    onProductClick = { barcode ->
                        navController.navigate(Destination.ProductDetail.createRoute(barcode))
                    },
                    onOpenActivityLog = {
                        navController.navigate(Destination.ActivityLog.route)
                    },
                )
            }
            composable(Destination.ShoppingList.route) {
                ShoppingListScreen()
            }
            composable(Destination.Statistics.route) {
                StatisticsScreen()
            }
            composable(
                route = Destination.ScanResult.route,
                arguments = listOf(navArgument("barcode") { type = NavType.StringType }),
            ) { entry ->
                val barcode = entry.arguments?.getString("barcode").orEmpty()
                ScanResultScreen(
                    barcode = barcode,
                    onSaved = {
                        navController.navigate(Destination.Inventory.route) {
                            popUpTo(Destination.Scan.route)
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
            composable(Destination.ActivityLog.route) {
                ActivityLogScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BoodschpBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        topLevelDestinations.forEach { destination ->
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
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
