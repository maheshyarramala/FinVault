package com.example.feature.finance

import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.BudgetProgressItem
import com.example.core.data.repository.CreditCardSummary
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringRuleEntity

enum class FinanceSectionTab {
    EXPENSES_AND_ACCOUNTS,
    EXPENSES,
    ACCOUNTS,
    CARDS,
    BUDGETS
}

enum class TransactionTypeFilter {
    ALL,
    EXPENSE,
    INCOME,
    TRANSFER,
    CARD
}

enum class DateRangePreset {
    ALL,
    TODAY,
    THIS_MONTH,
    LAST_30_DAYS
}

enum class TransactionFormKind {
    EXPENSE,
    INCOME,
    TRANSFER,
    CARD_PAYMENT
}

sealed interface ActiveFinanceOverlay {
    data object None : ActiveFinanceOverlay
    data class TransactionForm(
        val kind: TransactionFormKind,
        val editingTransaction: LedgerTransactionWithEntries? = null,
        val preselectedAccountId: String? = null,
        val preselectedCardId: String? = null
    ) : ActiveFinanceOverlay

    data class TransactionDetail(
        val transaction: LedgerTransactionWithEntries
    ) : ActiveFinanceOverlay

    data class AccountStatement(
        val account: AccountEntity
    ) : ActiveFinanceOverlay

    data object AddAccount : ActiveFinanceOverlay
    data class EditAccount(val account: AccountEntity) : ActiveFinanceOverlay
    data object AddCreditCard : ActiveFinanceOverlay
    data class EditCreditCard(val card: CreditCardSummary) : ActiveFinanceOverlay
    data class BudgetForm(val editingBudget: BudgetProgressItem? = null) : ActiveFinanceOverlay
}

data class FinanceUiState(
    val selectedTab: FinanceSectionTab = FinanceSectionTab.EXPENSES_AND_ACCOUNTS,
    val searchQuery: String = "",
    val typeFilter: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val selectedCategoryId: String? = null,
    val selectedAccountId: String? = null,
    val dateRangePreset: DateRangePreset = DateRangePreset.ALL,
    val budgetMonthKey: String = DateTimeUtils.monthKey(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<LedgerTransactionWithEntries> = emptyList(),
    val filteredTransactions: List<LedgerTransactionWithEntries> = emptyList(),
    val creditCards: List<CreditCardSummary> = emptyList(),
    val budgets: List<BudgetProgressItem> = emptyList(),
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val totalLiquidBalance: MoneyPaise = MoneyPaise.ZERO,
    val totalCardOutstanding: MoneyPaise = MoneyPaise.ZERO,
    val activeOverlay: ActiveFinanceOverlay = ActiveFinanceOverlay.None,
    val showAddCategoryDialog: Boolean = false,
    val isSubmitting: Boolean = false,
    val defaultExpensePaymentMethod: PaymentMethod = PaymentMethod.BANK,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val hasActiveTransactionFilters: Boolean
        get() = searchQuery.isNotBlank() ||
            typeFilter != TransactionTypeFilter.ALL ||
            selectedCategoryId != null ||
            selectedAccountId != null ||
            dateRangePreset != DateRangePreset.ALL
}
