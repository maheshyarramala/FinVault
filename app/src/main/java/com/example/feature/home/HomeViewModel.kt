package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.preferences.HomeDashboardWidget
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.preferences.UserPreferencesState
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.BudgetProgressItem
import com.example.core.data.repository.BudgetRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.CreditCardRepository
import com.example.core.data.repository.CreditCardSummary
import com.example.core.data.repository.DebtLedgerSummary
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.LoanEmiLedgerSummary
import com.example.core.data.repository.MonthlyCashFlowBar
import com.example.core.data.repository.RecurringRuleRepository
import com.example.core.data.repository.SplitBillLedgerSummary
import com.example.core.data.repository.SplitBillPortfolioSummary
import com.example.core.data.repository.SplitBillRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.dao.CategorySpendAggregate
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class CategoryExpenseSlice(
    val categoryId: String?,
    val name: String,
    val colorHex: Long,
    val amount: MoneyPaise,
    val percentage: Float
)

sealed interface UpcomingReminderItem {
    val id: String
    val title: String
    val subtitle: String
    val amount: MoneyPaise
    val dueEpochMs: Long

    data class RecurringEmiOrBill(
        override val id: String,
        val ruleId: String,
        override val title: String,
        override val subtitle: String,
        override val amount: MoneyPaise,
        override val dueEpochMs: Long,
        val isEmiOrLoan: Boolean
    ) : UpcomingReminderItem

    data class CardDue(
        override val id: String,
        val cardAccountId: String,
        override val title: String,
        override val subtitle: String,
        override val amount: MoneyPaise,
        override val dueEpochMs: Long
    ) : UpcomingReminderItem
}

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val totalAccountBalance: MoneyPaise,
        val availableBalance: MoneyPaise,
        val bankBalance: MoneyPaise,
        val cashBalance: MoneyPaise,
        val savingsBalance: MoneyPaise,
        val totalCreditCardOutstanding: MoneyPaise,
        val netWorth: MoneyPaise,
        val monthlyIncome: MoneyPaise,
        val monthlyExpenses: MoneyPaise,
        val monthlyNetCashFlow: MoneyPaise,
        val savingsRatePercent: String,
        val activeAccountCount: Int,
        val seededCategoryCount: Int,
        val currentMonthLabel: String,
        val databaseVersion: Int,
        val recentTransactions: List<LedgerTransactionWithEntries>,
        val categoryBreakdown: List<CategoryExpenseSlice>,
        val cashFlowTrend: List<MonthlyCashFlowBar>,
        val budgetProgress: List<BudgetProgressItem>,
        val creditCards: List<CreditCardSummary>,
        val upcomingReminders: List<UpcomingReminderItem>,
        val loanEmiSummary: LoanEmiLedgerSummary,
        val debtSummary: DebtLedgerSummary,
        val splitBillSummary: SplitBillLedgerSummary,
        val splitBillPortfolio: SplitBillPortfolioSummary,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val hiddenWidgets: Set<HomeDashboardWidget>,
        val isCustomizeDialogOpen: Boolean,
        val statusMessage: String?,
        val errorMessage: String?
    ) : HomeUiState {
        fun isWidgetVisible(widget: HomeDashboardWidget): Boolean = widget !in hiddenWidgets
    }
}

