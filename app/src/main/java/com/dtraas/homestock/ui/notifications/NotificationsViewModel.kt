package com.dtraas.homestock.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.model.DeveloperNotice
import com.dtraas.homestock.data.model.DeveloperNotices
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.ActivityReadStore
import com.dtraas.homestock.data.repository.DismissedNoticesStore
import com.dtraas.homestock.data.repository.HouseholdMember
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel(
    activityLogRepository: ActivityLogRepository,
    householdMembersRepository: HouseholdMembersRepository,
    private val dismissedNoticesStore: DismissedNoticesStore,
    private val activityReadStore: ActivityReadStore,
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

    // null = "Iedereen" — the header's per-member filter chips (replacing what used to be a
    // Huishouden/Meldingen tab pair; Meldingen lives as the "Berichten van HomeStock" teaser at
    // the bottom of the timeline now, see NotificationsScreen).
    private val _selectedMemberUid = MutableStateFlow<String?>(null)
    val selectedMemberUid: StateFlow<String?> = _selectedMemberUid

    fun onMemberFilterChange(uid: String?) {
        _selectedMemberUid.value = uid
    }

    /** [appActivity] narrowed to just the selected member's own entries. Matching is by
     *  (trimmed) display name — the same best-effort join
     *  [com.dtraas.homestock.data.repository.photoUrlFor] already uses elsewhere — since
     *  activityLog entries only ever stamped a name, not a uid. */
    val filteredActivity: StateFlow<List<ActivityLogWithProduct>> =
        combine(appActivity, members, selectedMemberUid) { activity, members, uid ->
            val name = uid?.let { id -> members.find { it.uid == id }?.displayName?.trim() }
            if (name.isNullOrEmpty()) activity else activity.filter { it.actorName?.trim() == name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When this device last looked at the activity timeline — see [ActivityReadStore]. Drives
     *  the "N wijzigingen sinds …" banner and each row's unread dot. Deliberately does *not*
     *  auto-advance just from opening this screen (unlike [markNoticesSeen] below) — the banner's
     *  own "Markeer gelezen" action is the only thing that moves it, so a household member can
     *  still see what's new even on a second glance at the same screen. */
    val lastActivitySeenAt: StateFlow<Long> = activityReadStore.lastSeenAt

    val unreadActivityCount: StateFlow<Int> =
        combine(appActivity, lastActivitySeenAt) { activity, seenAt -> activity.count { it.timestamp > seenAt } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markActivitySeen() {
        activityReadStore.markSeenNow()
    }

    fun dismissNotice(id: String) {
        dismissedNoticesStore.dismiss(id)
    }

    fun markNoticesSeen() {
        dismissedNoticesStore.markAllSeen()
    }
}
