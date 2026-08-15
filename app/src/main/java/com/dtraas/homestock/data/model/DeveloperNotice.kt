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
    /**
     * Newest first. Static for now — there's no backend to fetch these from, so shipping a new
     * notice means adding an entry here (plus its strings in all 5 locales) in the same PR as
     * the feature it announces. Keep this list curated, not exhaustive: entries that have been
     * superseded by a later redesign — or that no longer describe the app accurately — should be
     * deleted outright rather than left to go stale (e.g. "2 per rij" once the grid dropped that
     * constraint), same reasoning `id`s are never reused for something else once retired.
     */
    val all: List<DeveloperNotice> = listOf(
        DeveloperNotice(
            id = "csv_import_export",
            titleRes = R.string.notice_csv_import_export_title,
            messageRes = R.string.notice_csv_import_export_message,
        ),
        DeveloperNotice(
            id = "premium",
            titleRes = R.string.notice_premium_title,
            messageRes = R.string.notice_premium_message,
        ),
        DeveloperNotice(
            id = "ai_recognition",
            titleRes = R.string.notice_ai_recognition_title,
            messageRes = R.string.notice_ai_recognition_message,
        ),
        DeveloperNotice(
            id = "receipt_scan_ai",
            titleRes = R.string.notice_receipt_scan_ai_title,
            messageRes = R.string.notice_receipt_scan_ai_message,
        ),
        DeveloperNotice(
            id = "recipes_ai",
            titleRes = R.string.notice_recipes_ai_title,
            messageRes = R.string.notice_recipes_ai_message,
        ),
        DeveloperNotice(
            id = "meal_planner",
            titleRes = R.string.notice_meal_planner_title,
            messageRes = R.string.notice_meal_planner_message,
        ),
        DeveloperNotice(
            id = "favorites",
            titleRes = R.string.notice_favorites_title,
            messageRes = R.string.notice_favorites_message,
        ),
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
