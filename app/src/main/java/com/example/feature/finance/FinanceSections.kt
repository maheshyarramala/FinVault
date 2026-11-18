package com.example.feature.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.BudgetProgressItem
import com.example.core.data.repository.CreditCardSummary
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CategoryType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.RecurringRuleEntity
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors

@Composable
fun ExpensesFilterBar(
    searchQuery: String,
    typeFilter: TransactionTypeFilter,
    datePreset: DateRangePreset,
    selectedCategoryId: String?,
    selectedAccountId: String?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    hasActiveFilters: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectTypeFilter: (TransactionTypeFilter) -> Unit,
    onSelectDatePreset: (DateRangePreset) -> Unit,
    onSelectCategoryId: (String?) -> Unit,
    onSelectAccountId: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.finance_search_hint)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("finance_search_input")
        )
        if (hasActiveFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.testTag("finance_clear_filters")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Clear All Filters")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                TransactionTypeFilter.ALL to "All",
                TransactionTypeFilter.EXPENSE to "Expense",
                TransactionTypeFilter.INCOME to "Income",
                TransactionTypeFilter.TRANSFER to "Transfer",
                TransactionTypeFilter.CARD to "Card"
            ).forEach { (filter, label) ->
                FilterChip(
                    selected = typeFilter == filter,
                    onClick = { onSelectTypeFilter(filter) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("filter_type_${filter.name.lowercase()}")
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                DateRangePreset.ALL to "All Dates",
                DateRangePreset.TODAY to "Today",
                DateRangePreset.THIS_MONTH to "This Month",
                DateRangePreset.LAST_30_DAYS to "Last 30d"
            ).forEach { (preset, label) ->
                FilterChip(
                    selected = datePreset == preset,
                    onClick = { onSelectDatePreset(preset) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("filter_date_${preset.name.lowercase()}")
                )
            }
        }

        if (accounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedAccountId == null,
                    onClick = { onSelectAccountId(null) },
                    label = { Text("All Accounts") },
                    modifier = Modifier.testTag("filter_account_all")
                )
                accounts.forEach { acc ->
                    FilterChip(
                        selected = selectedAccountId == acc.id,
                        onClick = {
                            onSelectAccountId(if (selectedAccountId == acc.id) null else acc.id)
                        },
                        label = { Text(acc.name) },
                        modifier = Modifier.testTag("filter_account_${acc.id}")
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryId == null,
                    onClick = { onSelectCategoryId(null) },
                    label = { Text("All Categories") },
                    modifier = Modifier.testTag("filter_category_all")
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = {
                            onSelectCategoryId(if (selectedCategoryId == cat.id) null else cat.id)
                        },
                        label = { Text(cat.name) },
                        modifier = Modifier.testTag("filter_category_${cat.id}")
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRowCard(
    item: LedgerTransactionWithEntries,
    categoryMap: Map<String, CategoryEntity>,
    accountMap: Map<String, AccountEntity>,
    onClick: () -> Unit
) {
    val tx = item.transaction
    val semantic = LocalFinVaultColors.current
    val category = tx.categoryId?.let { categoryMap[it] }
    val linkedAccounts = item.entries.mapNotNull { it.accountId?.let { id -> accountMap[id]?.name } }.distinct()

    val isIncome = tx.txType == LedgerTransactionType.INCOME ||
        tx.txType == LedgerTransactionType.SPLIT_REIMBURSEMENT
    val isTransfer = tx.txType == LedgerTransactionType.TRANSFER ||
        tx.txType == LedgerTransactionType.CREDIT_CARD_PAYMENT

    val amountColor = when {
        isIncome -> semantic.income
        isTransfer -> MaterialTheme.colorScheme.primary
        else -> semantic.expense
    }
    val prefix = when {
        isIncome -> "+"
        isTransfer -> ""
        else -> "-"
    }

    FinVaultCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("transaction_card_${tx.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            category?.let { Color(it.colorHex) }
                                ?: MaterialTheme.colorScheme.primary
                        )
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.description,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitleParts = buildList {
                        add(category?.name ?: tx.txType.name.replace('_', ' '))
                        if (linkedAccounts.isNotEmpty()) add(linkedAccounts.joinToString(" → "))
                        add(DateTimeUtils.formatShortDate(tx.txTimestampEpochMs))
                    }
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${MoneyPaise(tx.totalAmountPaise).formatInr()}",
                    style = FinVaultNumericTypography.rowAmount,
                    color = amountColor
                )
                Text(
                    text = tx.paymentMethod.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccountRowCard(
    account: AccountEntity,
    onClick: () -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val icon = when (account.accountType) {
        AccountType.BANK -> Icons.Filled.AccountBalance
        AccountType.CASH -> Icons.Filled.AccountBalanceWallet
        AccountType.SAVINGS -> Icons.Filled.Savings
        AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
        AccountType.CUSTOM -> Icons.Filled.SwapHoriz
    }
    val balanceColor = if (account.accountType == AccountType.CREDIT_CARD) {
        semantic.expense
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    FinVaultCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("account_card_${account.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (account.isDefault) {
                            FinVaultStatusPill(text = "Default")
                        }
                    }
                    val meta = buildList {
                        add(account.accountType.name.replace('_', ' '))
                        account.institutionName?.let { add(it) }
                        account.accountLast4?.let { add("••$it") }
                    }.joinToString(" • ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyPaise(account.currentBalancePaise).formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = balanceColor
                )
                Text(
                    text = if (account.accountType == AccountType.CREDIT_CARD) "Outstanding" else "Balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RecurringRulesStrip(
    rules: List<RecurringRuleEntity>,
    onPostNow: (String) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rules.forEach { rule ->
            FinVaultCard(modifier = Modifier.testTag("recurring_rule_${rule.id}")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${MoneyPaise(rule.amountPaise).formatInr()} • ${rule.frequency.name} • Next ${DateTimeUtils.formatShortDate(rule.nextRunEpochMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onPostNow(rule.id) },
                            modifier = Modifier.testTag("recurring_post_${rule.id}")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Post")
                        }
                        IconButton(
                            onClick = { onDeleteRule(rule.id) },
                            modifier = Modifier.testTag("recurring_delete_${rule.id}")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete recurring rule",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreditCardDetailCard(
    card: CreditCardSummary,
    onToggleReminder: (Boolean) -> Unit,
    onPayCard: () -> Unit,
    onSpendOnCard: () -> Unit,
    onSnapshotStatement: () -> Unit,
    onEditCard: () -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val utilFraction = (card.utilizationPct / 100f).coerceIn(0f, 1f)
    val utilColor = when {
        card.utilizationPct >= 80 -> semantic.expense
        card.utilizationPct >= 50 -> semantic.warning
        else -> MaterialTheme.colorScheme.primary
    }

    FinVaultCard(modifier = Modifier.testTag("credit_card_item_${card.accountId}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name + (card.last4?.let { " ••$it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${card.network ?: "Card"} • Cycle ${card.billingCycleDay} • Stmt ${card.statementDay} • Due ${card.dueDay}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FinVaultStatusPill(
                    text = "${card.utilizationPct}% used",
                    containerColor = utilColor.copy(alpha = 0.14f),
                    contentColor = utilColor
                )
                IconButton(
                    onClick = onEditCard,
                    modifier = Modifier.testTag("card_edit_${card.accountId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit credit card",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { utilFraction },
            color = utilColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = card.outstandingBalance.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.expense
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = card.availableCredit.formatInr(),
                    style = FinVaultNumericTypography.rowAmount,
                    color = semantic.income
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = card.creditLimit.formatInr(),
                    style = FinVaultNumericTypography.rowAmount
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Due Date Reminder", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = card.reminderEnabled,
                onCheckedChange = onToggleReminder,
                modifier = Modifier.testTag("card_reminder_${card.accountId}")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSpendOnCard,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_spend_${card.accountId}")
            ) {
                Text("Spend")
            }
            Button(
                onClick = onPayCard,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_pay_${card.accountId}")
            ) {
                Text("Pay Card")
            }
            OutlinedButton(
                onClick = onSnapshotStatement,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_stmt_${card.accountId}")
            ) {
                Text("Stmt")
            }
        }

        if (card.statements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = semantic.cardBorder)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recent Statements", style = MaterialTheme.typography.labelMedium)
            card.statements.take(2).forEach { stmt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Due ${DateTimeUtils.formatShortDate(stmt.dueDateEpochMs)} (${stmt.status.name})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MoneyPaise(stmt.paidAmountPaise).formatInr()} / ${MoneyPaise(stmt.statementBalancePaise).formatInr()}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetProgressCard(
    item: BudgetProgressItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val progress = (item.usagePercentage / 100f).coerceIn(0f, 1f)
    val statusColor = when {
        item.isOverBudget -> semantic.expense
        item.isAlertTriggered -> semantic.warning
        else -> semantic.income
    }
    val statusLabel = when {
        item.isOverBudget -> "Over Budget (${item.usagePercentage}%)"
        item.isAlertTriggered -> "Alert ≥${item.alertThresholdPct}% (${item.usagePercentage}%)"
        else -> "${item.usagePercentage}% Used"
    }

    FinVaultCard(modifier = Modifier.testTag("budget_card_${item.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Limit ${item.limit.formatInr()} • Prev Month ${item.previousMonthSpent.formatInr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FinVaultStatusPill(
                    text = statusLabel,
                    containerColor = statusColor.copy(alpha = 0.14f),
                    contentColor = statusColor
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("budget_edit_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit budget",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("budget_delete_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete budget",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Spent: ${item.spent.formatInr()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (item.remaining.paise >= 0L) {
                    "Remaining: ${item.remaining.formatInr()}"
                } else {
                    "Overspent: ${MoneyPaise(-item.remaining.paise).formatInr()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.remaining.paise >= 0L) semantic.income else semantic.expense
            )
        }
    }
}

@Composable
fun CategorySummaryRow(category: CategoryEntity) {
    val semantic = LocalFinVaultColors.current
    val isIncome = category.type == CategoryType.INCOME
    val typeLabel = if (isIncome) {
        stringResource(R.string.finance_filter_income)
    } else {
        stringResource(R.string.finance_filter_expense)
    }
    val typeContainer = if (isIncome) semantic.incomeContainer else semantic.expenseContainer
    val typeColor = if (isIncome) semantic.income else semantic.expense

    FinVaultCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(category.colorHex))
                )
                Text(
                    text = if (category.parentId != null) "↳ ${category.name}" else category.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = typeContainer,
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
