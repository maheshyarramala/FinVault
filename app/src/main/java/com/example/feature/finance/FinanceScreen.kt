package com.example.feature.finance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultEmptyStateCard
import com.example.core.designsystem.component.FinVaultSectionHeader
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val semantic = LocalFinVaultColors.current

    val accountMap = remember(uiState.accounts) { uiState.accounts.associateBy { it.id } }
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val message = uiState.errorMessage ?: uiState.statusMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("finance_screen_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val netWorth = MoneyPaise(
                    uiState.totalLiquidBalance.paise - uiState.totalCardOutstanding.paise
                )
                Column {
                    Text(
                        text = stringResource(R.string.finance_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Liquid ${uiState.totalLiquidBalance.formatInr()} • Card Debt ${uiState.totalCardOutstanding.formatInr()} • Net ${netWorth.formatInr()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                val tabOptions = listOf(
                    FinanceSectionTab.EXPENSES_AND_ACCOUNTS to "${stringResource(R.string.finance_tab_expenses)} & ${stringResource(R.string.finance_tab_accounts)}",
                    FinanceSectionTab.EXPENSES to stringResource(R.string.finance_tab_expenses),
                    FinanceSectionTab.ACCOUNTS to stringResource(R.string.finance_tab_accounts),
                    FinanceSectionTab.CARDS to stringResource(R.string.finance_tab_cards),
                    FinanceSectionTab.BUDGETS to stringResource(R.string.finance_tab_budgets)
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("finance_segment_switcher"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabOptions.forEach { (tab, label) ->
                        FilterChip(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("finance_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.openTransactionForm(TransactionFormKind.EXPENSE) },
                        modifier = Modifier.testTag("finance_btn_add_expense")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.finance_action_add_expense))
                    }
                    FilledTonalButton(
                        onClick = { viewModel.openTransactionForm(TransactionFormKind.INCOME) },
                        modifier = Modifier.testTag("finance_btn_add_income")
                    ) {
                        Text(stringResource(R.string.finance_action_add_income))
                    }
                    OutlinedButton(
                        onClick = { viewModel.openTransactionForm(TransactionFormKind.TRANSFER) },
                        modifier = Modifier.testTag("finance_btn_transfer")
                    ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                        Text(stringResource(R.string.finance_action_transfer))
                    }
                    OutlinedButton(
                        onClick = { viewModel.openAddAccount() },
                        modifier = Modifier.testTag("finance_btn_add_account")
                    ) {
                        Text(stringResource(R.string.finance_action_add_account))
                    }
                    OutlinedButton(
                        onClick = { viewModel.openAddCreditCard() },
                        modifier = Modifier.testTag("finance_btn_add_card")
                    ) {
                        Text(stringResource(R.string.finance_action_add_card))
                    }
                    OutlinedButton(
                        onClick = { viewModel.openAddBudget() },
                        modifier = Modifier.testTag("finance_btn_add_budget")
                    ) {
                        Text(stringResource(R.string.finance_action_add_budget))
                    }
                }
            }

            if (uiState.selectedTab == FinanceSectionTab.EXPENSES_AND_ACCOUNTS ||
                uiState.selectedTab == FinanceSectionTab.ACCOUNTS
            ) {
                item {
                    FinVaultSectionHeader(
                        title = stringResource(R.string.finance_tab_accounts),
                        trailingText = "${uiState.accounts.size} active"
                    )
                }

                if (uiState.accounts.isEmpty()) {
                    item {
                        FinVaultEmptyStateCard(
                            icon = Icons.Filled.AccountBalance,
                            title = stringResource(R.string.finance_accounts_empty_title),
                            body = stringResource(R.string.finance_accounts_empty_body)
                        )
                    }
                } else {
                    items(
                        items = uiState.accounts,
                        key = { "acc_${it.id}" }
                    ) { account ->
                        AccountRowCard(
                            account = account,
                            onClick = { viewModel.openAccountStatement(account) }
                        )
                    }
                }
            }

            if (uiState.selectedTab == FinanceSectionTab.EXPENSES_AND_ACCOUNTS ||
                uiState.selectedTab == FinanceSectionTab.EXPENSES
            ) {
                if (uiState.recurringRules.isNotEmpty()) {
                    item {
                        FinVaultSectionHeader(
                            title = "Recurring Schedules",
                            trailingText = "${uiState.recurringRules.size} active"
                        )
                    }
                    item {
                        RecurringRulesStrip(
                            rules = uiState.recurringRules,
                            onPostNow = { viewModel.postRecurringRule(it) },
                            onDeleteRule = { viewModel.deleteRecurringRule(it) }
                        )
                    }
                }

                item {
                    FinVaultSectionHeader(
                        title = "Transactions",
                        trailingText = "${uiState.filteredTransactions.size} of ${uiState.transactions.size}"
                    )
                }

                item {
                    ExpensesFilterBar(
                        searchQuery = uiState.searchQuery,
                        typeFilter = uiState.typeFilter,
                        datePreset = uiState.dateRangePreset,
                        selectedCategoryId = uiState.selectedCategoryId,
                        selectedAccountId = uiState.selectedAccountId,
                        accounts = uiState.accounts,
                        categories = uiState.categories,
                        hasActiveFilters = uiState.hasActiveTransactionFilters,
                        onSearchChange = viewModel::updateSearchQuery,
                        onSelectTypeFilter = viewModel::selectTypeFilter,
                        onSelectDatePreset = viewModel::selectDateRangePreset,
                        onSelectCategoryId = viewModel::selectCategoryFilter,
                        onSelectAccountId = viewModel::selectAccountFilter,
                        onClearFilters = viewModel::clearAllFilters
                    )
                }

                if (uiState.filteredTransactions.isEmpty()) {
                    item {
                        FinVaultEmptyStateCard(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = if (uiState.hasActiveTransactionFilters) {
                                "No matching transactions"
                            } else {
                                stringResource(R.string.finance_expenses_empty_title)
                            },
                            body = if (uiState.hasActiveTransactionFilters) {
                                "No transactions match the active search or filters. Clear filters to view all ledger entries."
                            } else {
                                stringResource(R.string.finance_expenses_empty_body)
                            },
                            actionLabel = if (uiState.hasActiveTransactionFilters) "Clear All Filters" else null,
                            onAction = if (uiState.hasActiveTransactionFilters) viewModel::clearAllFilters else null
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredTransactions,
                        key = { "tx_${it.transaction.id}" }
                    ) { txItem ->
                        TransactionRowCard(
                            item = txItem,
                            categoryMap = categoryMap,
                            accountMap = accountMap,
                            onClick = { viewModel.openTransactionDetail(txItem) }
                        )
                    }
                }
            }

            if (uiState.selectedTab == FinanceSectionTab.CARDS) {
                item {
                    FinVaultCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Credit Card Outstanding",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.totalCardOutstanding.formatInr(),
                                    style = FinVaultNumericTypography.metricAmount,
                                    color = semantic.expense
                                )
                            }
                            Button(onClick = { viewModel.openAddCreditCard() }) {
                                Text(stringResource(R.string.finance_action_add_card))
                            }
                        }
                    }
                }

                if (uiState.creditCards.isEmpty()) {
                    item {
                        FinVaultEmptyStateCard(
                            icon = Icons.Filled.CreditCard,
                            title = stringResource(R.string.finance_cards_empty_title),
                            body = stringResource(R.string.finance_cards_empty_body)
                        )
                    }
                } else {
                    items(
                        items = uiState.creditCards,
                        key = { "card_${it.accountId}" }
                    ) { card ->
                        CreditCardDetailCard(
                            card = card,
                            onToggleReminder = { enabled -> viewModel.toggleCardReminder(card.accountId, enabled) },
                            onPayCard = {
                                viewModel.openTransactionForm(
                                    kind = TransactionFormKind.CARD_PAYMENT,
                                    preselectedCardId = card.accountId
                                )
                            },
                            onSpendOnCard = {
                                viewModel.openTransactionForm(
                                    kind = TransactionFormKind.EXPENSE,
                                    preselectedAccountId = card.accountId
                                )
                            },
                            onSnapshotStatement = { viewModel.generateCardStatement(card.accountId) },
                            onEditCard = { viewModel.openEditCreditCard(card) }
                        )
                    }
                }
            }

            if (uiState.selectedTab == FinanceSectionTab.BUDGETS) {
                item {
                    FinVaultCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.shiftBudgetMonth(-1) },
                                modifier = Modifier.testTag("budget_prev_month")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                            }
                            Text(
                                text = DateTimeUtils.formatMonthKeyLabel(uiState.budgetMonthKey),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = { viewModel.shiftBudgetMonth(1) },
                                modifier = Modifier.testTag("budget_next_month")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                            }
                        }
                    }
                }

                if (uiState.budgets.isEmpty()) {
                    item {
                        FinVaultEmptyStateCard(
                            icon = Icons.Filled.PieChart,
                            title = stringResource(R.string.finance_budgets_empty_title),
                            body = stringResource(R.string.finance_budgets_empty_body)
                        )
                    }
                } else {
                    items(
                        items = uiState.budgets,
                        key = { "budget_${it.id}" }
                    ) { budget ->
                        BudgetProgressCard(
                            item = budget,
                            onEdit = { viewModel.openEditBudget(budget) },
                            onDelete = { viewModel.deleteBudget(budget.id) }
                        )
                    }
                }
            }

            if (uiState.selectedTab == FinanceSectionTab.EXPENSES_AND_ACCOUNTS ||
                uiState.selectedTab == FinanceSectionTab.ACCOUNTS
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FinVaultSectionHeader(
                            title = stringResource(R.string.finance_seeded_categories_header),
                            trailingText = "${uiState.categories.size}",
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { viewModel.openAddCategory() },
                            modifier = Modifier.testTag("finance_btn_add_category")
                        ) {
                            Text("+ Category")
                        }
                    }
                }
                items(
                    items = uiState.categories,
                    key = { "cat_${it.id}" }
                ) { category ->
                    CategorySummaryRow(category = category)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    when (val overlay = uiState.activeOverlay) {
        ActiveFinanceOverlay.None -> Unit

        is ActiveFinanceOverlay.TransactionForm -> {
            TransactionFormSheet(
                kind = overlay.kind,
                editingTransaction = overlay.editingTransaction,
                preselectedAccountId = overlay.preselectedAccountId,
                preselectedCardId = overlay.preselectedCardId,
                accounts = uiState.accounts,
                categories = uiState.categories,
                defaultExpensePaymentMethod = uiState.defaultExpensePaymentMethod,
                isSubmitting = uiState.isSubmitting,
                onDismiss = viewModel::dismissOverlay,
                onOpenAddCategory = viewModel::openAddCategory,
                onSubmit = viewModel::submitTransaction
            )
        }

        is ActiveFinanceOverlay.TransactionDetail -> {
            TransactionDetailSheet(
                item = overlay.transaction,
                accounts = uiState.accounts,
                categories = uiState.categories,
                onDismiss = viewModel::dismissOverlay,
                onEdit = viewModel::openEditTransaction,
                onDelete = viewModel::deleteTransaction
            )
        }

        is ActiveFinanceOverlay.AccountStatement -> {
            AccountStatementSheet(
                account = overlay.account,
                entriesFlow = remember(overlay.account.id) {
                    viewModel.observeAccountStatement(overlay.account.id)
                },
                onDismiss = viewModel::dismissOverlay,
                onEdit = { viewModel.openEditAccount(overlay.account) },
                onArchive = { viewModel.archiveAccount(overlay.account.id) }
            )
        }

        ActiveFinanceOverlay.AddAccount -> {
            AddAccountDialog(
                editingAccount = null,
                onDismiss = viewModel::dismissOverlay,
                onCreate = viewModel::createAccount
            )
        }

        is ActiveFinanceOverlay.EditAccount -> {
            AddAccountDialog(
                editingAccount = overlay.account,
                onDismiss = viewModel::dismissOverlay,
                onCreate = viewModel::createAccount,
                onUpdate = viewModel::updateAccount
            )
        }

        ActiveFinanceOverlay.AddCreditCard -> {
            AddCreditCardDialog(
                editingCard = null,
                onDismiss = viewModel::dismissOverlay,
                onCreate = viewModel::createCreditCard
            )
        }

        is ActiveFinanceOverlay.EditCreditCard -> {
            AddCreditCardDialog(
                editingCard = overlay.card,
                onDismiss = viewModel::dismissOverlay,
                onCreate = viewModel::createCreditCard,
                onUpdate = viewModel::updateCreditCard
            )
        }

        is ActiveFinanceOverlay.BudgetForm -> {
            BudgetFormDialog(
                editingBudget = overlay.editingBudget,
                categories = uiState.categories,
                monthKey = uiState.budgetMonthKey,
                onDismiss = viewModel::dismissOverlay,
                onSave = viewModel::upsertBudget
            )
        }
    }

    if (uiState.showAddCategoryDialog) {
        CreateCategoryDialog(
            categories = uiState.categories,
            onDismiss = viewModel::dismissAddCategory,
            onSave = viewModel::createCategory
        )
    }
}
