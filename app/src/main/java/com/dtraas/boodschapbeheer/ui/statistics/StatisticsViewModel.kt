package com.dtraas.boodschapbeheer.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.data.local.dao.TopScannedProduct
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.repository.StatisticsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val totalInInventory: Int = 0,
    val totalScansAllTime: Int = 0,
    val scansThisWeek: Int = 0,
    val topScannedProducts: List<TopScannedProduct> = emptyList(),
    val categoryDistribution: List<Pair<Category, Int>> = emptyList(),
)

class StatisticsViewModel(
    statisticsRepository: StatisticsRepository,
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = combine(
        statisticsRepository.observeInventoryCount(),
        statisticsRepository.observeTotalScanCount(),
        statisticsRepository.observeScanCountSince(sevenDaysAgoMillis()),
        statisticsRepository.observeTopScannedProducts(limit = 5),
        statisticsRepository.observeCategoryDistribution(),
    ) { totalInInventory, totalScans, scansThisWeek, topProducts, categoryCounts ->
        StatisticsUiState(
            isLoading = false,
            totalInInventory = totalInInventory,
            totalScansAllTime = totalScans,
            scansThisWeek = scansThisWeek,
            topScannedProducts = topProducts,
            categoryDistribution = categoryCounts
                .map { Category.fromStorageKey(it.category) to it.count }
                .sortedByDescending { it.second },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())
}

private fun sevenDaysAgoMillis(): Long = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
