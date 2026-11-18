package com.example.feature.finance

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CategoryType
import com.example.core.database.entity.EntrySide
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringFrequency
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    kind: TransactionFormKind,
    editingTransaction: LedgerTransactionWithEntries?,
    preselectedAccountId: String?,
    preselectedCardId: String?,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    defaultExpensePaymentMethod: PaymentMethod,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onOpenAddCategory: () -> Unit,
    onSubmit: (
        kind: TransactionFormKind,
        editingTxId: String?,
        amountInput: String,
        description: String,
        notes: String,
        sourceAccountId: String?,
        destinationAccountId: String?,
        categoryId: String?,
        paymentMethod: PaymentMethod,
        timestampEpochMs: Long,
        isRecurring: Boolean,
        frequency: RecurringFrequency,
        idempotencyKey: String
    ) -> Unit
) {
    val assetAccounts = remember(accounts) { accounts.filter { it.accountType != AccountType.CREDIT_CARD } }
    val cardAccounts = remember(accounts) { accounts.filter { it.accountType == AccountType.CREDIT_CARD } }
    val defaultBank = remember(assetAccounts) {
        assetAccounts.firstOrNull { it.accountType == AccountType.BANK && it.isDefault }
            ?: assetAccounts.firstOrNull { it.accountType == AccountType.BANK }
            ?: assetAccounts.firstOrNull()
    }
    val submissionKey = remember(kind, editingTransaction?.transaction?.id) {
        UUID.randomUUID().toString()
    }

    val initialAmount = remember(editingTransaction) {
        editingTransaction?.let { MoneyPaise(it.transaction.totalAmountPaise).formatInr(includeSymbol = false) }
            ?.replace(",", "")
            ?: ""
    }
    val initialSourceId = remember(editingTransaction, preselectedAccountId, accounts) {
        editingTransaction?.entries?.firstOrNull { it.entrySide == EntrySide.CREDIT && it.accountId != null }?.accountId
            ?: preselectedAccountId
            ?: defaultBank?.id
            ?: accounts.firstOrNull()?.id
    }
    val initialDestId = remember(editingTransaction, preselectedCardId, assetAccounts, cardAccounts) {
        when (kind) {
            TransactionFormKind.INCOME -> editingTransaction?.entries
                ?.firstOrNull { it.entrySide == EntrySide.DEBIT && it.accountId != null }?.accountId
                ?: defaultBank?.id
            TransactionFormKind.TRANSFER -> editingTransaction?.entries
                ?.firstOrNull { it.entrySide == EntrySide.DEBIT && it.accountId != null }?.accountId
                ?: assetAccounts.firstOrNull { it.id != initialSourceId }?.id
            TransactionFormKind.CARD_PAYMENT -> preselectedCardId
                ?: editingTransaction?.entries?.firstOrNull { it.entrySide == EntrySide.DEBIT && it.accountId != null }?.accountId
                ?: cardAccounts.firstOrNull()?.id
            TransactionFormKind.EXPENSE -> null
        }
    }
    val initialPaymentMethod = remember(editingTransaction, preselectedAccountId, accounts, defaultExpensePaymentMethod) {
        editingTransaction?.transaction?.paymentMethod
            ?: preselectedAccountId?.let { id ->
                accounts.firstOrNull { it.id == id }?.let { acc ->
                    when (acc.accountType) {
                        AccountType.BANK -> PaymentMethod.BANK
                        AccountType.CASH -> PaymentMethod.CASH
                        AccountType.SAVINGS -> PaymentMethod.SAVINGS
                        AccountType.CREDIT_CARD -> PaymentMethod.CREDIT_CARD
                        AccountType.CUSTOM -> PaymentMethod.CUSTOM
                    }
                }
            }
            ?: defaultExpensePaymentMethod
    }

    val filteredCategories = remember(categories, kind) {
        val targetType = if (kind == TransactionFormKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categories.filter { it.type == targetType }
    }

    var amountInput by remember { mutableStateOf(initialAmount) }
    var description by remember { mutableStateOf(editingTransaction?.transaction?.description ?: "") }
    var notes by remember { mutableStateOf(editingTransaction?.transaction?.notes ?: "") }
    var selectedSourceId by remember { mutableStateOf(initialSourceId) }
    var selectedDestId by remember { mutableStateOf(initialDestId) }
    var selectedCategoryId by remember {
        mutableStateOf(
            editingTransaction?.transaction?.categoryId
                ?: filteredCategories.firstOrNull()?.id
        )
    }
    var paymentMethod by remember {
        mutableStateOf(initialPaymentMethod)
    }
    var timestampMs by remember {
        mutableLongStateOf(editingTransaction?.transaction?.txTimestampEpochMs ?: DateTimeUtils.nowEpochMs())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }

    val sheetTitle = when {
        editingTransaction != null -> "Edit Transaction"
        kind == TransactionFormKind.EXPENSE -> "Record Expense"
        kind == TransactionFormKind.INCOME -> "Record Income"
        kind == TransactionFormKind.TRANSFER -> "Transfer Money"
        else -> "Pay Credit Card"
    }

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
                        .verticalScroll(rememberScrollState())
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
            Text(
                text = sheetTitle,
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Amount (₹)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_amount_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_description_input")
            )

            if (kind == TransactionFormKind.EXPENSE) {
                Text("Payment Method (Defaults to Bank)", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        PaymentMethod.BANK to "Bank",
                        PaymentMethod.CASH to "Cash",
                        PaymentMethod.CREDIT_CARD to "Credit Card",
                        PaymentMethod.SAVINGS to "Savings"
                    ).forEach { (method, label) ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = {
                                paymentMethod = method
                                val matchingPool = accounts.filter {
                                    when (method) {
                                        PaymentMethod.BANK -> it.accountType == AccountType.BANK
                                        PaymentMethod.CASH -> it.accountType == AccountType.CASH
                                        PaymentMethod.CREDIT_CARD -> it.accountType == AccountType.CREDIT_CARD
                                        PaymentMethod.SAVINGS -> it.accountType == AccountType.SAVINGS
                                        PaymentMethod.CUSTOM -> it.accountType == AccountType.CUSTOM
                                    }
                                }
                                val matching = matchingPool.firstOrNull { it.isDefault } ?: matchingPool.firstOrNull()
                                selectedSourceId = matching?.id
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("tx_method_${method.name.lowercase()}")
                        )
                    }
                }
                val selectedAccType = accounts.firstOrNull { it.id == selectedSourceId }?.accountType
                val methodMatchesSelected = when (paymentMethod) {
                    PaymentMethod.BANK -> selectedAccType == AccountType.BANK
                    PaymentMethod.CASH -> selectedAccType == AccountType.CASH
                    PaymentMethod.CREDIT_CARD -> selectedAccType == AccountType.CREDIT_CARD
                    PaymentMethod.SAVINGS -> selectedAccType == AccountType.SAVINGS
                    PaymentMethod.CUSTOM -> selectedAccType == AccountType.CUSTOM
                }
                if (selectedSourceId == null && accounts.isNotEmpty() && !methodMatchesSelected) {
                    Text(
                        text = "No ${paymentMethod.name.lowercase().replace('_', ' ')} account exists yet — add one or select another account below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (kind == TransactionFormKind.EXPENSE || kind == TransactionFormKind.TRANSFER || kind == TransactionFormKind.CARD_PAYMENT) {
                val sourcePool = if (kind == TransactionFormKind.EXPENSE) accounts else assetAccounts
                Text(
                    text = if (kind == TransactionFormKind.EXPENSE) "Paid From Account" else "From Account",
                    style = MaterialTheme.typography.labelLarge
                )
                if (sourcePool.isEmpty()) {
                    Text(
                        text = "No eligible accounts available. Add a Bank, Cash, or Savings account first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sourcePool.forEach { acc ->
                            FilterChip(
                                selected = selectedSourceId == acc.id,
                                onClick = {
                                    selectedSourceId = acc.id
                                    if (kind == TransactionFormKind.EXPENSE) {
                                        paymentMethod = when (acc.accountType) {
                                            AccountType.BANK -> PaymentMethod.BANK
                                            AccountType.CASH -> PaymentMethod.CASH
                                            AccountType.SAVINGS -> PaymentMethod.SAVINGS
                                            AccountType.CREDIT_CARD -> PaymentMethod.CREDIT_CARD
                                            AccountType.CUSTOM -> PaymentMethod.CUSTOM
                                        }
                                    }
                                },
                                label = { Text("${acc.name} (${MoneyPaise(acc.currentBalancePaise).formatInr()})") },
                                modifier = Modifier.testTag("tx_source_${acc.id}")
                            )
                        }
                    }
                }
            }

            if (kind == TransactionFormKind.INCOME || kind == TransactionFormKind.TRANSFER || kind == TransactionFormKind.CARD_PAYMENT) {
                val destPool = if (kind == TransactionFormKind.CARD_PAYMENT) cardAccounts else assetAccounts
                Text(
                    text = when (kind) {
                        TransactionFormKind.INCOME -> "Deposit To Account"
                        TransactionFormKind.CARD_PAYMENT -> "Credit Card"
                        else -> "To Account"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
                if (destPool.isEmpty()) {
                    Text(
                        text = if (kind == TransactionFormKind.CARD_PAYMENT) {
                            "No credit cards added yet. Add a card first."
                        } else {
                            "No Bank, Cash, or Savings account found. Add an account first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (kind == TransactionFormKind.TRANSFER && destPool.size < 2) {
                    Text(
                        text = "Add at least 2 Bank, Cash, or Savings accounts to transfer between them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        destPool.forEach { acc ->
                            FilterChip(
                                selected = selectedDestId == acc.id,
                                onClick = { selectedDestId = acc.id },
                                label = { Text(acc.name) },
                                modifier = Modifier.testTag("tx_dest_${acc.id}")
                            )
                        }
                    }
                }
            }

            if (kind == TransactionFormKind.EXPENSE || kind == TransactionFormKind.INCOME) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onOpenAddCategory) {
                        Text("+ New Category")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name) },
                            modifier = Modifier.testTag("tx_cat_${cat.id}")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Date: ${DateTimeUtils.formatShortDate(timestampMs)}", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("Change Date")
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_notes_input")
            )

            if (editingTransaction == null && (kind == TransactionFormKind.EXPENSE || kind == TransactionFormKind.INCOME)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Repeat as Recurring", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it },
                        modifier = Modifier.testTag("tx_recurring_switch")
                    )
                }
                if (isRecurring) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecurringFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSubmit(
                            kind,
                            editingTransaction?.transaction?.id,
                            amountInput,
                            description,
                            notes,
                            selectedSourceId,
                            selectedDestId,
                            selectedCategoryId,
                            paymentMethod,
                            timestampMs,
                            isRecurring,
                            frequency,
                            submissionKey
                        )
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tx_submit_button")
                ) {
                    Text(
                        when {
                            isSubmitting -> "Saving..."
                            editingTransaction != null -> "Save Changes"
                            else -> "Post Entry"
                        }
                    )
                }
            }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = timestampMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { timestampMs = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