class HomeViewModel(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    ledgerRepository: LedgerRepository,
    budgetRepository: BudgetRepository,
    creditCardRepository: CreditCardRepository,
    private val recurringRuleRepository: RecurringRuleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    splitBillRepository: SplitBillRepository? = null
) : ViewModel() {

    private val currentMonthKey = DateTimeUtils.monthKey()
    private val customizeDialogOpen = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private data class AccountSnapshot(
        val liquidBalance: MoneyPaise,
        val cardOutstanding: MoneyPaise,
        val accountCount: Int,
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>
    )

    private data class LedgerAnalyticsSnapshot(
        val monthlyIncome: MoneyPaise,
        val monthlyExpenses: MoneyPaise,
        val recentTransactions: List<LedgerTransactionWithEntries>,
        val categoryTotals: List<CategorySpendAggregate>,
        val cashFlowTrend: List<MonthlyCashFlowBar>,
        val debtSummary: DebtLedgerSummary,
        val splitSummary: SplitBillLedgerSummary,
        val loanEmiSummary: LoanEmiLedgerSummary
    )

    private data class ObligationsAndPrefsSnapshot(
        val budgets: List<BudgetProgressItem>,
        val cards: List<CreditCardSummary>,
        val rules: List<RecurringRuleEntity>,
        val splitPortfolio: SplitBillPortfolioSummary,
        val prefs: UserPreferencesState,
        val isCustomizeOpen: Boolean,
        val status: String?,
        val error: String?
    )

    private val emptySplitPortfolio = SplitBillPortfolioSummary(
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

    private val accountSnapshotFlow = combine<Any?, AccountSnapshot>(
        accountRepository.totalLiquidBalance,
        accountRepository.totalCreditCardOutstanding,
        accountRepository.activeAccountCount,
        accountRepository.activeAccounts,
        categoryRepository.allCategories
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        AccountSnapshot(
            liquidBalance = values[0] as MoneyPaise,
            cardOutstanding = values[1] as MoneyPaise,
            accountCount = values[2] as Int,
            accounts = values[3] as List<AccountEntity>,
            categories = values[4] as List<CategoryEntity>
        )
    }

    private val ledgerSnapshotFlow = combine<Any?, LedgerAnalyticsSnapshot>(
        ledgerRepository.observeMonthlyIncome(),
        ledgerRepository.observeMonthlyExpenses(),
        ledgerRepository.observeRecentTransactions(limit = 5),
        ledgerRepository.observeCategoryExpensesForMonth(currentMonthKey),
        ledgerRepository.observeMonthlyCashFlowTrend(monthsCount = 6),
        ledgerRepository.observeDebtLedgerSummary(),
        ledgerRepository.observeSplitBillLedgerSummary(),
        ledgerRepository.observeLoanEmiLedgerSummary()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LedgerAnalyticsSnapshot(
            monthlyIncome = values[0] as MoneyPaise,
            monthlyExpenses = values[1] as MoneyPaise,
            recentTransactions = values[2] as List<LedgerTransactionWithEntries>,
            categoryTotals = values[3] as List<CategorySpendAggregate>,
            cashFlowTrend = values[4] as List<MonthlyCashFlowBar>,
            debtSummary = values[5] as DebtLedgerSummary,
            splitSummary = values[6] as SplitBillLedgerSummary,
            loanEmiSummary = values[7] as LoanEmiLedgerSummary
        )
    }

    private val obligationsSnapshotFlow = combine<Any?, ObligationsAndPrefsSnapshot>(
        budgetRepository.observeBudgetsWithProgress(currentMonthKey),
        creditCardRepository.creditCards,
        recurringRuleRepository.activeRules,
        splitBillRepository?.portfolioSummary ?: flowOf(emptySplitPortfolio),
        userPreferencesRepository.preferences,
        customizeDialogOpen,
        statusMessage,
        errorMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        ObligationsAndPrefsSnapshot(
            budgets = values[0] as List<BudgetProgressItem>,
            cards = values[1] as List<CreditCardSummary>,
            rules = values[2] as List<RecurringRuleEntity>,
            splitPortfolio = values[3] as SplitBillPortfolioSummary,
            prefs = values[4] as UserPreferencesState,
            isCustomizeOpen = values[5] as Boolean,
            status = values[6] as String?,
            error = values[7] as String?
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        accountSnapshotFlow,
        ledgerSnapshotFlow,
        obligationsSnapshotFlow
    ) { accSnap, ledgerSnap, oblSnap ->
        val bankPaise = accSnap.accounts
            .filter { it.accountType == AccountType.BANK }
            .sumOf { it.currentBalancePaise }
        val cashPaise = accSnap.accounts
            .filter { it.accountType == AccountType.CASH }
            .sumOf { it.currentBalancePaise }
        val savingsPaise = accSnap.accounts
            .filter { it.accountType == AccountType.SAVINGS || it.accountType == AccountType.CUSTOM }
            .sumOf { it.currentBalancePaise }

        val netWorth = MoneyPaise(accSnap.liquidBalance.paise - accSnap.cardOutstanding.paise)
        val monthlyNet = MoneyPaise(ledgerSnap.monthlyIncome.paise - ledgerSnap.monthlyExpenses.paise)
        val savingsRate = if (ledgerSnap.monthlyIncome.isPositive) {
            val pct = (monthlyNet.paise.toDouble() / ledgerSnap.monthlyIncome.paise.toDouble()) * 100.0
            String.format(Locale.US, "%.1f%%", pct)
        } else {
            "0.0%"
        }

        val categoryMap = accSnap.categories.associateBy { it.id }
        val totalCategoryPaise = ledgerSnap.categoryTotals.sumOf { it.totalPaise }.coerceAtLeast(1L)
        val categoryBreakdown = ledgerSnap.categoryTotals
            .filter { it.totalPaise > 0L }
            .sortedByDescending { it.totalPaise }
            .map { agg ->
                val cat = agg.categoryId?.let { categoryMap[it] }
                CategoryExpenseSlice(
                    categoryId = agg.categoryId,
                    name = cat?.name ?: "Uncategorized",
                    colorHex = cat?.colorHex ?: 0xFF64748BL,
                    amount = MoneyPaise(agg.totalPaise),
                    percentage = ((agg.totalPaise.toDouble() / totalCategoryPaise.toDouble()) * 100.0).toFloat()
                )
            }

        val nowMs = DateTimeUtils.nowEpochMs()
        val ruleReminders = oblSnap.rules.map { rule ->
            val isEmi = rule.categoryId == "cat_exp_emi" ||
                rule.description.contains("emi", ignoreCase = true) ||
                rule.description.contains("loan", ignoreCase = true)
            UpcomingReminderItem.RecurringEmiOrBill(
                id = "rule_${rule.id}",
                ruleId = rule.id,
                title = rule.description,
                subtitle = "${if (isEmi) "EMI / Loan" else "Recurring"} • ${rule.frequency.name} • Due ${DateTimeUtils.formatShortDate(rule.nextRunEpochMs)}",
                amount = MoneyPaise(rule.amountPaise),
                dueEpochMs = rule.nextRunEpochMs,
                isEmiOrLoan = isEmi
            )
        }
        val cardReminders = oblSnap.cards
            .filter { it.reminderEnabled && it.outstandingBalance.paise > 0L }
            .map { card ->
                val nextDueMs = DateTimeUtils.nextDayOfMonthEpochMs(card.dueDay, nowMs)
                UpcomingReminderItem.CardDue(
                    id = "card_due_${card.accountId}",
                    cardAccountId = card.accountId,
                    title = "${card.name} Card Bill",
                    subtitle = "Due ${DateTimeUtils.formatShortDate(nextDueMs)} • ${card.utilizationPct}% utilized",
                    amount = card.outstandingBalance,
                    dueEpochMs = nextDueMs
                )
            }
        val upcomingReminders = (ruleReminders + cardReminders).sortedBy { it.dueEpochMs }

        HomeUiState.Ready(
            totalAccountBalance = accSnap.liquidBalance,
            availableBalance = accSnap.liquidBalance,
            bankBalance = MoneyPaise(bankPaise),
            cashBalance = MoneyPaise(cashPaise),
            savingsBalance = MoneyPaise(savingsPaise),
            totalCreditCardOutstanding = accSnap.cardOutstanding,
            netWorth = netWorth,
            monthlyIncome = ledgerSnap.monthlyIncome,
            monthlyExpenses = ledgerSnap.monthlyExpenses,
            monthlyNetCashFlow = monthlyNet,
            savingsRatePercent = savingsRate,
            activeAccountCount = accSnap.accountCount,
            seededCategoryCount = accSnap.categories.size,
            currentMonthLabel = DateTimeUtils.formatMonthYear(nowMs),
            databaseVersion = FinVaultDatabase.DATABASE_VERSION,
            recentTransactions = ledgerSnap.recentTransactions,
            categoryBreakdown = categoryBreakdown,
            cashFlowTrend = ledgerSnap.cashFlowTrend,
            budgetProgress = oblSnap.budgets,
            creditCards = oblSnap.cards,
            upcomingReminders = upcomingReminders,
            loanEmiSummary = ledgerSnap.loanEmiSummary,
            debtSummary = ledgerSnap.debtSummary,
            splitBillSummary = ledgerSnap.splitSummary,
            splitBillPortfolio = oblSnap.splitPortfolio,
            accounts = accSnap.accounts,
            categories = accSnap.categories,
            hiddenWidgets = oblSnap.prefs.hiddenHomeWidgets,
            isCustomizeDialogOpen = oblSnap.isCustomizeOpen,
            statusMessage = oblSnap.status,
            errorMessage = oblSnap.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading
    )

    fun openCustomizeDialog() {
        customizeDialogOpen.value = true
    }

    fun dismissCustomizeDialog() {
        customizeDialogOpen.value = false
    }

    fun toggleWidgetVisibility(widget: HomeDashboardWidget, visible: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHomeWidgetVisible(widget, visible)
        }
    }

    fun resetWidgetsToDefault() {
        viewModelScope.launch {
            userPreferencesRepository.resetHomeWidgets()
            statusMessage.value = "Dashboard widgets reset to default"
        }
    }

    fun postRecurringReminder(ruleId: String) {
        viewModelScope.launch {
            runCatching {
                recurringRuleRepository.postNextOccurrence(ruleId)
            }.onSuccess { result ->
                if (result != null) {
                    statusMessage.value = "Scheduled entry posted to ledger"
                }
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to post scheduled entry"
            }
        }
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    companion object {
        fun factory(
            accountRepository: AccountRepository,
            categoryRepository: CategoryRepository,
            ledgerRepository: LedgerRepository,
            budgetRepository: BudgetRepository,
            creditCardRepository: CreditCardRepository,
            recurringRuleRepository: RecurringRuleRepository,
            userPreferencesRepository: UserPreferencesRepository,
            splitBillRepository: SplitBillRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    ledgerRepository = ledgerRepository,
                    budgetRepository = budgetRepository,
                    creditCardRepository = creditCardRepository,
                    recurringRuleRepository = recurringRuleRepository,
                    userPreferencesRepository = userPreferencesRepository,
                    splitBillRepository = splitBillRepository
                ) as T
            }
        }
    }
}
