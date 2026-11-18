package com.example.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.MoneyPaise
import com.example.core.data.preferences.HomeDashboardWidget
import com.example.core.data.repository.DebtLedgerSummary
import com.example.core.data.repository.LoanEmiLedgerSummary
import com.example.core.data.repository.SplitBillLedgerSummary
import com.example.core.data.repository.SplitBillPortfolioSummary
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultInfoRow
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors
import com.example.feature.finance.FinanceSectionTab
import com.example.feature.finance.TransactionFormKind

@Composable
fun MonthlyFinancialSummaryCard(
    monthLabel: String,
    monthlyIncome: MoneyPaise,
    monthlyExpenses: MoneyPaise,
    monthlyNetCashFlow: MoneyPaise,
    savingsRatePercent: String,
    activeAccountCount: Int,
    onOpenReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinVaultColors.current
    val isSurplus = monthlyNetCashFlow.paise >= 0L

    FinVaultCard(modifier = modifier.testTag("home_monthly_summary_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Monthly Financial Summary",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$monthLabel • $activeAccountCount active accounts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FinVaultStatusPill(
                text = "Save $savingsRatePercent",
                containerColor = if (isSurplus) semantic.incomeContainer else semantic.expenseContainer,
                contentColor = if (isSurplus) semantic.income else semantic.expense
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = semantic.cardBorder)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Inflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = monthlyIncome.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.income
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Outflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = monthlyExpenses.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.expense
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Net Surplus",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = monthlyNetCashFlow.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = if (isSurplus) semantic.income else semantic.expense
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onOpenReports,
                modifier = Modifier.testTag("home_open_reports_btn")
            ) {
                Text("View Analytics")
            }
        }
    }
}

