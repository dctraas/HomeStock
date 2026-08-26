package com.dtraas.homestock.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.dtraas.homestock.R
import java.time.LocalDate

sealed class Destination(val route: String) {
    data object Scan : Destination("scan")
    data object Inventory : Destination("inventory")
    data object ShoppingList : Destination("shopping_list")
    data object ShoppingMode : Destination("shopping_mode?listId={listId}") {
        // Empty string, not a real "no list" sentinel value, stands in for null here — NavType.StringType
        // arguments can't be genuinely null unless declared nullable, and a nullable String nav argument
        // still round-trips awkwardly through the query-string route syntax. The default (unnamed) list's
        // own id is already null everywhere else in this app (see ShoppingListItemEntity.listId), so "" is
        // free to mean exactly that here — ShoppingModeScreen turns it back into null before use.
        fun createRoute(listId: String?) = "shopping_mode?listId=${listId ?: ""}"
    }
    data object Statistics : Destination("statistics")
    data object Notifications : Destination("notifications")
    data object More : Destination("more")
    data object ScanResult : Destination("scan_result/{barcode}?fromScan={fromScan}") {
        /**
         * [fromScan] marks that this screen was reached mid-batch from the barcode camera
         * (an unknown barcode needing confirmation) rather than from search/AI-herkenning —
         * see HomeStockNavHost's onSaved handling for why that changes where "opslaan" lands.
         */
        fun createRoute(barcode: String, fromScan: Boolean = false) = "scan_result/$barcode?fromScan=$fromScan"
    }
    data object ProductDetail : Destination("product_detail/{barcode}") {
        fun createRoute(barcode: String) = "product_detail/$barcode"
    }
    data object SearchProduct : Destination("search_product")
    data object Recipes : Destination("recipes")
    data object RecipeDetail : Destination("recipe_detail/{mealId}") {
        fun createRoute(mealId: String) = "recipe_detail/$mealId"
    }
    data object CookMode : Destination("cook_mode/{mealId}") {
        fun createRoute(mealId: String) = "cook_mode/$mealId"
    }
    data object CustomRecipeEdit : Destination("custom_recipe_edit?recipeId={recipeId}&importId={importId}") {
        /**
         * [recipeId] null creates a new recipe; non-null edits an existing one. [importId] is a
         * third, mutually-exclusive case: also creates a new recipe, but pre-filled from an
         * already-imported draft (see [com.dtraas.homestock.data.repository.RecipeRepository.importRecipeFromUrl])
         * instead of starting empty.
         */
        fun createRoute(recipeId: String? = null, importId: String? = null): String {
            val params = listOfNotNull(
                recipeId?.let { "recipeId=$it" },
                importId?.let { "importId=$it" },
            )
            return if (params.isEmpty()) "custom_recipe_edit" else "custom_recipe_edit?${params.joinToString("&")}"
        }
    }
    data object ReceiptScan : Destination("receipt_scan")
    data object AiRecognize : Destination("ai_recognize")
    data object MealPlan : Destination("meal_plan")
    data object WeekOverview : Destination("week_overview/{anchorDate}") {
        // ISO-8601 (yyyy-MM-dd), same DATE_FORMATTER MealPlanRepository stores its own Firestore
        // document ids as — free of the "/" a plain LocalDate.toString() would need escaping.
        fun createRoute(anchorDate: LocalDate) = "week_overview/$anchorDate"
    }
    data object Premium : Destination("premium")
    data object Household : Destination("household")
    data object AppSettings : Destination("app_settings")
    data object AccountLink : Destination("account_link")
    data object PrivacyPolicy : Destination("privacy_policy")
    data object Licenses : Destination("licenses")
}

data class TopLevelDestination(
    val destination: Destination,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

// Scannen used to sit here as the app's primary action; it's now reached via the "+" on
// Voorraad instead (see InventoryScreen), which also offers zoeken/bonnetje/AI naast barcode
// scannen in one place rather than needing its own permanent tab. Meldingen moved off the bar
// too — reached via an icon on Voorraad's top bar instead (see InventoryScreen) — freeing this
// slot for Recepten.
val topLevelDestinations = listOf(
    TopLevelDestination(Destination.Recipes, R.string.more_beta_recipes, Icons.Filled.RestaurantMenu),
    TopLevelDestination(Destination.ShoppingList, R.string.nav_shopping_list, Icons.Filled.ShoppingCart),
    TopLevelDestination(Destination.Inventory, R.string.nav_inventory, Icons.Filled.Inventory2),
    TopLevelDestination(Destination.MealPlan, R.string.nav_meals, Icons.Filled.CalendarMonth),
    TopLevelDestination(Destination.More, R.string.nav_more, Icons.Filled.MoreHoriz),
)
