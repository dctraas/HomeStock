package com.dtraas.homestock.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.local.dao.InventoryItemWithProduct
import com.dtraas.homestock.data.model.DeveloperNotice
import com.dtraas.homestock.data.model.DeveloperNotices
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.DismissedNoticesStore
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.InventoryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The two filter chips replacing the old two tabs — "Huishouden" is the default. The earlier
 *  third option, "Alles", was removed on request; Huishouden's own timeline still surfaces a
 *  teaser row into Meldingen (see NotificationsScreen's showTipsTeaser) so tips stay reachable
 *  without a dedicated "everything" view. */
enum class ActivityFilter { HOUSEHOLD, TIPS }

class NotificationsViewModel(
    activityLogRepository: ActivityLogRepository,
    inventoryRepository: InventoryRepository,
    householdMembersRepository: HouseholdMembersRepository,
    private val dismissedNoticesStore: DismissedNoticesStore,
) : ViewModel() {

    val developerNotices: StateFlow<List<DeveloperNotice>> =
        dismissedNoticesStore.dismissedIds
            .map { dismissedIds -> DeveloperNotices.all.filterNot { it.id in dismissedIds } }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DeveloperNotices.all.filterNot { it.id in dismissedNoticesStore.dismissedIds.value },
            )

    val appActivity: StateFlow<List<ActivityLogWithProduct>> =
        activityLogRepository.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<HouseholdMember>> =
        householdMembersRepository.observeMembers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The single most urgent expiring item — soonest expiration date, today or tomorrow only —
     * for the "Alles"/"Huishouden" timeline's urgent card at the top. Only one at a time (the
     * design shows exactly one urgent card, not a stack); a household with several items
     * expiring today still only sees the single most pressing one here, the rest are still
     * fully visible on Voorraad itself.
     */
    val urgentItem: StateFlow<InventoryItemWithProduct?> =
        inventoryRepository.observeInventoryWithProduct()
            .map { items ->
                val today = LocalDate.now()
                val cutoff = today.plusDays(1)
                items
                    .filter { item ->
                        val expiration = item.expirationDate ?: return@filter false
                        val date = Instant.ofEpochMilli(expiration).atZone(ZoneId.systemDefault()).toLocalDate()
                        !date.isBefore(today) && !date.isAfter(cutoff)
                    }
                    .minByOrNull { it.expirationDate!! }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _filter = MutableStateFlow(ActivityFilter.HOUSEHOLD)
    val filter: StateFlow<ActivityFilter> = _filter

    fun onFilterChange(filter: ActivityFilter) {
        _filter.value = filter
    }

    fun dismissNotice(id: String) {
        dismissedNoticesStore.dismiss(id)
    }

    fun markNoticesSeen() {
        dismissedNoticesStore.markAllSeen()
    }
}
