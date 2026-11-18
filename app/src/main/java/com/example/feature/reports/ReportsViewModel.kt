package com.example.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.DebtRepository
import com.example.core.database.entity.DebtStatus
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.SplitBillPortfolioSummary
import com.example.core.data.repository.SplitBillRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ReportPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

data class ReportsUiState(
    val selectedPeriod: ReportPeriod = ReportPeriod.MONTHLY,
    val netWorth: MoneyPaise = MoneyPaise.ZERO,
    val periodIncome: MoneyPaise = MoneyPaise.ZERO,
    val periodLedgerExpenses: MoneyPaise = MoneyPaise.ZERO,
    val netCashFlow: MoneyPaise = MoneyPaise.ZERO,
    val savingsRatePercent: String = "0.0%",
    val personalDirectSpending: MoneyPaise = MoneyPaise.ZERO,
    val actualPersonalSpending: MoneyPaise = MoneyPaise.ZERO,
    val groupExpensesPaidByUser: MoneyPaise = MoneyPaise.ZERO,
    val userGroupExpenseShare: MoneyPaise = MoneyPaise.ZERO,
    val splitReimbursementsReceived: MoneyPaise = MoneyPaise.ZERO,
    val outstandingSplitReceivables: MoneyPaise = MoneyPaise.ZERO,
    val splitPayables: MoneyPaise = MoneyPaise.ZERO,
    val lentOutstanding: MoneyPaise = MoneyPaise.ZERO,
    val borrowedOutstanding: MoneyPaise = MoneyPaise.ZERO,
    val overdueDebtCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    accountRepository: AccountRepository,
    private val ledgerRepository: LedgerRepository,
    splitBillRepository: SplitBillRepository? = null,
    debtRepository: DebtRepository? = null
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(ReportPeriod.MONTHLY)

    private data class PeriodMetrics(
        val period: ReportPeriod,
        val income: MoneyPaise,
        val totalExpenses: MoneyPaise,
        val personalDirectExpenses: MoneyPaise
    )

    private val emptyPortfolio = SplitBillPortfolioSummary(
        totalGroupExpenses = MoneyPaise.ZERO,
        youOwe = MoneyPaise.ZERO,
        youAreOwed = MoneyPaise.ZERO,
        unsettledAmount = MoneyPaise.ZERO,
        paidByUserTotal = MoneyPaise.ZERO,
        userPersonalShareTotal = MoneyPaise.ZERO,
        reimbursementsReceivedTotal = MoneyPaise.ZERO,
        outgoingSettlementsTotal = MoneyPaise.ZERO,
        activeGroupCount = 0,
        groupOverviews = emptyList()
    )

    private val periodCashFlowFlow = selectedPeriod.flatMapLatest { period ->
        val now = DateTimeUtils.nowEpochMs()
        val (start, end) = when (period) {
            ReportPeriod.DAILY -> DateTimeUtils.dayRange(now)
            ReportPeriod.WEEKLY -> DateTimeUtils.last7DaysRange(now)
            ReportPeriod.MONTHLY -> DateTimeUtils.currentMonthRange(now)
            ReportPeriod.YEARLY -> DateTimeUtils.currentYearRange(now)
        }
        combine(
            ledgerRepository.observeIncomeInRange(start, end),
            ledgerRepository.observeExpensesInRange(start, end),
            ledgerRepository.observePersonalDirectExpensesInRange(start, end)
        ) { income, expenses, directPersonal ->
            PeriodMetrics(
                period = period,
                income = income,
                totalExpenses = expenses,
                personalDirectExpenses = directPersonal
            )
        }
    }

    val uiState: StateFlow<ReportsUiState> = combine(
        periodCashFlowFlow,
        accountRepository.totalLiquidBalance,
        accountRepository.totalCreditCardOutstanding,
        splitBillRepository?.portfolioSummary ?: flowOf(emptyPortfolio),
        debtRepository?.debts ?: flowOf(emptyList())
    ) { metrics, liquidBalance, cardOutstanding, splitPortfolio, debts ->
        val netWorth = liquidBalance - cardOutstanding
        val cashFlow = metrics.income - metrics.totalExpenses
        val savingsRate = if (metrics.income.isPositive) {
            val pct = (cashFlow.paise.toDouble() / metrics.income.paise.toDouble()) * 100.0
            String.format(Locale.US, "%.1f%%", pct)
        } else {
            "0.0%"
        }
        val actualPersonalSpending = MoneyPaise(
            metrics.personalDirectExpenses.paise + splitPortfolio.userPersonalShareTotal.paise
        )
        ReportsUiState(
            selectedPeriod = metrics.period,
            netWorth = netWorth,
            periodIncome = metrics.income,
            periodLedgerExpenses = metrics.totalExpenses,
            netCashFlow = cashFlow,
            savingsRatePercent = savingsRate,
            personalDirectSpending = metrics.personalDirectExpenses,
            actualPersonalSpending = actualPersonalSpending,
            groupExpensesPaidByUser = splitPortfolio.paidByUserTotal,
            userGroupExpenseShare = splitPortfolio.userPersonalShareTotal,
            splitReimbursementsReceived = splitPortfolio.reimbursementsReceivedTotal,
            outstandingSplitReceivables = splitPortfolio.youAreOwed,
            splitPayables = splitPortfolio.youOwe,
            lentOutstanding = MoneyPaise(debts.filter { it.status == DebtStatus.ACTIVE && it.type.name == "LENT" }.sumOf { Math.addExact(it.outstandingPrincipalPaise, it.interestOutstandingPaise) }),
            borrowedOutstanding = MoneyPaise(debts.filter { it.status == DebtStatus.ACTIVE && it.type.name == "BORROWED" }.sumOf { Math.addExact(it.outstandingPrincipalPaise, it.interestOutstandingPaise) }),
            overdueDebtCount = debts.count { debt ->
                debt.status == DebtStatus.ACTIVE && (debt.outstandingPrincipalPaise > 0L || debt.interestOutstandingPaise > 0L) && debt.dueEpochMs?.let { due ->
                    Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(LocalDate.now())
                } == true
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportsUiState()
    )

    fun selectPeriod(period: ReportPeriod) {
        selectedPeriod.value = period
    }

    companion object {
        fun factory(
            accountRepository: AccountRepository,
            ledgerRepository: LedgerRepository,
            splitBillRepository: SplitBillRepository? = null,
            debtRepository: DebtRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ReportsViewModel(accountRepository, ledgerRepository, splitBillRepository, debtRepository) as T
                }
            }
    }
}
