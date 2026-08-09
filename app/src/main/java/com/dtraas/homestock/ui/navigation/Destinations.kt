package com.dtraas.homestock.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.dtraas.homestock.R

sealed class Destination(val route: String) {
    data object Scan : Destination("scan")
    data object Inventory : Destination("inventory")
    data object ShoppingList : Destination("shopping_list")
    data object Statistics : Destination("statistics")
    data object Notifications : Destination("notifications")
    data object More : Destination("more")
    data object ScanResult : Destination("scan_result/{barcode}") {
        fun createRoute(barcode: String) = "scan_result/$barcode"
    }
    data object ProductDetail : Destination("product_detail/{barcode}") {
        fun createRoute(barcode: String) = "product_detail/$barcode"
    }
    data object SearchProduct : Destination("search_product")
    data object Recipes : Destination("recipes")
    data object RecipeDetail : Destination("recipe_detail/{mealId}") {
        fun createRoute(mealId: String) = "recipe_detail/$mealId"
    }
    data object ReceiptScan : Destination("receipt_scan")
    data object AiRecognize : Destination("ai_recognize")
    data object MealPlan : Destination("meal_plan")
    data object Premium : Destination("premium")
    data object Household : Destination("household")
    data object AccountLink : Destination("account_link")
}

data class TopLevelDestination(
    val destination: Destination,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

// Scannen used to sit here (middle slot) as the app's primary action; it's now reached via the
// "+" on Voorraad instead (see InventoryScreen), which also offers zoeken/bonnetje/AI naast
// barcode scannen in one place rather than needing its own permanent tab. Maaltijdplanner takes
// its slot instead.
val topLevelDestinations = listOf(
    TopLevelDestination(Destination.Inventory, R.string.nav_inventory, Icons.Filled.Inventory2),
    TopLevelDestination(Destination.ShoppingList, R.string.nav_shopping_list, Icons.Filled.ShoppingCart),
    TopLevelDestination(Destination.MealPlan, R.string.nav_meals, Icons.Filled.RestaurantMenu),
    TopLevelDestination(Destination.Notifications, R.string.nav_news, Icons.Filled.Notifications),
    TopLevelDestination(Destination.More, R.string.nav_more, Icons.Filled.MoreHoriz),
)
