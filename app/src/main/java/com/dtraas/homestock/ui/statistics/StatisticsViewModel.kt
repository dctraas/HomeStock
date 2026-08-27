package com.dtraas.homestock.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.local.dao.ActivityLogWithProduct
import com.dtraas.homestock.data.local.dao.TopScannedProduct
import com.dtraas.homestock.data.local.dao.TopWastedProduct
import com.dtraas.homestock.data.repository.ActivityLogRepository
import com.dtraas.homestock.data.repository.HouseholdMembersRepository
import com.dtraas.homestock.data.repository.HouseholdRepository
import com.dtraas.homestock.data.repository.HouseholdSession
import com.dtraas.homestock.data.repository.MonthlyWaste
import com.dtraas.homestock.data.repository.StatisticsRepository
import com.dtraas.homestock.data.repository.YearlyWaste
import com.dtraas.homestock.data.repository.photoUrlFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which period the hero chart/eyebrow currently shows — the "Maand"/"Jaar" pills in the header. */
enum class StatisticsPeriod { MONTH, YEAR }

/** One household member's scan activity, for the "Wie doet wat" cards — [photoUrl] is resolved
 *  by matching [com.dtraas.homestock.data.local.dao.ActorScanCount.actorName] against
 *  [com.dtraas.homestock.data.repository.HouseholdMember.displayName]; activityLog entries only
 *  ever stamped a plain name, not a uid, so this is a best-effort exact-name match rather than a
 *  guaranteed join — a member who's renamed themselves since their last scan simply falls back
 *  to the initials/icon placeholder the screen already shows for an unmatched name. */
data class MemberScanEntry(val name: String, val photoUrl: String?, val scanCount: Int)

/** One member's share of this month's activity, as a rounded percentage of the household total —
 *  the "Dennis 62% · Marieke 38%" row. */
