package com.dtraas.boodschp.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.dao.ActivityLogWithProduct
import com.dtraas.boodschp.data.model.DeveloperNotice
import com.dtraas.boodschp.data.model.DeveloperNotices
import com.dtraas.boodschp.data.repository.ActivityLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel(
    activityLogRepository: ActivityLogRepository,
) : ViewModel() {

    val developerNotices: List<DeveloperNotice> = DeveloperNotices.all

    val appActivity: StateFlow<List<ActivityLogWithProduct>> =
        activityLogRepository.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
