package com.dtraas.boodschapbeheer.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication

/**
 * Marks a shopping list line as checked from the widget's checkbox. The widget only ever
 * shows unchecked items, so there's no "uncheck" direction to handle here — the row simply
 * disappears once [com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository.setChecked]
 * triggers the widget's own refresh.
 */
class ToggleShoppingListItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val itemId = parameters[itemIdKey] ?: return
        val application = context.applicationContext as BoodschapBeheerApplication
        application.container.shoppingListRepository.setChecked(itemId, true)
    }

    companion object {
        val itemIdKey = ActionParameters.Key<String>("shopping_list_item_id")
    }
}
