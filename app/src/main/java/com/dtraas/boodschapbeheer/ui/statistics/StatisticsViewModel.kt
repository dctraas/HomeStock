package com.dtraas.boodschapbeheer.ui.statistics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.dao.ActorScanCount
import com.dtraas.boodschapbeheer.data.local.dao.CategoryCount
import com.dtraas.boodschapbeheer.data.local.dao.TopScannedProduct
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.repository.StatisticsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

/** Everything except the time-range-dependent scan count, which is combined in separately. */
private data class StaticStats(
    val totalInInventory: Int,
    val totalScansAllTime: Int,
    val topScannedProducts: List<TopScannedProduct>,
    val categoryDistribution: List<CategoryCount>,
    val scansByActor: List<ActorScanCount>,
)

/** How far back "scans in range" looks — the only thing the time-range toggle affects. */
enum class StatisticsTimeRange(@StringRes val labelRes: Int, val days: Long) {
    WEEK(R.string.statistics_this_week, 7),
    MONTH(R.string.statistics_range_month, 30),
}

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val totalInInventory: Int = 0,
    val totalScansAllTime: Int = 0,
    val timeRange: StatisticsTimeRange = StatisticsTimeRange.WEEK,
    val scansInRange: Int = 0,
    val topScannedProducts: List<TopScannedProduct> = emptyList(),
    val categoryDistribution: List<Pair<Category, Int>> = emptyList(),
    val scansByActor: List<ActorScanCount> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
) : ViewModel() {

    private val timeRange = MutableStateFlow(StatisticsTimeRange.WEEK)

    private val staticStats = combine(
        statisticsRepository.observeInventoryCount(),
        statisticsRepository.observeTotalScanCount(),
        statisticsRepository.observeTopScannedProducts(limit = 5),
        statisticsRepository.observeCategoryDistribution(),
        statisticsRepository.observeScansByActor(),
    ) { totalInInventory, totalScans, topProducts, categoryCounts, scansByActor ->
        StaticStats(totalInInventory, totalScans, topProducts, categoryCounts, scansByActor)
    }

    private val scansInRange = timeRange.flatMapLatest { range ->
        statisticsRepository.observeScanCountSince(sinceMillis(range)).map { count -> range to count }
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        staticStats,
        scansInRange,
    ) { stats, (range, scansInRangeCount) ->
        StatisticsUiState(
            isLoading = false,
            totalInInventory = stats.totalInInventory,
            totalScansAllTime = stats.totalScansAllTime,
            timeRange = range,
            scansInRange = scansInRangeCount,
            topScannedProducts = stats.topScannedProducts,
            categoryDistribution = stats.categoryDistribution
                .map { Category.fromStorageKey(it.category) to it.count }
                .sortedByDescending { it.second },
            scansByActor = stats.scansByActor,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun onTimeRangeChange(range: StatisticsTimeRange) {
        timeRange.value = range
    }
}

private fun sinceMillis(range: StatisticsTimeRange): Long =
    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(range.days)
