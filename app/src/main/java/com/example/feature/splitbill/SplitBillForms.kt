package com.example.feature.splitbill

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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.SplitExpenseWithShares
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.SplitMethod
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.theme.LocalFinVaultColors
import com.example.core.domain.split.SplitCalculator
import com.example.core.domain.split.SplitInputSpec
import java.util.Locale

/**
 * Add or Edit Split Bill Expense Sheet.
 * Intentionally excludes tax, tip, and discount options per specification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSplitExpenseSheet(
    participants: List<SplitParticipantEntity>,
    eligibleAccounts: List<AccountEntity>,
    existingExpense: SplitExpenseWithShares?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        description: String,
        amountInput: String,
        expenseDateEpochMs: Long,
        paidByParticipantId: String,
        paidFromAccountId: String?,
        splitSpec: SplitInputSpec,
        notes: String
    ) -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val initialExpense = existingExpense?.expense
    val currentUser = participants.firstOrNull { it.isCurrentUser } ?: participants.firstOrNull()

    var description by remember(initialExpense) { mutableStateOf(initialExpense?.description ?: "") }
    var amountInput by remember(initialExpense) {
        mutableStateOf(
            initialExpense?.totalAmountPaise?.let { paise ->
                String.format(Locale.US, "%.2f", paise / 100.0)
            } ?: ""
        )
    }
    var expenseDateMs by remember(initialExpense) {
        mutableLongStateOf(initialExpense?.expenseDateEpochMs ?: DateTimeUtils.nowEpochMs())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var paidById by remember(initialExpense, participants) {
        mutableStateOf(initialExpense?.paidByParticipantId ?: currentUser?.id ?: "")
    }
    val defaultAccount = remember(eligibleAccounts) {
        eligibleAccounts.firstOrNull { it.isDefault && it.accountType == AccountType.BANK }
            ?: eligibleAccounts.firstOrNull { it.accountType == AccountType.BANK }
            ?: eligibleAccounts.firstOrNull()
    }
    var selectedAccountId by remember(initialExpense, eligibleAccounts) {
        mutableStateOf(initialExpense?.paidFromAccountId ?: defaultAccount?.id)
    }
    var splitMethod by remember(initialExpense) {
        mutableStateOf(initialExpense?.splitMethod ?: SplitMethod.EQUAL)
    }
    var notes by remember(initialExpense) { mutableStateOf(initialExpense?.notes ?: "") }

    val selectedParticipantIds = remember(existingExpense, participants) {
        val initialSet = if (existingExpense != null) {
            existingExpense.shares.map { it.participantId }.toSet()
        } else {
            participants.map { it.id }.toSet()
        }
        mutableStateMapOf<String, Boolean>().apply {
            participants.forEach { p -> put(p.id, p.id in initialSet) }
        }
    }

    val unequalInputs = remember(existingExpense, participants) {
        mutableStateMapOf<String, String>().apply {
            participants.forEach { p ->
                val share = existingExpense?.shares?.firstOrNull { it.participantId == p.id }
                put(
                    p.id,
                    share?.let { String.format(Locale.US, "%.2f", it.shareAmountPaise / 100.0) } ?: ""
                )
            }
        }
    }

    val percentageInputs = remember(existingExpense, participants) {
        mutableStateMapOf<String, String>().apply {
            participants.forEach { p ->
                val bps = existingExpense?.shares?.firstOrNull { it.participantId == p.id }?.percentageBps
                put(
                    p.id,
                    bps?.let { String.format(Locale.US, "%.2f", it / 100.0).removeSuffix(".00") } ?: ""
                )
            }
        }
    }

    val customShareInputs = remember(existingExpense, participants) {
        mutableStateMapOf<String, String>().apply {
            participants.forEach { p ->
                val scaled = existingExpense?.shares?.firstOrNull { it.participantId == p.id }?.customUnitsScaled
                put(
                    p.id,
                    scaled?.let {
                        String.format(Locale.US, "%.2f", it / 100.0).removeSuffix(".00")
                    } ?: "1"
                )
            }
        }
    }

    val paidByMember = participants.firstOrNull { it.id == paidById }
    val paidByMe = paidByMember?.isCurrentUser == true
    val activeParticipantIds = participants.filter { selectedParticipantIds[it.id] == true }.map { it.id }
    val parsedAmount = MoneyPaise.parseFromRupeeInput(amountInput)

    val validationPair = remember(
        parsedAmount,
        activeParticipantIds,
        splitMethod,
        unequalInputs.toMap(),
        percentageInputs.toMap(),
        customShareInputs.toMap(),
        paidByMe,
        selectedAccountId,
        eligibleAccounts
    ) {
        if (parsedAmount == null || !parsedAmount.isPositive) {
            null to "Enter a positive amount in ₹"
        } else if (paidByMe && selectedAccountId == null) {
            null to "Add or select a Bank or Cash account"
        } else if (activeParticipantIds.isEmpty()) {
            null to "Select at least 1 participant"
        } else {
            val specResult = runCatching {
                val spec = when (splitMethod) {
                    SplitMethod.EQUAL -> SplitInputSpec.Equal(activeParticipantIds)
                    SplitMethod.UNEQUAL_AMOUNT -> SplitInputSpec.UnequalAmounts(
                        participantIds = activeParticipantIds,
                        amountsPaise = activeParticipantIds.associateWith { id ->
                            MoneyPaise.parseFromRupeeInput(unequalInputs[id].orEmpty())?.paise ?: -1L
                        }
                    )
                    SplitMethod.PERCENTAGE -> SplitInputSpec.Percentages(
                        participantIds = activeParticipantIds,
                        percentagesBps = activeParticipantIds.associateWith { id ->
                            SplitCalculator.parsePercentToBps(percentageInputs[id].orEmpty()) ?: -1
                        }
                    )
                    SplitMethod.CUSTOM_SHARE -> SplitInputSpec.CustomShares(
                        participantIds = activeParticipantIds,
                        unitsScaled = activeParticipantIds.associateWith { id ->
                            SplitCalculator.parseCustomSharesScaled(customShareInputs[id].orEmpty()) ?: -1L
                        }
                    )
                }
                SplitCalculator.calculateShares(parsedAmount.paise, spec)
                spec
            }
            if (specResult.isSuccess) {
                specResult.getOrNull() to null
            } else {
                null to (specResult.exceptionOrNull()?.message ?: "Check split values")
            }
        }
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
                        .imePadding()
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
                            text = if (existingExpense == null) "Add Group Expense" else "Edit Group Expense",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (e.g. Dinner, Hotel)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_exp_desc_input")
                    )

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Total Amount (₹)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_exp_amount_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date: ${DateTimeUtils.formatShortDate(expenseDateMs)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("split_exp_date_btn")
                        ) {
                            Text("Change Date")
                        }
                    }

                    Text("Paid By", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        participants.forEach { member ->
                            FilterChip(
                                selected = paidById == member.id,
                                onClick = { paidById = member.id },
                                label = { Text(member.name + if (member.isCurrentUser) " (You)" else "") },
                                modifier = Modifier.testTag("split_paid_by_${member.id}")
                            )
                        }
                    }

                    if (paidByMe) {
                        Text(
                            text = "Deduct Full Expense From Bank / Cash Account",
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (eligibleAccounts.isEmpty()) {
                            Text(
                                text = "No Bank or Cash accounts exist yet. Create one in Finance → Accounts to pay as Me.",
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
                                eligibleAccounts.forEach { acc ->
                                    FilterChip(
                                        selected = selectedAccountId == acc.id,
                                        onClick = { selectedAccountId = acc.id },
                                        label = { Text("${acc.name} (${MoneyPaise(acc.currentBalancePaise).formatInr()})") },
                                        modifier = Modifier.testTag("split_exp_account_${acc.id}")
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Paid by ${paidByMember?.name ?: "other member"}: personal Bank/Cash balances remain untouched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("Split Method", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            SplitMethod.EQUAL to "Equal",
                            SplitMethod.UNEQUAL_AMOUNT to "Exact ₹",
                            SplitMethod.PERCENTAGE to "Percentage %",
                            SplitMethod.CUSTOM_SHARE to "Custom Shares"
                        ).forEach { (method, label) ->
                            FilterChip(
                                selected = splitMethod == method,
                                onClick = { splitMethod = method },
                                label = { Text(label) },
                                modifier = Modifier.testTag("split_method_${method.name.lowercase()}")
                            )
                        }
                    }

                    Text("Participants (${activeParticipantIds.size} selected)", style = MaterialTheme.typography.labelLarge)
                    FinVaultCard {
                        val previewShares = remember(parsedAmount, validationPair) {
                            val spec = validationPair.first
                            if (parsedAmount != null && spec != null) {
                                runCatching { SplitCalculator.calculateShares(parsedAmount.paise, spec) }
                                    .getOrNull()?.associateBy { it.participantId }
                            } else {
                                null
                            }
                        }
                        participants.forEach { member ->
                            val isChecked = selectedParticipantIds[member.id] == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedParticipantIds[member.id] = checked
                                    },
                                    modifier = Modifier.testTag("split_participant_check_${member.id}")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name + if (member.isCurrentUser) " (You)" else "",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val calculated = previewShares?.get(member.id)
                                    if (calculated != null) {
                                        Text(
                                            text = "Share: ${MoneyPaise(calculated.shareAmountPaise).formatInr()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semantic.income
                                        )
                                    }
                                }
                                if (isChecked && splitMethod != SplitMethod.EQUAL) {
                                    when (splitMethod) {
                                        SplitMethod.UNEQUAL_AMOUNT -> {
                                            OutlinedTextField(
                                                value = unequalInputs[member.id].orEmpty(),
                                                onValueChange = { unequalInputs[member.id] = it },
                                                label = { Text("₹") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .testTag("split_unequal_input_${member.id}")
                                            )
                                        }
                                        SplitMethod.PERCENTAGE -> {
                                            OutlinedTextField(
                                                value = percentageInputs[member.id].orEmpty(),
                                                onValueChange = { percentageInputs[member.id] = it },
                                                label = { Text("%") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .testTag("split_percent_input_${member.id}")
                                            )
                                        }
                                        SplitMethod.CUSTOM_SHARE -> {
                                            OutlinedTextField(
                                                value = customShareInputs[member.id].orEmpty(),
                                                onValueChange = { customShareInputs[member.id] = it },
                                                label = { Text("Shares") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier
                                                    .width(96.dp)
                                                    .testTag("split_custom_input_${member.id}")
                                            )
                                        }
                                        SplitMethod.EQUAL -> Unit
                                    }
                                }
                            }
                        }
                    }

                    validationPair.second?.let { validationError ->
                        Text(
                            text = validationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("split_exp_validation_error")
                        )
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_exp_notes_input")
                    )

                    Button(
                        onClick = {
                            val spec = validationPair.first ?: return@Button
                            onSubmit(
                                description,
                                amountInput,
                                expenseDateMs,
                                paidById,
                                if (paidByMe) selectedAccountId else null,
                                spec,
                                notes
                            )
                        },
                        enabled = !isSubmitting && description.isNotBlank() && validationPair.first != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_exp_submit_btn")
                    ) {
                        Text(if (existingExpense == null) "Save Group Expense" else "Update Group Expense")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = expenseDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { expenseDateMs = it }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditSplitSettlementSheet(
    participants: List<SplitParticipantEntity>,
    eligibleAccounts: List<AccountEntity>,
    existingSettlement: SplitSettlementEntity?,
    prefillFromId: String?,
    prefillToId: String?,
    prefillAmountPaise: Long?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        fromParticipantId: String,
        toParticipantId: String,
        amountInput: String,
        settlementDateEpochMs: Long,
        accountId: String?,
        notes: String
    ) -> Unit
) {
    val currentUser = participants.firstOrNull { it.isCurrentUser } ?: participants.firstOrNull()
    val otherMember = participants.firstOrNull { it.id != currentUser?.id } ?: participants.lastOrNull()

    var fromId by remember(existingSettlement, prefillFromId, participants) {
        mutableStateOf(
            existingSettlement?.fromParticipantId
                ?: prefillFromId
                ?: otherMember?.id
                ?: ""
        )
    }
    var toId by remember(existingSettlement, prefillToId, participants) {
        mutableStateOf(
            existingSettlement?.toParticipantId
                ?: prefillToId
                ?: currentUser?.id
                ?: ""
        )
    }
    var amountInput by remember(existingSettlement, prefillAmountPaise) {
        val initialPaise = existingSettlement?.amountPaise ?: prefillAmountPaise
        mutableStateOf(
            initialPaise?.let { String.format(Locale.US, "%.2f", it / 100.0) } ?: ""
        )
    }
    var settlementDateMs by remember(existingSettlement) {
        mutableLongStateOf(existingSettlement?.settlementDateEpochMs ?: DateTimeUtils.nowEpochMs())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val defaultAccount = remember(eligibleAccounts) {
        eligibleAccounts.firstOrNull { it.isDefault && it.accountType == AccountType.BANK }
            ?: eligibleAccounts.firstOrNull { it.accountType == AccountType.BANK }
            ?: eligibleAccounts.firstOrNull()
    }
    var selectedAccountId by remember(existingSettlement, eligibleAccounts) {
        mutableStateOf(existingSettlement?.accountId ?: defaultAccount?.id)
    }
    var notes by remember(existingSettlement) { mutableStateOf(existingSettlement?.notes ?: "") }

    val fromMember = participants.firstOrNull { it.id == fromId }
    val toMember = participants.firstOrNull { it.id == toId }
    val involvesUser = fromMember?.isCurrentUser == true || toMember?.isCurrentUser == true
    val parsedAmount = MoneyPaise.parseFromRupeeInput(amountInput)
    val samePeople = fromId.isNotBlank() && fromId == toId
    val canSubmit = !isSubmitting &&
        fromId.isNotBlank() &&
        toId.isNotBlank() &&
        !samePeople &&
        parsedAmount != null &&
        parsedAmount.isPositive &&
        (!involvesUser || selectedAccountId != null)

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
                        .imePadding()
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
                            text = if (existingSettlement == null) "Record Settlement" else "Edit Settlement",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }

                    Text("Who Paid (From)", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        participants.forEach { member ->
                            FilterChip(
                                selected = fromId == member.id,
                                onClick = { fromId = member.id },
                                label = { Text(member.name + if (member.isCurrentUser) " (You)" else "") },
                                modifier = Modifier.testTag("settle_from_${member.id}")
                            )
                        }
                    }

                    Text("Paid To (Recipient)", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        participants.forEach { member ->
                            FilterChip(
                                selected = toId == member.id,
                                onClick = { toId = member.id },
                                label = { Text(member.name + if (member.isCurrentUser) " (You)" else "") },
                                modifier = Modifier.testTag("settle_to_${member.id}")
                            )
                        }
                    }

                    if (samePeople) {
                        Text(
                            text = "Payer and recipient must be different people.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Settlement Amount (₹)") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_settle_amount_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date: ${DateTimeUtils.formatShortDate(settlementDateMs)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("split_settle_date_btn")
                        ) {
                            Text("Change Date")
                        }
                    }

                    if (involvesUser) {
                        Text(
                            text = if (fromMember?.isCurrentUser == true) {
                                "Source Account (Deducted via Central Ledger)"
                            } else {
                                "Destination Account (Reimbursement Deposited)"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (eligibleAccounts.isEmpty()) {
                            Text(
                                text = "Create a Bank or Cash account in Finance → Accounts first.",
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
                                eligibleAccounts.forEach { acc ->
                                    FilterChip(
                                        selected = selectedAccountId == acc.id,
                                        onClick = { selectedAccountId = acc.id },
                                        label = { Text("${acc.name} (${MoneyPaise(acc.currentBalancePaise).formatInr()})") },
                                        modifier = Modifier.testTag("settle_account_${acc.id}")
                                    )
                                }
                            }
                        }
                        if (toMember?.isCurrentUser == true) {
                            Text(
                                text = "Recorded as a receivable reimbursement — will not inflate ordinary income reports.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_settle_notes_input")
                    )

                    Button(
                        onClick = {
                            onSubmit(
                                fromId,
                                toId,
                                amountInput,
                                settlementDateMs,
                                if (involvesUser) selectedAccountId else null,
                                notes
                            )
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_settle_submit_btn")
                    ) {
                        Text(if (existingSettlement == null) "Confirm Settlement" else "Update Settlement")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = settlementDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { settlementDateMs = it }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
