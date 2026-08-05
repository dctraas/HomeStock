package com.dtraas.homestock.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.model.DeveloperNotice
import com.dtraas.homestock.data.model.DeveloperNotices
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.DismissedNoticesStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel(
    activityLogRepository: ActivityLogRepository,
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

    fun dismissNotice(id: String) {
        dismissedNoticesStore.dismiss(id)
    }
}
