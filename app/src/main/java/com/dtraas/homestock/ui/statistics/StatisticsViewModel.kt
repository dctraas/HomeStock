package com.dtraas.homestock.ui.statistics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.R
import com.dtraas.homestock.data.local.dao.ActorScanCount
import com.dtraas.homestock.data.local.dao.CategoryCount
import com.dtraas.homestock.data.local.dao.TopScannedProduct
import com.dtraas.homestock.data.model.ActivityType
import com.dtraas.homestock.data.model.Category
import com.dtraas.homestock.data.repository.StatisticsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit

/** Everything except the time-range-dependent stats, which are combined in separately. */
private data class StaticStats(
    val totalInInventory: Int,
    val totalScansAllTime: Int,
    val topScannedProducts: List<TopScannedProduct>,
    val categoryDistribution: List<CategoryCount>,
    val scansByActor: List<ActorScanCount>,
)

/** Inventory "health" counts — not tied to the time-range toggle, always all-current-stock. */
private data class InventoryHealthStats(
    val expiringSoonCount: Int,
    val lowStockCount: Int,
)

/** Everything the time-range toggle affects. */
private data class RangeStats(
    val range: StatisticsTimeRange,
    val scansInRange: Int,
    val removedInRange: Int,
    val addedToShoppingListInRange: Int,
)

/** How far back the time-range-dependent stats look. */
enum class StatisticsTimeRange(@StringRes val labelRes: Int, val days: Long) {
    WEEK(R.string.statistics_this_week, 7),
    MONTH(R.string.statistics_range_month, 30),
}

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val totalInInventory: Int = 0,
    val totalScansAllTime: Int = 0,
    val expiringSoonCount: Int = 0,
    val lowStockCount: Int = 0,
    val busiestWeekday: DayOfWeek? = null,
    val timeRange: StatisticsTimeRange = StatisticsTimeRange.WEEK,
    val scansInRange: Int = 0,
    val removedInRange: Int = 0,
    val addedToShoppingListInRange: Int = 0,
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

    private val inventoryHealth = combine(
        statisticsRepository.observeExpiringSoonCount(),
        statisticsRepository.observeLowStockCount(),
    ) { expiringSoon, lowStock -> InventoryHealthStats(expiringSoon, lowStock) }

    private val rangeStats = timeRange.flatMapLatest { range ->
        val since = sinceMillis(range)
        combine(
            statisticsRepository.observeScanCountSince(since),
            statisticsRepository.observeActivityCountByType(ActivityType.REMOVED, since),
            statisticsRepository.observeActivityCountByType(ActivityType.ADDED_TO_SHOPPING_LIST, since),
        ) { scans, removed, addedToList -> RangeStats(range, scans, removed, addedToList) }
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        staticStats,
        inventoryHealth,
        statisticsRepository.observeBusiestWeekday(),
        rangeStats,
    ) { stats, health, busiestWeekday, range ->
        StatisticsUiState(
            isLoading = false,
            totalInInventory = stats.totalInInventory,
            totalScansAllTime = stats.totalScansAllTime,
            expiringSoonCount = health.expiringSoonCount,
            lowStockCount = health.lowStockCount,
            busiestWeekday = busiestWeekday,
            timeRange = range.range,
            scansInRange = range.scansInRange,
            removedInRange = range.removedInRange,
            addedToShoppingListInRange = range.addedToShoppingListInRange,
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
