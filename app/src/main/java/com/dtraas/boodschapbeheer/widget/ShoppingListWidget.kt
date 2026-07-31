package com.dtraas.boodschapbeheer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication
import com.dtraas.boodschapbeheer.MainActivity
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschapbeheer.data.repository.ShoppingListRepository

/**
 * Home screen widget showing the not-yet-checked shopping list lines, with a
 * checkbox on each row to mark it done directly without opening the app.
 * Widgets can't hold a live Firestore listener open, so this does a one-shot
 * fetch each time Android (re)composes it — see [ShoppingListRepository.getUncheckedItemsOnce].
 * [updateShoppingListWidget] below is called by the repository after every shopping
 * list write so the widget doesn't have to wait for the OS's own (infrequent)
 * update schedule to catch up.
 */
class ShoppingListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val application = context.applicationContext as BoodschapBeheerApplication
        val allUnchecked = application.container.shoppingListRepository.getUncheckedItemsOnce()
        val visibleItems = allUnchecked.take(MAX_VISIBLE_ITEMS)
        val overflowCount = allUnchecked.size - visibleItems.size

        val title = context.getString(R.string.widget_shopping_list_title)
        val emptyMessage = context.getString(R.string.widget_shopping_list_empty)
        val moreLabel = if (overflowCount > 0) {
            context.getString(R.string.widget_shopping_list_more_format, overflowCount)
        } else {
            null
        }

        provideContent {
            GlanceTheme {
                ShoppingListWidgetContent(
                    shoppingItems = visibleItems,
                    title = title,
                    emptyMessage = emptyMessage,
                    moreLabel = moreLabel,
                )
            }
        }
    }

    private companion object {
        const val MAX_VISIBLE_ITEMS = 12
    }
}

suspend fun updateShoppingListWidget(context: Context) {
    ShoppingListWidget().updateAll(context)
}

@Composable
private fun ShoppingListWidgetContent(
    shoppingItems: List<ShoppingListItemEntity>,
    title: String,
    emptyMessage: String,
    moreLabel: String?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = GlanceTheme.colors.onSurface,
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
        )

        if (shoppingItems.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = emptyMessage,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(shoppingItems, itemId = { it.id.hashCode().toLong() }) { item ->
                    ShoppingListWidgetRow(item)
                }
            }
            if (moreLabel != null) {
                Text(
                    text = moreLabel,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    modifier = GlanceModifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ShoppingListWidgetRow(item: ShoppingListItemEntity) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<ToggleShoppingListItemAction>(
                actionParametersOf(ToggleShoppingListItemAction.itemIdKey to item.id),
            ),
        )
        Text(
            text = item.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 4.dp),
        )
    }
}
