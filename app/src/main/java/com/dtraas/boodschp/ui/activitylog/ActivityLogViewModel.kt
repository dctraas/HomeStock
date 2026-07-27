package com.dtraas.boodschp.ui.activitylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschp.data.local.dao.ActivityLogWithProduct
import com.dtraas.boodschp.data.repository.ActivityLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ActivityLogViewModel(
    activityLogRepository: ActivityLogRepository,
) : ViewModel() {

    val entries: StateFlow<List<ActivityLogWithProduct>> =
        activityLogRepository.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