data class ActivityShareEntry(val name: String, val percent: Int)

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val period: StatisticsPeriod = StatisticsPeriod.MONTH,
    val monthlyWaste: List<MonthlyWaste> = emptyList(),
    val yearlyWaste: List<YearlyWaste> = emptyList(),
    /** Percentage change of the current period's waste value vs. the previous one — null when
     *  there's nothing to compare against (division by zero, or not enough history). Positive
     *  means waste went up, negative means it went down. */
    val wasteDeltaPercent: Int? = null,
    val wasteProductCountThisMonth: Int = 0,
    val wasteGoal: Double? = null,
    val savedValueThisMonth: Double = 0.0,
    val expiringSoonCount: Int = 0,
    val lowStockCount: Int = 0,
    val totalInInventory: Int = 0,
    val categoryCount: Int = 0,
    val topWastedProducts: List<TopWastedProduct> = emptyList(),
    val topScannedProducts: List<TopScannedProduct> = emptyList(),
    val memberScans: List<MemberScanEntry> = emptyList(),
    val activityShare: List<ActivityShareEntry> = emptyList(),
    val recentActivity: List<ActivityLogWithProduct> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
    private val householdMembersRepository: HouseholdMembersRepository,
    private val householdRepository: HouseholdRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val householdSession: HouseholdSession,
) : ViewModel() {

    private val periodFlow = MutableStateFlow(StatisticsPeriod.MONTH)

    fun onPeriodChange(period: StatisticsPeriod) {
        periodFlow.value = period
    }

    fun setWasteGoal(goal: Double?) {
        val householdId = householdSession.householdId.value ?: return
        viewModelScope.launch { householdRepository.setWasteGoal(householdId, goal) }
    }

    private val memberScans = combine(
        statisticsRepository.observeScansByActor(),
        householdMembersRepository.observeMembers(),
    ) { scans, members ->
        scans.map { scan ->
            MemberScanEntry(name = scan.actorName ?: "", photoUrl = members.photoUrlFor(scan.actorName), scanCount = scan.scanCount)
        }
    }

    private val activityShare = statisticsRepository.observeActivityShareThisMonth().map { counts ->
        val total = counts.sumOf { it.count }
        if (total == 0) return@map emptyList()
        counts.map { ActivityShareEntry(name = it.actorName.orEmpty(), percent = ((it.count * 100) / total)) }
    }

    // combine() only has typed overloads up to 5 flows, so this ViewModel's ~13 source flows are
    // folded together in a handful of pairwise/five-wide steps rather than one giant call.
    private val wasteAndGoal = combine(
        statisticsRepository.observeMonthlyWasteValue(monthsBack = 6),
        statisticsRepository.observeYearlyWasteValue(yearsBack = 6),
        statisticsRepository.observeWasteProductCountThisMonth(),
        householdRepository.observeWasteGoal(),
        statisticsRepository.observeSavedValueThisMonth(),
    ) { monthly, yearly, productCount, goal, saved ->
        WasteAndGoal(monthly, yearly, productCount, goal, saved)
    }

    private val statusAndTopWasted = combine(
        statisticsRepository.observeExpiringSoonCount(withinDays = 7),
        statisticsRepository.observeLowStockCount(),
        statisticsRepository.observeInventoryCount(),
        statisticsRepository.observeCategoryDistribution(),
        statisticsRepository.observeTopWastedProducts(limit = 5),
    ) { expiringSoon, lowStock, totalInInventory, categories, topWasted ->
        StatusAndTopWasted(expiringSoon, lowStock, totalInInventory, categories.size, topWasted)
    }

    private val activityDetail = combine(
        activityShare,
        statisticsRepository.observeTopScannedProducts(limit = 5),
        activityLogRepository.observeRecent(limit = 6),
    ) { share, topScanned, recent -> ActivityDetail(share, topScanned, recent) }

    private val baseStats = combine(wasteAndGoal, statusAndTopWasted, activityDetail, periodFlow) { waste, status, activity, period ->
        StatisticsUiState(
            isLoading = false,
            period = period,
            monthlyWaste = waste.monthly,
            yearlyWaste = waste.yearly,
            wasteDeltaPercent = when (period) {
                StatisticsPeriod.MONTH -> monthlyDeltaPercent(waste.monthly)
                StatisticsPeriod.YEAR -> yearlyDeltaPercent(waste.yearly)
            },
            wasteProductCountThisMonth = waste.productCount,
            wasteGoal = waste.goal,
            savedValueThisMonth = waste.saved,
            expiringSoonCount = status.expiringSoon,
            lowStockCount = status.lowStock,
            totalInInventory = status.totalInInventory,
            categoryCount = status.categoryCount,
            topWastedProducts = status.topWasted,
            topScannedProducts = activity.topScanned,
            activityShare = activity.share,
            recentActivity = activity.recent,
        )
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        baseStats,
        memberScans,
    ) { stats, scans -> stats.copy(memberScans = scans) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())
}

private data class WasteAndGoal(
    val monthly: List<MonthlyWaste>,
    val yearly: List<YearlyWaste>,
    val productCount: Int,
    val goal: Double?,
    val saved: Double,
)

private data class StatusAndTopWasted(
    val expiringSoon: Int,
    val lowStock: Int,
    val totalInInventory: Int,
    val categoryCount: Int,
    val topWasted: List<TopWastedProduct>,
)

private data class ActivityDetail(
    val share: List<ActivityShareEntry>,
    val topScanned: List<TopScannedProduct>,
    val recent: List<ActivityLogWithProduct>,
)

/** Percentage change of the last entry in [monthlyWaste] (the current month) vs. the one before
 *  it — null when there aren't at least 2 months, or the previous month's value is 0 (nothing to
 *  divide by, and "0% more" would misleadingly read as "no waste last month either"). */
private fun monthlyDeltaPercent(monthlyWaste: List<MonthlyWaste>): Int? {
    if (monthlyWaste.size < 2) return null
    val previous = monthlyWaste[monthlyWaste.size - 2].totalValue
    val current = monthlyWaste.last().totalValue
    if (previous <= 0.0) return null
    return (((current - previous) / previous) * 100).toInt()
}

/** Same idea as [monthlyDeltaPercent], for the "Jaar" period. */
private fun yearlyDeltaPercent(yearlyWaste: List<YearlyWaste>): Int? {
    if (yearlyWaste.size < 2) return null
    val previous = yearlyWaste[yearlyWaste.size - 2].totalValue
    val current = yearlyWaste.last().totalValue
    if (previous <= 0.0) return null
    return (((current - previous) / previous) * 100).toInt()
}
