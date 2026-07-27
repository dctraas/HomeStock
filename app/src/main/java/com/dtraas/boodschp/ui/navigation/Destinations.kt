package com.dtraas.boodschp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {
    data object Scan : Destination("scan")
    data object Inventory : Destination("inventory")
    data object ShoppingList : Destination("shopping_list")
    data object Statistics : Destination("statistics")
    data object ScanResult : Destination("scan_result/{barcode}") {
        fun createRoute(barcode: String) = "scan_result/$barcode"
    }
    data object ProductDetail : Destination("product_detail/{barcode}") {
        fun createRoute(barcode: String) = "product_detail/$barcode"
    }
}

data class TopLevelDestination(
    val destination: Destination,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Destination.ShoppingList, "Lijstje", Icons.Filled.ShoppingCart),
    TopLevelDestination(Destination.Inventory, "Voorraad", Icons.Filled.Inventory2),
    TopLevelDestination(Destination.Scan, "Scannen", Icons.Filled.CameraAlt),
    TopLevelDestination(Destination.Statistics, "Statistieken", Icons.Filled.BarChart),
)