@Composable
fun SavingsAndAccountBreakdownCard(
    bankBalance: MoneyPaise,
    cashBalance: MoneyPaise,
    savingsBalance: MoneyPaise,
    onOpenAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinVaultColors.current
    FinVaultCard(modifier = modifier.testTag("home_savings_overview_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Savings & Accounts Overview",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = onOpenAccounts,
                modifier = Modifier.testTag("home_manage_accounts_btn")
            ) {
                Text("Accounts")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        FinVaultInfoRow(
            icon = Icons.Filled.AccountBalance,
            label = "Bank Accounts",
            value = bankBalance.formatInr(),
            iconTint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        FinVaultInfoRow(
            icon = Icons.Filled.AccountBalanceWallet,
            label = "Cash in Hand",
            value = cashBalance.formatInr(),
            iconTint = semantic.warning
        )
        Spacer(modifier = Modifier.height(8.dp))
        FinVaultInfoRow(
            icon = Icons.Filled.Savings,
            label = "Savings & Reserves",
            value = savingsBalance.formatInr(),
            iconTint = semantic.income
        )
    }
}

@Composable
fun EmiAndCardRemindersCard(
    creditCardOutstanding: MoneyPaise,
    cardCount: Int,
    loanEmiSummary: LoanEmiLedgerSummary,
    upcomingReminders: List<UpcomingReminderItem>,
    onPostRecurringRule: (String) -> Unit,
    onPayCreditCard: () -> Unit,
    onOpenCardsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinVaultColors.current
    FinVaultCard(modifier = modifier.testTag("home_emi_cards_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "EMIs, Loans & Credit Cards",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TextButton(
                onClick = onOpenCardsTab,
                modifier = Modifier.testTag("home_open_cards_btn")
            ) {
                Text("Cards")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Card Outstanding ($cardCount)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = creditCardOutstanding.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = if (creditCardOutstanding.paise > 0L) semantic.expense else semantic.income
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "EMI Paid This Month",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = loanEmiSummary.currentMonthPaid.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (loanEmiSummary.paymentCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ledger Loan Totals (${loanEmiSummary.paymentCount} entries): Principal ${loanEmiSummary.totalPrincipalPaid.formatInr()} • Interest ${loanEmiSummary.totalInterestPaid.formatInr()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = semantic.cardBorder)
        Spacer(modifier = Modifier.height(10.dp))

        if (upcomingReminders.isEmpty()) {
            Text(
                text = "No upcoming EMI, recurring bill, or credit card dues scheduled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                upcomingReminders.take(4).forEach { reminder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_reminder_${reminder.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reminder.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = reminder.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = reminder.amount.formatInr(),
                                style = FinVaultNumericTypography.rowAmount,
                                color = semantic.expense
                            )
                            when (reminder) {
                                is UpcomingReminderItem.RecurringEmiOrBill -> {
                                    TextButton(
                                        onClick = { onPostRecurringRule(reminder.ruleId) },
                                        modifier = Modifier.testTag("home_post_reminder_${reminder.ruleId}")
                                    ) {
                                        Text("Post Now")
                                    }
                                }
                                is UpcomingReminderItem.CardDue -> {
                                    TextButton(
                                        onClick = onPayCreditCard,
                                        modifier = Modifier.testTag("home_pay_card_${reminder.cardAccountId}")
                                    ) {
                                        Text("Pay Card")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebtsSummaryCard(
    summary: DebtLedgerSummary,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinVaultColors.current
    FinVaultCard(modifier = modifier.testTag("home_debts_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Handshake,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Outstanding Debts",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            FinVaultStatusPill(
                text = if (summary.transactionCount == 0) "Settled" else "${summary.transactionCount} entries"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Owed to You",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.owedToYou.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.income,
                    modifier = Modifier.testTag("home_debt_owed_to_you")
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "You Owe",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.youOwe.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.expense,
                    modifier = Modifier.testTag("home_debt_you_owe")
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Net Position",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.netDebtPosition.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = if (summary.netDebtPosition.paise >= 0L) semantic.income else semantic.expense
                )
            }
        }
        if (summary.transactionCount == 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Zero active lending or borrowing entries recorded in the central ledger.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SplitBillSummaryCard(
    summary: SplitBillLedgerSummary,
    portfolio: SplitBillPortfolioSummary,
    onOpenSplitBill: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinVaultColors.current
    FinVaultCard(modifier = modifier.testTag("home_split_bill_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CallSplit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Split Bill Summary",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TextButton(
                onClick = onOpenSplitBill,
                modifier = Modifier.testTag("home_open_split_bill_btn")
            ) {
                Text("Manage (${portfolio.activeGroupCount})")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "You Are Owed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = portfolio.youAreOwed.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.income,
                    modifier = Modifier.testTag("home_split_you_are_owed")
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "You Owe",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = portfolio.youOwe.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.expense,
                    modifier = Modifier.testTag("home_split_you_owe")
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Group Expenses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (portfolio.totalGroupExpenses.isPositive || portfolio.activeGroupCount > 0) {
                        portfolio.totalGroupExpenses.formatInr()
                    } else {
                        summary.totalSplitPaid.formatInr()
                    },
                    style = FinVaultNumericTypography.rowAmount,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("home_split_total_paid")
                )
            }
        }

        if (summary.transactionCount > 0 || portfolio.reimbursementsReceivedTotal.isPositive) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = semantic.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Paid by You: ${summary.totalSplitPaid.formatInr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Reimbursed: ${summary.totalReimbursed.formatInr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.income,
                    modifier = Modifier.testTag("home_split_reimbursed")
                )
            }
        } else if (portfolio.activeGroupCount == 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Create groups for trips, roommates, or family to split expenses and track settlements.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CustomizeHomeWidgetsDialog(
    hiddenWidgets: Set<HomeDashboardWidget>,
    onToggleWidget: (HomeDashboardWidget, Boolean) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("home_customize_widgets_dialog"),
        title = {
            Text(
                text = "Customize Home Widgets",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose which ledger summaries and charts appear on your Home dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                HomeDashboardWidget.entries.forEach { widget ->
                    val isVisible = widget !in hiddenWidgets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = widget.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isVisible,
                            onCheckedChange = { checked -> onToggleWidget(widget, checked) },
                            modifier = Modifier.testTag("widget_toggle_${widget.name.lowercase()}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("customize_widgets_done")
            ) {
                Text("Done")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onResetDefault,
                modifier = Modifier.testTag("customize_widgets_reset")
            ) {
                Text("Reset All")
            }
        }
    )
}
