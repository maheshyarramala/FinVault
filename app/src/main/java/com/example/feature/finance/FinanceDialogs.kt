package com.example.feature.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.BudgetProgressItem
import com.example.core.data.repository.CreditCardSummary
import com.example.core.database.dao.AccountStatementEntryRow
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CategoryType
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors
import kotlinx.coroutines.flow.Flow

@Composable
fun AddAccountDialog(
    editingAccount: AccountEntity? = null,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        type: AccountType,
        openingBalance: String,
        institution: String,
        last4: String,
        isDefault: Boolean
    ) -> Unit,
    onUpdate: (
        accountId: String,
        name: String,
        institution: String,
        last4: String,
        isDefault: Boolean
    ) -> Unit = { _, _, _, _, _ -> }
) {
    var name by remember(editingAccount?.id) { mutableStateOf(editingAccount?.name ?: "") }
    var type by remember(editingAccount?.id) { mutableStateOf(editingAccount?.accountType ?: AccountType.BANK) }
    var openingBalance by remember(editingAccount?.id) { mutableStateOf("") }
    var institution by remember(editingAccount?.id) { mutableStateOf(editingAccount?.institutionName ?: "") }
    var last4 by remember(editingAccount?.id) { mutableStateOf(editingAccount?.accountLast4 ?: "") }
    var isDefault by remember(editingAccount?.id) { mutableStateOf(editingAccount?.isDefault ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingAccount == null) "Add Account" else "Edit Account") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. HDFC Savings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("account_name_input")
                )
                if (editingAccount == null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AccountType.BANK to "Bank",
                            AccountType.CASH to "Cash",
                            AccountType.SAVINGS to "Savings",
                            AccountType.CUSTOM to "Custom"
                        ).forEach { (accType, label) ->
                            FilterChip(
                                selected = type == accType,
                                onClick = { type = accType },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = openingBalance,
                        onValueChange = { openingBalance = it },
                        label = { Text("Opening Balance (₹)") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("account_opening_input")
                    )
                } else {
                    Text(
                        text = "${editingAccount.accountType.name.replace('_', ' ')} • Opening balance ${MoneyPaise(editingAccount.openingBalancePaise).formatInr()} (ledger-fixed)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text("Bank / Institution (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = last4,
                    onValueChange = { if (it.length <= 4) last4 = it },
                    label = { Text("Last 4 Digits (Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set as Default", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editingAccount == null) {
                        onCreate(name, type, openingBalance, institution, last4, isDefault)
                    } else {
                        onUpdate(editingAccount.id, name, institution, last4, isDefault)
                    }
                },
                modifier = Modifier.testTag("account_create_submit")
            ) { Text(if (editingAccount == null) "Create" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddCreditCardDialog(
    editingCard: CreditCardSummary? = null,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        last4: String,
        network: String,
        limit: String,
        outstanding: String,
        cycleDay: Int,
        statementDay: Int,
        dueDay: Int,
        reminder: Boolean
    ) -> Unit,
    onUpdate: (
        accountId: String,
        name: String,
        last4: String,
        network: String,
        limit: String,
        cycleDay: Int,
        statementDay: Int,
        dueDay: Int,
        reminder: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
) {
    val initialLimit = remember(editingCard?.accountId) {
        editingCard?.let { it.creditLimit.formatInr(includeSymbol = false).replace(",", "") } ?: ""
    }
    var name by remember(editingCard?.accountId) { mutableStateOf(editingCard?.name ?: "") }
    var last4 by remember(editingCard?.accountId) { mutableStateOf(editingCard?.last4 ?: "") }
    var network by remember(editingCard?.accountId) { mutableStateOf(editingCard?.network ?: "Visa") }
    var limit by remember(editingCard?.accountId) { mutableStateOf(initialLimit) }
    var outstanding by remember(editingCard?.accountId) { mutableStateOf("") }
    var cycleDay by remember(editingCard?.accountId) { mutableStateOf((editingCard?.billingCycleDay ?: 1).toString()) }
    var statementDay by remember(editingCard?.accountId) { mutableStateOf((editingCard?.statementDay ?: 15).toString()) }
    var dueDay by remember(editingCard?.accountId) { mutableStateOf((editingCard?.dueDay ?: 5).toString()) }
    var reminder by remember(editingCard?.accountId) { mutableStateOf(editingCard?.reminderEnabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingCard == null) "Add Credit Card" else "Edit Credit Card") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Card Name") },
                    placeholder = { Text("e.g. HDFC Regalia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("card_name_input")
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = { Text("Credit Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("card_limit_input")
                )
                if (editingCard == null) {
                    OutlinedTextField(
                        value = outstanding,
                        onValueChange = { outstanding = it },
                        label = { Text("Current Outstanding (₹)") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("card_outstanding_input")
                    )
                } else {
                    Text(
                        text = "Current outstanding ${editingCard.outstandingBalance.formatInr()} is calculated from the ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = last4,
                        onValueChange = { if (it.length <= 4) last4 = it },
                        label = { Text("Last 4") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = network,
                        onValueChange = { network = it },
                        label = { Text("Network") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cycleDay,
                        onValueChange = { cycleDay = it },
                        label = { Text("Cycle Day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = statementDay,
                        onValueChange = { statementDay = it },
                        label = { Text("Stmt Day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDay,
                        onValueChange = { dueDay = it },
                        label = { Text("Due Day") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Reminders", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminder, onCheckedChange = { reminder = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cDay = cycleDay.toIntOrNull() ?: 1
                    val sDay = statementDay.toIntOrNull() ?: 15
                    val dDay = dueDay.toIntOrNull() ?: 5
                    if (editingCard == null) {
                        onCreate(name, last4, network, limit, outstanding, cDay, sDay, dDay, reminder)
                    } else {
                        onUpdate(editingCard.accountId, name, last4, network, limit, cDay, sDay, dDay, reminder)
                    }
                },
                modifier = Modifier.testTag("card_create_submit")
            ) { Text(if (editingCard == null) "Add Card" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BudgetFormDialog(
    editingBudget: BudgetProgressItem? = null,
    categories: List<CategoryEntity>,
    monthKey: String,
    onDismiss: () -> Unit,
    onSave: (categoryId: String?, limitInput: String, alertThresholdPct: Int) -> Unit
) {
    val expenseCategories = remember(categories) {
        categories.filter { it.type == CategoryType.EXPENSE }
    }
    val initialLimit = remember(editingBudget?.id) {
        editingBudget?.limit?.formatInr(includeSymbol = false)?.replace(",", "") ?: ""
    }
    var selectedCategoryId by remember(editingBudget?.id) { mutableStateOf(editingBudget?.categoryId) }
    var limitInput by remember(editingBudget?.id) { mutableStateOf(initialLimit) }
    var thresholdPct by remember(editingBudget?.id) { mutableIntStateOf(editingBudget?.alertThresholdPct ?: 80) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (editingBudget == null) {
                    "Set Budget (${DateTimeUtils.formatMonthKeyLabel(monthKey)})"
                } else {
                    "Edit Budget (${DateTimeUtils.formatMonthKeyLabel(monthKey)})"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scope", style = MaterialTheme.typography.labelLarge)
                if (editingBudget == null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("Overall Monthly") },
                            modifier = Modifier.testTag("budget_scope_overall")
                        )
                        expenseCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) },
                                modifier = Modifier.testTag("budget_scope_${cat.id}")
                            )
                        }
                    }
                } else {
                    Text(
                        text = editingBudget.categoryName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Spending Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("budget_limit_input")
                )
                Text("Alert Threshold: $thresholdPct%", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(70, 80, 90).forEach { pct ->
                        FilterChip(
                            selected = thresholdPct == pct,
                            onClick = { thresholdPct = pct },
                            label = { Text("$pct%") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedCategoryId, limitInput, thresholdPct) },
                modifier = Modifier.testTag("budget_save_submit")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateCategoryDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, type: CategoryType, parentId: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CategoryType.EXPENSE) }
    var parentId by remember { mutableStateOf<String?>(null) }

    val parentCandidates = remember(categories, type) {
        categories.filter { it.type == type && it.parentId == null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Category / Subcategory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("category_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == CategoryType.EXPENSE,
                        onClick = {
                            type = CategoryType.EXPENSE
                            parentId = null
                        },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = type == CategoryType.INCOME,
                        onClick = {
                            type = CategoryType.INCOME
                            parentId = null
                        },
                        label = { Text("Income") }
                    )
                }
                Text("Parent Category (Optional Subcategory)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = parentId == null,
                        onClick = { parentId = null },
                        label = { Text("Top-level") }
                    )
                    parentCandidates.forEach { parent ->
                        FilterChip(
                            selected = parentId == parent.id,
                            onClick = { parentId = parent.id },
                            label = { Text(parent.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, type, parentId) },
                modifier = Modifier.testTag("category_save_submit")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AccountStatementSheet(
    account: AccountEntity,
    entriesFlow: Flow<List<AccountStatementEntryRow>>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val entries by entriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val semantic = LocalFinVaultColors.current
    var confirmArchive by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("account_stmt_close_btn")
                        ) {
                            Text("Close")
                        }
                    }
                    Text(
                        text = "Balance: ${MoneyPaise(account.currentBalancePaise).formatInr()} • Opening: ${MoneyPaise(account.openingBalancePaise).formatInr()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("account_edit_btn")
                        ) {
                            Text("Edit Account")
                        }
                        if (account.accountType != AccountType.CREDIT_CARD) {
                            OutlinedButton(
                                onClick = { confirmArchive = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("account_archive_btn")
                            ) {
                                Text("Archive")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    if (entries.isEmpty()) {
                        Text(
                            text = "No ledger entries posted for this account yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(entries, key = { it.entryId }) { row ->
                                val isPositiveEffect = if (account.accountType == AccountType.CREDIT_CARD) {
                                    row.entrySide == "CREDIT"
                                } else {
                                    row.entrySide == "DEBIT"
                                }
                                FinVaultCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (row.isVoided) "${row.description} (Voided)" else row.description,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "${row.txType} • ${DateTimeUtils.formatShortDate(row.txTimestampEpochMs)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${if (isPositiveEffect) "+" else "-"}${MoneyPaise(row.amountPaise).formatInr()}",
                                            style = FinVaultNumericTypography.rowAmount,
                                            color = if (isPositiveEffect) semantic.income else semantic.expense
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive '${account.name}'?") },
            text = {
                Text("Archiving hides this account from active lists while keeping historical ledger entries intact. Accounts must have ₹0.00 balance to archive.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmArchive = false
                        onArchive()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("account_archive_confirm_btn")
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text("Cancel") }
            }
        )
    }
}
