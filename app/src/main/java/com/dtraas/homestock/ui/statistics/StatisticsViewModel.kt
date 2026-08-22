package com.dtraas.homestock.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.TopWastedProduct
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.MonthlyWaste
import com.dtraas.homestock.data.repository.StatisticsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One household member's scan activity, for the "Wie doet wat" cards — [photoUrl] is resolved
 *  by matching [com.dtraas.homestock.data.local.dao.ActorScanCount.actorName] against
 *  [com.dtraas.homestock.data.repository.HouseholdMember.displayName]; activityLog entries only
 *  ever stamped a plain name, not a uid, so this is a best-effort exact-name match rather than a
 *  guaranteed join — a member who's renamed themselves since their last scan simply falls back
 *  to the initials/icon placeholder the screen already shows for an unmatched name. */
data class MemberScanEntry(val name: String, val photoUrl: String?, val scanCount: Int)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val monthlyWaste: List<MonthlyWaste> = emptyList(),
    /** Percentage change of the current month's waste value vs. the previous month — null when
     *  the previous month has no waste value to compare against (division by zero). Positive
     *  means waste went up, negative means it went down. */
    val wasteDeltaPercent: Int? = null,
    val expiringSoonCount: Int = 0,
    val lowStockCount: Int = 0,
    val totalInInventory: Int = 0,
    val topWastedProducts: List<TopWastedProduct> = emptyList(),
    val memberScans: List<MemberScanEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
    private val householdMembersRepository: HouseholdMembersRepository,
) : ViewModel() {

    private val memberScans = combine(
        statisticsRepository.observeScansByActor(),
        householdMembersRepository.observeMembers(),
    ) { scans, members ->
        scans.map { scan ->
            val photoUrl = scan.actorName?.let { name -> members.firstOrNull { it.displayName == name }?.photoUrl }
            MemberScanEntry(name = scan.actorName ?: "", photoUrl = photoUrl, scanCount = scan.scanCount)
        }
    }

    // combine() only has typed overloads up to 5 flows — memberScans is folded in via a second,
    // nested combine rather than switching the whole block to the untyped vararg overload (same
    // pattern this ViewModel used before this rewrite).
    private val baseStats = combine(
        statisticsRepository.observeMonthlyWasteValue(monthsBack = 6),
        statisticsRepository.observeExpiringSoonCount(),
        statisticsRepository.observeLowStockCount(),
        statisticsRepository.observeInventoryCount(),
        statisticsRepository.observeTopWastedProducts(limit = 5),
    ) { monthlyWaste, expiringSoonCount, lowStockCount, totalInInventory, topWastedProducts ->
        StatisticsUiState(
            isLoading = false,
            monthlyWaste = monthlyWaste,
            wasteDeltaPercent = wasteDeltaPercent(monthlyWaste),
            expiringSoonCount = expiringSoonCount,
            lowStockCount = lowStockCount,
            totalInInventory = totalInInventory,
            topWastedProducts = topWastedProducts,
        )
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        baseStats,
        memberScans,
    ) { stats, scans -> stats.copy(memberScans = scans) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())
}

/** Percentage change of the last entry in [monthlyWaste] (the current month) vs. the one before
 *  it — null when there aren't at least 2 months, or the previous month's value is 0 (nothing to
 *  divide by, and "0% more" would misleadingly read as "no waste last month either"). */
private fun wasteDeltaPercent(monthlyWaste: List<MonthlyWaste>): Int? {
    if (monthlyWaste.size < 2) return null
    val previous = monthlyWaste[monthlyWaste.size - 2].totalValue
    val current = monthlyWaste.last().totalValue
    if (previous <= 0.0) return null
    return (((current - previous) / previous) * 100).toInt()
}
