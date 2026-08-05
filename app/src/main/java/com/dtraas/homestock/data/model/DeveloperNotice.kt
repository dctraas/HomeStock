package com.dtraas.homestock.data.model

import androidx.annotation.StringRes
import com.dtraas.homestock.R

/** A short "what's new" style message from the app's developer, shown on the Meldingen screen. */
data class DeveloperNotice(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
)

object DeveloperNotices {
    /** Newest first. Static for now — there's no backend to fetch these from. */
    val all: List<DeveloperNotice> = listOf(
        DeveloperNotice(
            id = "household_sharing",
            titleRes = R.string.notice_household_sharing_title,
            messageRes = R.string.notice_household_sharing_message,
        ),
        DeveloperNotice(
            id = "notifications",
            titleRes = R.string.notice_notifications_title,
            messageRes = R.string.notice_notifications_message,
        ),
        DeveloperNotice(
            id = "color_palette",
            titleRes = R.string.notice_color_palette_title,
            messageRes = R.string.notice_color_palette_message,
        ),
        DeveloperNotice(
            id = "sort_options",
            titleRes = R.string.notice_sort_options_title,
            messageRes = R.string.notice_sort_options_message,
        ),
        DeveloperNotice(
            id = "faster_scanning",
            titleRes = R.string.notice_faster_scanning_title,
            messageRes = R.string.notice_faster_scanning_message,
        ),
        DeveloperNotice(
            id = "undo",
            titleRes = R.string.notice_undo_title,
            messageRes = R.string.notice_undo_message,
        ),
        DeveloperNotice(
            id = "inventory_cards",
            titleRes = R.string.notice_inventory_cards_title,
            messageRes = R.string.notice_inventory_cards_message,
        ),
        DeveloperNotice(
            id = "shopping_list_stores",
            titleRes = R.string.notice_shopping_list_stores_title,
            messageRes = R.string.notice_shopping_list_stores_message,
        ),
        DeveloperNotice(
            id = "statistics",
            titleRes = R.string.notice_statistics_title,
            messageRes = R.string.notice_statistics_message,
        ),
    )
}
