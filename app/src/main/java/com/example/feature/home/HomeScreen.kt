package com.example.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.data.preferences.HomeDashboardWidget
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultEmptyStateCard
import com.example.core.designsystem.component.FinVaultHeroCard
import com.example.core.designsystem.component.FinVaultMetricTile
import com.example.core.designsystem.component.FinVaultSectionHeader
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.LocalFinVaultColors
import com.example.core.designsystem.theme.PureWhite
import com.example.feature.finance.FinanceSectionTab
import com.example.feature.finance.TransactionFormKind
import com.example.feature.finance.TransactionRowCard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onQuickTransaction: (TransactionFormKind) -> Unit = {},
    onOpenFinanceTab: (FinanceSectionTab) -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenSplitBill: () -> Unit = {},
    onOpenDebtManagement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalFinVaultColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    when (val state = uiState) {
        HomeUiState.Loading -> {
            Column(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("home_loading"))
            }
        }

        is HomeUiState.Ready -> {
            val accountMap = remember(state.accounts) { state.accounts.associateBy { it.id } }
            val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

            LaunchedEffect(state.statusMessage, state.errorMessage) {
                val msg = state.errorMessage ?: state.statusMessage
                if (msg != null) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearMessages()
                }
            }

            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_screen_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.home_greeting),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FinVaultStatusPill(
                                    text = stringResource(R.string.phase_badge_format, 5)
                                )
                                IconButton(
                                    onClick = viewModel::openCustomizeDialog,
                                    modifier = Modifier.testTag("home_customize_widgets_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = "Customize dashboard widgets",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }

                    item {
                        FinVaultHeroCard(
                            title = stringResource(R.string.home_total_balance),
                            amountFormatted = state.totalAccountBalance.formatInr(),
                            subtitle = "${state.currentMonthLabel} • ${stringResource(R.string.currency_inr_label)}",
                            badgeText = stringResource(R.string.db_version_label, state.databaseVersion)
                        ) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = PureWhite.copy(alpha = 0.16f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.home_available_balance),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PureWhite.copy(alpha = 0.78f)
                                )
                                Text(
                                    text = state.availableBalance.formatInr(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PureWhite,
                                    modifier = Modifier.testTag("home_available_balance_amount")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Net Worth (After Card Debt)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureWhite.copy(alpha = 0.72f)
                                )
                                Text(
                                    text = state.netWorth.formatInr(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PureWhite,
                                    modifier = Modifier.testTag("home_net_worth_amount")
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
                                onClick = { onQuickTransaction(TransactionFormKind.EXPENSE) },
                                modifier = Modifier.testTag("home_quick_expense")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text(stringResource(R.string.finance_action_add_expense))
                            }
                            FilledTonalButton(
                                onClick = { onQuickTransaction(TransactionFormKind.INCOME) },
                                modifier = Modifier.testTag("home_quick_income")
                            ) {
                                Text(stringResource(R.string.finance_action_add_income))
                            }
                            OutlinedButton(
                                onClick = { onQuickTransaction(TransactionFormKind.TRANSFER) },
                                modifier = Modifier.testTag("home_quick_transfer")
                            ) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                                Text(stringResource(R.string.finance_action_transfer))
                            }
                            OutlinedButton(
                                onClick = { onOpenFinanceTab(FinanceSectionTab.ACCOUNTS) },
                                modifier = Modifier.testTag("home_quick_accounts")
                            ) {
                                Text(stringResource(R.string.finance_tab_accounts))
                            }
                            OutlinedButton(
                                onClick = { onOpenFinanceTab(FinanceSectionTab.CARDS) },
                                modifier = Modifier.testTag("home_quick_cards")
                            ) {
                                Text(stringResource(R.string.finance_tab_cards))
                            }
                            OutlinedButton(
                                onClick = { onOpenFinanceTab(FinanceSectionTab.BUDGETS) },
                                modifier = Modifier.testTag("home_quick_budgets")
                            ) {
                                Text(stringResource(R.string.finance_tab_budgets))
                            }
                            OutlinedButton(
                                onClick = onOpenSplitBill,
                                modifier = Modifier.testTag("home_quick_split_bill")
                            ) {
                                Text("Split Bill")
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FinVaultMetricTile(
                                label = stringResource(R.string.home_monthly_income),
                                value = state.monthlyIncome.formatInr(),
                                icon = Icons.Filled.ArrowDownward,
                                tint = semantic.income,
                                containerColor = semantic.incomeContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_monthly_income_tile")
                            )
                            FinVaultMetricTile(
                                label = stringResource(R.string.home_monthly_expenses),
                                value = state.monthlyExpenses.formatInr(),
                                icon = Icons.Filled.ArrowUpward,
                                tint = semantic.expense,
                                containerColor = semantic.expenseContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_monthly_expenses_tile")
                            )
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.MONTHLY_SUMMARY)) {
                        item(key = "widget_monthly_summary") {
                            MonthlyFinancialSummaryCard(
                                monthLabel = state.currentMonthLabel,
                                monthlyIncome = state.monthlyIncome,
                                monthlyExpenses = state.monthlyExpenses,
                                monthlyNetCashFlow = state.monthlyNetCashFlow,
                                savingsRatePercent = state.savingsRatePercent,
                                activeAccountCount = state.activeAccountCount,
                                onOpenReports = onOpenReports
                            )
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.INCOME_VS_EXPENSE_CHART)) {
                        item(key = "widget_income_expense_chart") {
                            IncomeExpenseComparisonChartCard(
                                bars = state.cashFlowTrend,
                                monthlyNet = state.monthlyNetCashFlow
                            )
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.CATEGORY_CHART)) {
                        item(key = "widget_category_chart") {
                            CategoryDonutChartCard(
                                slices = state.categoryBreakdown,
                                totalMonthlyExpense = state.monthlyExpenses
                            )
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.RECENT_TRANSACTIONS)) {
                        item(key = "widget_recent_tx_header") {
                            FinVaultSectionHeader(
                                title = stringResource(R.string.home_recent_transactions_title),
                                trailingText = if (state.recentTransactions.isEmpty()) {
                                    "0 posted"
                                } else {
                                    "Latest ${state.recentTransactions.size}"
                                }
                            )
                        }

                        if (state.recentTransactions.isEmpty()) {
                            item(key = "widget_recent_tx_empty") {
                                FinVaultEmptyStateCard(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    title = stringResource(R.string.home_recent_transactions_empty_title),
                                    body = stringResource(R.string.home_recent_transactions_empty_body)
                                )
                            }
                        } else {
                            items(
                                items = state.recentTransactions,
                                key = { "home_tx_${it.transaction.id}" }
                            ) { txItem ->
                                TransactionRowCard(
                                    item = txItem,
                                    categoryMap = categoryMap,
                                    accountMap = accountMap,
                                    onClick = { onOpenFinanceTab(FinanceSectionTab.EXPENSES) }
                                )
                            }
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.BUDGET_PROGRESS)) {
                        item(key = "widget_budget_header") {
                            FinVaultSectionHeader(
                                title = "Budget Progress",
                                trailingText = if (state.budgetProgress.isEmpty()) {
                                    "None set"
                                } else {
                                    "${state.budgetProgress.size} active"
                                }
                            )
                        }

                        if (state.budgetProgress.isEmpty()) {
                            item(key = "widget_budget_empty") {
                                FinVaultEmptyStateCard(
                                    icon = Icons.Filled.PieChart,
                                    title = "No Monthly Budgets Set",
                                    body = "Set overall or category limits in Finance → Budgets to monitor real-time spending thresholds here."
                                )
                            }
                        } else {
                            items(
                                items = state.budgetProgress.take(4),
                                key = { "home_budget_${it.id}" }
                            ) { budget ->
                                val progress = (budget.usagePercentage / 100f).coerceIn(0f, 1f)
                                val barColor = when {
                                    budget.isOverBudget -> semantic.expense
                                    budget.isAlertTriggered -> semantic.warning
                                    else -> semantic.income
                                }
                                FinVaultCard(modifier = Modifier.testTag("home_budget_card_${budget.id}")) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = budget.categoryName,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        FinVaultStatusPill(
                                            text = "${budget.usagePercentage}%",
                                            containerColor = barColor.copy(alpha = 0.14f),
                                            contentColor = barColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = barColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Spent ${budget.spent.formatInr()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Limit ${budget.limit.formatInr()}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.EMI_AND_CARDS)) {
                        item(key = "widget_emi_and_cards") {
                            EmiAndCardRemindersCard(
                                creditCardOutstanding = state.totalCreditCardOutstanding,
                                cardCount = state.creditCards.size,
                                loanEmiSummary = state.loanEmiSummary,
                                upcomingReminders = state.upcomingReminders,
                                onPostRecurringRule = viewModel::postRecurringReminder,
                                onPayCreditCard = { onQuickTransaction(TransactionFormKind.CARD_PAYMENT) },
                                onOpenCardsTab = { onOpenFinanceTab(FinanceSectionTab.CARDS) }
                            )
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.DEBTS_SUMMARY)) {
                        item(key = "widget_debts_summary") {
                            DebtsSummaryCard(summary = state.debtSummary, onOpenDebtManagement = onOpenDebtManagement)
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.SPLIT_BILL_SUMMARY)) {
                        item(key = "widget_split_bill_summary") {
                            SplitBillSummaryCard(
                                summary = state.splitBillSummary,
                                portfolio = state.splitBillPortfolio,
                                onOpenSplitBill = onOpenSplitBill
                            )
                        }
                    }

                    if (state.isWidgetVisible(HomeDashboardWidget.SAVINGS_OVERVIEW)) {
                        item(key = "widget_savings_overview") {
                            SavingsAndAccountBreakdownCard(
                                bankBalance = state.bankBalance,
                                cashBalance = state.cashBalance,
                                savingsBalance = state.savingsBalance,
                                onOpenAccounts = { onOpenFinanceTab(FinanceSectionTab.ACCOUNTS) }
                            )
                        }
                    }

                    item(key = "home_ledger_status_footer") {
                        FinVaultEmptyStateCard(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.home_ledger_status_title),
                            body = stringResource(R.string.home_ledger_status_body),
                            badgeLabel = "${
                                stringResource(R.string.home_active_accounts_count, state.activeAccountCount)
                            } • ${
                                stringResource(R.string.home_categories_seeded_count, state.seededCategoryCount)
                            }"
                        )
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            if (state.isCustomizeDialogOpen) {
                CustomizeHomeWidgetsDialog(
                    hiddenWidgets = state.hiddenWidgets,
                    onToggleWidget = viewModel::toggleWidgetVisibility,
                    onResetDefault = viewModel::resetWidgetsToDefault,
                    onDismiss = viewModel::dismissCustomizeDialog
                )
            }
        }
    }
}
