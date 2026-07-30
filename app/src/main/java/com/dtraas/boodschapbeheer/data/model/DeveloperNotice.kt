package com.dtraas.boodschapbeheer.data.model

import androidx.annotation.StringRes
import com.dtraas.boodschapbeheer.R

/** A short "what's new" style message from the app's developer, shown on the Nieuws screen. */
data class DeveloperNotice(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
)

object DeveloperNotices {
    /** Newest first. Static for now — there's no backend to fetch these from. */
    val all: List<DeveloperNotice> = listOf(
        DeveloperNotice(
            titleRes = R.string.notice_household_sharing_title,
            messageRes = R.string.notice_household_sharing_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_notifications_title,
            messageRes = R.string.notice_notifications_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_color_palette_title,
            messageRes = R.string.notice_color_palette_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_sort_options_title,
            messageRes = R.string.notice_sort_options_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_faster_scanning_title,
            messageRes = R.string.notice_faster_scanning_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_undo_title,
            messageRes = R.string.notice_undo_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_inventory_cards_title,
            messageRes = R.string.notice_inventory_cards_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_shopping_list_stores_title,
            messageRes = R.string.notice_shopping_list_stores_message,
        ),
        DeveloperNotice(
            titleRes = R.string.notice_statistics_title,
            messageRes = R.string.notice_statistics_message,
        ),
    )
}
