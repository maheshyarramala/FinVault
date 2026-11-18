package com.example.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultEmptyStateCard
import com.example.core.designsystem.component.FinVaultInfoRow
import com.example.core.designsystem.component.FinVaultMetricTile
import com.example.core.designsystem.component.FinVaultSectionHeader
import com.example.core.designsystem.theme.LocalFinVaultColors

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalFinVaultColors.current

    val periodLabel = when (uiState.selectedPeriod) {
        ReportPeriod.DAILY -> stringResource(R.string.reports_period_daily)
        ReportPeriod.WEEKLY -> stringResource(R.string.reports_period_weekly)
        ReportPeriod.MONTHLY -> stringResource(R.string.reports_period_monthly)
        ReportPeriod.YEARLY -> stringResource(R.string.reports_period_yearly)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.reports_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.reports_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            val periods = listOf(
                ReportPeriod.DAILY to stringResource(R.string.reports_period_daily),
                ReportPeriod.WEEKLY to stringResource(R.string.reports_period_weekly),
                ReportPeriod.MONTHLY to stringResource(R.string.reports_period_monthly),
                ReportPeriod.YEARLY to stringResource(R.string.reports_period_yearly)
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reports_period_switcher")
            ) {
                periods.forEachIndexed { index, (period, label) ->
                    SegmentedButton(
                        selected = uiState.selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = periods.size),
                        modifier = Modifier.testTag("reports_period_${period.name.lowercase()}")
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FinVaultMetricTile(
                    label = stringResource(R.string.reports_net_cashflow, periodLabel),
                    value = uiState.netCashFlow.formatInr(),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                FinVaultMetricTile(
                    label = stringResource(R.string.reports_savings_rate),
                    value = uiState.savingsRatePercent,
                    icon = Icons.Filled.Savings,
                    tint = semantic.income,
                    containerColor = semantic.incomeContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            FinVaultSectionHeader(
                title = "Personal vs Split Bill Accounting",
                trailingText = periodLabel
            )
            Spacer(modifier = Modifier.height(8.dp))
            FinVaultCard(modifier = Modifier.testTag("reports_split_breakdown_card")) {
                FinVaultInfoRow(
                    icon = Icons.Filled.AccountBalanceWallet,
                    label = "Actual Personal Spending ($periodLabel + Split Share)",
                    value = uiState.actualPersonalSpending.formatInr(),
                    iconTint = semantic.expense
                )
                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                FinVaultInfoRow(
                    icon = Icons.Filled.Payments,
                    label = "Ordinary Income ($periodLabel, excl. Reimbursements)",
                    value = uiState.periodIncome.formatInr(),
                    iconTint = semantic.income
                )
                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                FinVaultInfoRow(
                    icon = Icons.Filled.CallSplit,
                    label = "Group Expenses Paid by You",
                    value = uiState.groupExpensesPaidByUser.formatInr(),
                    iconTint = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                FinVaultInfoRow(
                    icon = Icons.Filled.CallSplit,
                    label = "Your Share of All Group Expenses",
                    value = uiState.userGroupExpenseShare.formatInr(),
                    iconTint = semantic.expense
                )
                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                FinVaultInfoRow(
                    icon = Icons.Filled.Savings,
                    label = "Reimbursements Received (Receivable Recovery)",
                    value = uiState.splitReimbursementsReceived.formatInr(),
                    iconTint = semantic.income
                )
                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                FinVaultInfoRow(
                    icon = Icons.Filled.CallSplit,
                    label = "Outstanding Split Receivables (You Are Owed)",
                    value = uiState.outstandingSplitReceivables.formatInr(),
                    iconTint = semantic.income
                )
                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                FinVaultInfoRow(
                    icon = Icons.Filled.CallSplit,
                    label = "Pending Split Payables (You Owe)",
                    value = uiState.splitPayables.formatInr(),
                    iconTint = semantic.expense
                )
            }
        }

        item {
            FinVaultEmptyStateCard(
                icon = Icons.Filled.Analytics,
                title = "Net Worth ${uiState.netWorth.formatInr()}",
                body = "Ordinary income excludes incoming Split Bill reimbursements. Group expenses paid by others never deduct from your personal accounts or expenses.",
                badgeLabel = stringResource(R.string.phase_badge_format, 5)
            )
        }
    }
}
