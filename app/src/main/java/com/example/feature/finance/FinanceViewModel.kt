package com.example.feature.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.BudgetRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.CreditCardRepository
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.RecurringRuleRepository
import com.example.core.database.dao.AccountStatementEntryRow
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CategoryType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringFrequency
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.LedgerPostSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val ledgerRepository: LedgerRepository,
    private val creditCardRepository: CreditCardRepository,
    private val budgetRepository: BudgetRepository,
    private val recurringRuleRepository: RecurringRuleRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(FinanceSectionTab.EXPENSES_AND_ACCOUNTS)
    private val searchQuery = MutableStateFlow("")
    private val typeFilter = MutableStateFlow(TransactionTypeFilter.ALL)
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val selectedAccountId = MutableStateFlow<String?>(null)
    private val dateRangePreset = MutableStateFlow(DateRangePreset.ALL)
    private val budgetMonthKey = MutableStateFlow(DateTimeUtils.monthKey())
    private val activeOverlay = MutableStateFlow<ActiveFinanceOverlay>(ActiveFinanceOverlay.None)
    private val showAddCategoryDialog = MutableStateFlow(false)
    private val isSubmitting = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private data class FilterState(
        val tab: FinanceSectionTab,
        val query: String,
        val typeFilter: TransactionTypeFilter,
        val categoryId: String?,
        val accountId: String?,
        val datePreset: DateRangePreset
    )

    private data class UiControlState(
        val monthKey: String,
        val overlay: ActiveFinanceOverlay,
        val showAddCategory: Boolean,
        val submitting: Boolean,
        val status: String?,
        val error: String?
    )

    private val filterState = combine<Any?, FilterState>(
        selectedTab,
        searchQuery,
        typeFilter,
        selectedCategoryId,
        selectedAccountId,
        dateRangePreset
    ) { values ->
        FilterState(
            tab = values[0] as FinanceSectionTab,
            query = values[1] as String,
            typeFilter = values[2] as TransactionTypeFilter,
            categoryId = values[3] as String?,
            accountId = values[4] as String?,
            datePreset = values[5] as DateRangePreset
        )
    }

    private val uiControlState = combine<Any?, UiControlState>(
        budgetMonthKey,
        activeOverlay,
        showAddCategoryDialog,
        isSubmitting,
        statusMessage,
        errorMessage
    ) { values ->
        UiControlState(
            monthKey = values[0] as String,
            overlay = values[1] as ActiveFinanceOverlay,
            showAddCategory = values[2] as Boolean,
            submitting = values[3] as Boolean,
            status = values[4] as String?,
            error = values[5] as String?
        )
    }

    private val budgetsFlow = budgetMonthKey.flatMapLatest { key ->
        budgetRepository.observeBudgetsWithProgress(key)
    }

    val uiState: StateFlow<FinanceUiState> = combine<Any?, FinanceUiState>(
        filterState,
        accountRepository.activeAccounts,
        categoryRepository.allCategories,
        ledgerRepository.activeTransactions,
        creditCardRepository.creditCards,
        budgetsFlow,
        recurringRuleRepository.activeRules,
        accountRepository.totalLiquidBalance,
        accountRepository.totalCreditCardOutstanding,
        uiControlState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val filters = values[0] as FilterState
        @Suppress("UNCHECKED_CAST")
        val accounts = values[1] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = values[2] as List<com.example.core.database.entity.CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val transactions = values[3] as List<LedgerTransactionWithEntries>
        @Suppress("UNCHECKED_CAST")
        val cards = values[4] as List<com.example.core.data.repository.CreditCardSummary>
        @Suppress("UNCHECKED_CAST")
        val budgets = values[5] as List<com.example.core.data.repository.BudgetProgressItem>
        @Suppress("UNCHECKED_CAST")
        val rules = values[6] as List<com.example.core.database.entity.RecurringRuleEntity>
        val liquidBalance = values[7] as MoneyPaise
        val cardOutstanding = values[8] as MoneyPaise
        val controls = values[9] as UiControlState

        val filtered = applyFilters(transactions, filters)
        FinanceUiState(
            selectedTab = filters.tab,
            searchQuery = filters.query,
            typeFilter = filters.typeFilter,
            selectedCategoryId = filters.categoryId,
            selectedAccountId = filters.accountId,
            dateRangePreset = filters.datePreset,
            budgetMonthKey = controls.monthKey,
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            filteredTransactions = filtered,
            creditCards = cards,
            budgets = budgets,
            recurringRules = rules,
            totalLiquidBalance = liquidBalance,
            totalCardOutstanding = cardOutstanding,
            activeOverlay = controls.overlay,
            showAddCategoryDialog = controls.showAddCategory,
            isSubmitting = controls.submitting,
            statusMessage = controls.status,
            errorMessage = controls.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FinanceUiState()
    )

    fun selectTab(tab: FinanceSectionTab) {
        selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectTypeFilter(filter: TransactionTypeFilter) {
        typeFilter.value = filter
    }

    fun selectCategoryFilter(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    fun selectAccountFilter(accountId: String?) {
        selectedAccountId.value = accountId
    }

    fun selectDateRangePreset(preset: DateRangePreset) {
        dateRangePreset.value = preset
    }

    fun clearAllFilters() {
        searchQuery.value = ""
        typeFilter.value = TransactionTypeFilter.ALL
        selectedCategoryId.value = null
        selectedAccountId.value = null
        dateRangePreset.value = DateRangePreset.ALL
    }

    fun shiftBudgetMonth(delta: Int) {
        budgetMonthKey.value = DateTimeUtils.shiftMonthKey(budgetMonthKey.value, delta)
    }

    fun openTransactionForm(
        kind: TransactionFormKind,
        preselectedAccountId: String? = null,
        preselectedCardId: String? = null
    ) {
        activeOverlay.value = ActiveFinanceOverlay.TransactionForm(
            kind = kind,
            preselectedAccountId = preselectedAccountId,
            preselectedCardId = preselectedCardId
        )
    }

    fun openEditTransaction(tx: LedgerTransactionWithEntries) {
        if (tx.transaction.sourceModule == "SPLIT_BILL") {
            errorMessage.value = "Edit or delete Split Bill entries inside Tools → Split Bill so group balances stay synchronized."
            return
        }
        val kind = when (tx.transaction.txType) {
            LedgerTransactionType.EXPENSE,
            LedgerTransactionType.CREDIT_CARD_EXPENSE,
            LedgerTransactionType.SPLIT_EXPENSE -> TransactionFormKind.EXPENSE
            LedgerTransactionType.INCOME,
            LedgerTransactionType.SPLIT_REIMBURSEMENT -> TransactionFormKind.INCOME
            LedgerTransactionType.TRANSFER -> TransactionFormKind.TRANSFER
            LedgerTransactionType.CREDIT_CARD_PAYMENT -> TransactionFormKind.CARD_PAYMENT
            LedgerTransactionType.SPLIT_SETTLEMENT,
            LedgerTransactionType.OPENING_BALANCE,
            LedgerTransactionType.DEBT_LENT,
            LedgerTransactionType.DEBT_BORROWED,
            LedgerTransactionType.DEBT_REPAYMENT,
            LedgerTransactionType.LOAN_EMI,
            LedgerTransactionType.LOAN_PREPAYMENT,
            LedgerTransactionType.REVERSAL -> {
                errorMessage.value = "System or specialized ledger entries cannot be edited directly here; reverse and re-post if needed."
                return
            }
        }
        activeOverlay.value = ActiveFinanceOverlay.TransactionForm(
            kind = kind,
            editingTransaction = tx
        )
    }

    fun openTransactionDetail(tx: LedgerTransactionWithEntries) {
        activeOverlay.value = ActiveFinanceOverlay.TransactionDetail(tx)
    }

    fun openAccountStatement(account: AccountEntity) {
        activeOverlay.value = ActiveFinanceOverlay.AccountStatement(account)
    }

    fun openAddAccount() {
        activeOverlay.value = ActiveFinanceOverlay.AddAccount
    }

    fun openEditAccount(account: AccountEntity) {
        activeOverlay.value = ActiveFinanceOverlay.EditAccount(account)
    }

    fun openAddCreditCard() {
        activeOverlay.value = ActiveFinanceOverlay.AddCreditCard
    }

    fun openEditCreditCard(card: com.example.core.data.repository.CreditCardSummary) {
        activeOverlay.value = ActiveFinanceOverlay.EditCreditCard(card)
    }

    fun openAddBudget() {
        activeOverlay.value = ActiveFinanceOverlay.BudgetForm(editingBudget = null)
    }

    fun openEditBudget(budget: com.example.core.data.repository.BudgetProgressItem) {
        activeOverlay.value = ActiveFinanceOverlay.BudgetForm(editingBudget = budget)
    }

    fun openAddCategory() {
        showAddCategoryDialog.value = true
    }

    fun dismissAddCategory() {
        showAddCategoryDialog.value = false
    }

    fun dismissOverlay() {
        activeOverlay.value = ActiveFinanceOverlay.None
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    fun observeAccountStatement(accountId: String): Flow<List<AccountStatementEntryRow>> =
        ledgerRepository.observeAccountStatementEntries(accountId)

    fun createAccount(
        name: String,
        type: AccountType,
        openingBalanceInput: String,
        institutionName: String,
        last4: String,
        isDefault: Boolean
    ) {
        val paise = if (openingBalanceInput.isBlank()) {
            0L
        } else {
            MoneyPaise.parseFromRupeeInput(openingBalanceInput)?.paise ?: -1L
        }
        if (name.isBlank() || paise < 0L) {
            errorMessage.value = "Enter a valid account name and non-negative opening balance."
            return
        }
        viewModelScope.launch {
            runCatching {
                ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = name,
                        accountType = type,
                        openingBalancePaise = paise,
                        institutionName = institutionName.ifBlank { null },
                        accountLast4 = last4.ifBlank { null },
                        isDefault = isDefault
                    )
                )
            }.onSuccess {
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Account '${it.name}' created"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to create account"
            }
        }
    }

    fun updateAccount(
        accountId: String,
        name: String,
        institutionName: String,
        last4: String,
        isDefault: Boolean
    ) {
        if (name.isBlank()) {
            errorMessage.value = "Account name cannot be blank."
            return
        }
        viewModelScope.launch {
            runCatching {
                accountRepository.updateAccountDetails(
                    accountId = accountId,
                    name = name,
                    institutionName = institutionName.ifBlank { null },
                    last4 = last4.ifBlank { null },
                    isDefault = isDefault
                )
            }.onSuccess { updated ->
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Account '${updated.name}' updated"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update account"
            }
        }
    }

    fun archiveAccount(accountId: String) {
        viewModelScope.launch {
            runCatching {
                accountRepository.archiveAccount(accountId)
            }.onSuccess {
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Account archived"
            }.onFailure {
                errorMessage.value = it.message ?: "Unable to archive account"
            }
        }
    }

    fun createCreditCard(
        name: String,
        last4: String,
        network: String,
        limitInput: String,
        openingOutstandingInput: String,
        billingCycleDay: Int,
        statementDay: Int,
        dueDay: Int,
        reminderEnabled: Boolean
    ) {
        val limitPaise = MoneyPaise.parseFromRupeeInput(limitInput)?.paise ?: -1L
        val outstandingPaise = if (openingOutstandingInput.isBlank()) {
            0L
        } else {
            MoneyPaise.parseFromRupeeInput(openingOutstandingInput)?.paise ?: -1L
        }
        if (name.isBlank() || limitPaise <= 0L || outstandingPaise < 0L) {
            errorMessage.value = "Enter a valid card name, positive credit limit, and outstanding amount."
            return
        }
        viewModelScope.launch {
            runCatching {
                creditCardRepository.createCreditCard(
                    name = name,
                    last4 = last4.ifBlank { null },
                    network = network.ifBlank { null },
                    creditLimitPaise = limitPaise,
                    openingOutstandingPaise = outstandingPaise,
                    billingCycleDay = billingCycleDay,
                    statementDay = statementDay,
                    dueDay = dueDay,
                    reminderEnabled = reminderEnabled
                )
            }.onSuccess {
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Credit card '$name' added"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to add credit card"
            }
        }
    }

    fun updateCreditCard(
        accountId: String,
        name: String,
        last4: String,
        network: String,
        limitInput: String,
        billingCycleDay: Int,
        statementDay: Int,
        dueDay: Int,
        reminderEnabled: Boolean
    ) {
        val limitPaise = MoneyPaise.parseFromRupeeInput(limitInput)?.paise ?: -1L
        if (name.isBlank() || limitPaise <= 0L) {
            errorMessage.value = "Enter a valid card name and positive credit limit."
            return
        }
        viewModelScope.launch {
            runCatching {
                creditCardRepository.updateCreditCardDetails(
                    accountId = accountId,
                    name = name,
                    last4 = last4.ifBlank { null },
                    network = network.ifBlank { null },
                    creditLimitPaise = limitPaise,
                    billingCycleDay = billingCycleDay,
                    statementDay = statementDay,
                    dueDay = dueDay,
                    reminderEnabled = reminderEnabled
                )
            }.onSuccess {
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Credit card '$name' updated"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update credit card"
            }
        }
    }

    fun submitTransaction(
        kind: TransactionFormKind,
        editingTransactionId: String?,
        amountInput: String,
        description: String,
        notes: String,
        sourceAccountId: String?,
        destinationAccountId: String?,
        categoryId: String?,
        paymentMethod: PaymentMethod,
        timestampEpochMs: Long,
        isRecurring: Boolean,
        recurringFrequency: RecurringFrequency,
        idempotencyKey: String = java.util.UUID.randomUUID().toString()
    ) {
        if (isSubmitting.value) return
        val amount = MoneyPaise.parseFromRupeeInput(amountInput)
        if (amount == null || amount.paise <= 0L) {
            errorMessage.value = "Enter a valid positive amount in ₹."
            return
        }
        val cleanDesc = description.trim().ifBlank {
            when (kind) {
                TransactionFormKind.EXPENSE -> "Expense"
                TransactionFormKind.INCOME -> "Income"
                TransactionFormKind.TRANSFER -> "Account Transfer"
                TransactionFormKind.CARD_PAYMENT -> "Credit Card Payment"
            }
        }

        val accountsMap = uiState.value.accounts.associateBy { it.id }

        val spec: LedgerPostSpec? = when (kind) {
            TransactionFormKind.EXPENSE -> {
                val acc = sourceAccountId?.let { accountsMap[it] }
                if (acc == null) {
                    errorMessage.value = "Select or create an account first."
                    return
                }
                if (acc.accountType == AccountType.CREDIT_CARD) {
                    LedgerPostSpec.CreditCardExpense(
                        cardAccountId = acc.id,
                        amountPaise = amount.paise,
                        categoryId = categoryId,
                        description = cleanDesc,
                        notes = notes.ifBlank { null },
                        timestampEpochMs = timestampEpochMs,
                        idempotencyKey = idempotencyKey
                    )
                } else {
                    LedgerPostSpec.Expense(
                        accountId = acc.id,
                        amountPaise = amount.paise,
                        categoryId = categoryId,
                        paymentMethod = paymentMethod,
                        description = cleanDesc,
                        notes = notes.ifBlank { null },
                        timestampEpochMs = timestampEpochMs,
                        idempotencyKey = idempotencyKey
                    )
                }
            }

            TransactionFormKind.INCOME -> {
                val acc = destinationAccountId?.let { accountsMap[it] }
                if (acc == null || acc.accountType == AccountType.CREDIT_CARD) {
                    errorMessage.value = "Select a Bank, Cash, or Savings destination account."
                    return
                }
                LedgerPostSpec.Income(
                    destinationAccountId = acc.id,
                    amountPaise = amount.paise,
                    categoryId = categoryId,
                    description = cleanDesc,
                    notes = notes.ifBlank { null },
                    timestampEpochMs = timestampEpochMs,
                    idempotencyKey = idempotencyKey
                )
            }

            TransactionFormKind.TRANSFER -> {
                if (sourceAccountId == null || destinationAccountId == null || sourceAccountId == destinationAccountId) {
                    errorMessage.value = "Choose two distinct accounts for transfer."
                    return
                }
                LedgerPostSpec.Transfer(
                    fromAccountId = sourceAccountId,
                    toAccountId = destinationAccountId,
                    amountPaise = amount.paise,
                    description = cleanDesc,
                    notes = notes.ifBlank { null },
                    timestampEpochMs = timestampEpochMs,
                    idempotencyKey = idempotencyKey
                )
            }

            TransactionFormKind.CARD_PAYMENT -> {
                if (sourceAccountId == null || destinationAccountId == null) {
                    errorMessage.value = "Select both paying bank/cash account and credit card."
                    return
                }
                LedgerPostSpec.CreditCardPayment(
                    fromAssetAccountId = sourceAccountId,
                    cardAccountId = destinationAccountId,
                    amountPaise = amount.paise,
                    description = cleanDesc,
                    notes = notes.ifBlank { null },
                    timestampEpochMs = timestampEpochMs,
                    idempotencyKey = idempotencyKey
                )
            }
        }

        if (spec == null) return

        viewModelScope.launch {
            isSubmitting.value = true
            try {
                runCatching {
                    if (editingTransactionId != null) {
                        val existingTx = ledgerRepository.getTransactionWithEntries(editingTransactionId)
                        if (existingTx?.transaction?.txType == LedgerTransactionType.CREDIT_CARD_PAYMENT &&
                            spec is LedgerPostSpec.CreditCardPayment
                        ) {
                            val oldCardId = existingTx.entries.firstOrNull {
                                it.entrySide == com.example.core.database.entity.EntrySide.DEBIT && it.accountId != null
                            }?.accountId
                            creditCardRepository.editCardPayment(
                                originalTransactionId = editingTransactionId,
                                oldCardAccountId = oldCardId,
                                oldAmountPaise = existingTx.transaction.totalAmountPaise,
                                updatedSpec = spec
                            )
                        } else {
                            ledgerRepository.editTransaction(editingTransactionId, spec)
                        }
                    } else {
                        if (kind == TransactionFormKind.CARD_PAYMENT &&
                            sourceAccountId != null &&
                            destinationAccountId != null
                        ) {
                            creditCardRepository.payCreditCard(
                                fromAssetAccountId = sourceAccountId,
                                cardAccountId = destinationAccountId,
                                amountPaise = amount.paise,
                                description = cleanDesc,
                                notes = notes.ifBlank { null },
                                timestampEpochMs = timestampEpochMs,
                                idempotencyKey = idempotencyKey
                            )
                        } else {
                            ledgerRepository.postTransaction(spec)
                        }
                        if (isRecurring && (kind == TransactionFormKind.EXPENSE || kind == TransactionFormKind.INCOME)) {
                            val ruleAccId = if (kind == TransactionFormKind.EXPENSE) sourceAccountId else destinationAccountId
                            if (ruleAccId != null) {
                                recurringRuleRepository.createRule(
                                    txType = if (kind == TransactionFormKind.INCOME) {
                                        LedgerTransactionType.INCOME
                                    } else {
                                        LedgerTransactionType.EXPENSE
                                    },
                                    amountPaise = amount.paise,
                                    accountId = ruleAccId,
                                    categoryId = categoryId,
                                    paymentMethod = paymentMethod,
                                    description = cleanDesc,
                                    notes = notes.ifBlank { null },
                                    frequency = recurringFrequency,
                                    firstRunEpochMs = timestampEpochMs
                                )
                            }
                        }
                    }
                }.onSuccess {
                    activeOverlay.value = ActiveFinanceOverlay.None
                    statusMessage.value = if (editingTransactionId != null) {
                        "Transaction updated in ledger"
                    } else {
                        "Posted ${amount.formatInr()} to ledger"
                    }
                }.onFailure {
                    errorMessage.value = it.message ?: "Ledger transaction failed"
                }
            } finally {
                isSubmitting.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            runCatching {
                val existing = ledgerRepository.getTransactionWithEntries(transactionId)
                if (existing?.transaction?.txType == LedgerTransactionType.CREDIT_CARD_PAYMENT) {
                    val cardId = existing.entries.firstOrNull {
                        it.entrySide == com.example.core.database.entity.EntrySide.DEBIT && it.accountId != null
                    }?.accountId
                    creditCardRepository.reverseCardPayment(
                        transactionId = transactionId,
                        cardAccountId = cardId,
                        amountPaise = existing.transaction.totalAmountPaise
                    )
                } else {
                    ledgerRepository.reverseOrDeleteTransaction(transactionId)
                }
            }.onSuccess {
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Transaction reversed and balances restored"
            }.onFailure {
                errorMessage.value = it.message ?: "Could not delete transaction"
            }
        }
    }

    fun toggleCardReminder(accountId: String, enabled: Boolean) {
        viewModelScope.launch {
            creditCardRepository.toggleReminder(accountId, enabled)
        }
    }

    fun generateCardStatement(accountId: String) {
        viewModelScope.launch {
            runCatching {
                creditCardRepository.generateStatementSnapshot(accountId)
            }.onSuccess {
                statusMessage.value = "Billing statement snapshot saved"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to create statement"
            }
        }
    }

    fun upsertBudget(categoryId: String?, limitInput: String, alertThresholdPct: Int) {
        val limitPaise = MoneyPaise.parseFromRupeeInput(limitInput)?.paise ?: -1L
        if (limitPaise <= 0L) {
            errorMessage.value = "Enter a positive budget limit in ₹."
            return
        }
        viewModelScope.launch {
            runCatching {
                budgetRepository.upsertBudget(
                    monthKey = budgetMonthKey.value,
                    categoryId = categoryId,
                    limitPaise = limitPaise,
                    alertThresholdPct = alertThresholdPct
                )
            }.onSuccess {
                activeOverlay.value = ActiveFinanceOverlay.None
                statusMessage.value = "Budget saved for ${DateTimeUtils.formatMonthKeyLabel(budgetMonthKey.value)}"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to save budget"
            }
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(budgetId)
            statusMessage.value = "Budget removed"
        }
    }

    fun postRecurringRule(ruleId: String) {
        viewModelScope.launch {
            runCatching {
                recurringRuleRepository.postNextOccurrence(ruleId)
            }.onSuccess {
                statusMessage.value = "Recurring entry posted to ledger"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to post recurring entry"
            }
        }
    }

    fun deleteRecurringRule(ruleId: String) {
        viewModelScope.launch {
            recurringRuleRepository.deleteRule(ruleId)
            statusMessage.value = "Recurring schedule removed"
        }
    }

    fun createCategory(name: String, type: CategoryType, parentId: String?) {
        if (name.isBlank()) {
            errorMessage.value = "Category name cannot be blank."
            return
        }
        viewModelScope.launch {
            runCatching {
                categoryRepository.createCategory(name = name, type = type, parentId = parentId)
            }.onSuccess {
                showAddCategoryDialog.value = false
                statusMessage.value = "Category '${it.name}' added"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to add category"
            }
        }
    }

    private fun applyFilters(
        items: List<LedgerTransactionWithEntries>,
        filters: FilterState
    ): List<LedgerTransactionWithEntries> {
        val now = DateTimeUtils.nowEpochMs()
        val (monthStart, monthEnd) = DateTimeUtils.currentMonthRange(now)
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val thirtyDaysAgo = now - 30L * 24L * 60L * 60L * 1000L

        return items.filter { item ->
            val tx = item.transaction
            val matchesQuery = filters.query.isBlank() ||
                tx.description.contains(filters.query, ignoreCase = true) ||
                (tx.notes?.contains(filters.query, ignoreCase = true) == true)

            val matchesType = when (filters.typeFilter) {
                TransactionTypeFilter.ALL -> true
                TransactionTypeFilter.EXPENSE -> tx.txType == LedgerTransactionType.EXPENSE ||
                    tx.txType == LedgerTransactionType.SPLIT_EXPENSE
                TransactionTypeFilter.INCOME -> tx.txType == LedgerTransactionType.INCOME ||
                    tx.txType == LedgerTransactionType.SPLIT_REIMBURSEMENT
                TransactionTypeFilter.TRANSFER -> tx.txType == LedgerTransactionType.TRANSFER
                TransactionTypeFilter.CARD -> tx.txType == LedgerTransactionType.CREDIT_CARD_EXPENSE ||
                    tx.txType == LedgerTransactionType.CREDIT_CARD_PAYMENT
            }

            val matchesCategory = filters.categoryId == null || tx.categoryId == filters.categoryId

            val matchesAccount = filters.accountId == null ||
                item.entries.any { it.accountId == filters.accountId }

            val matchesDate = when (filters.datePreset) {
                DateRangePreset.ALL -> true
                DateRangePreset.TODAY -> tx.txTimestampEpochMs >= todayStart
                DateRangePreset.THIS_MONTH -> tx.txTimestampEpochMs in monthStart..monthEnd
                DateRangePreset.LAST_30_DAYS -> tx.txTimestampEpochMs >= thirtyDaysAgo
            }

            matchesQuery && matchesType && matchesCategory && matchesAccount && matchesDate
        }
    }

    companion object {
        fun factory(
            accountRepository: AccountRepository,
            categoryRepository: CategoryRepository,
            ledgerRepository: LedgerRepository,
            creditCardRepository: CreditCardRepository,
            budgetRepository: BudgetRepository,
            recurringRuleRepository: RecurringRuleRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FinanceViewModel(
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    ledgerRepository = ledgerRepository,
                    creditCardRepository = creditCardRepository,
                    budgetRepository = budgetRepository,
                    recurringRuleRepository = recurringRuleRepository
                ) as T
            }
        }
    }
}
