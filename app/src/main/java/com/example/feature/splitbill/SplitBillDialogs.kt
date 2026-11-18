package com.example.feature.splitbill

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.SplitExpenseWithShares
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.SplitGroupEntity
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors
import com.example.core.domain.split.ParticipantBalanceSummary

@Composable
fun CreateEditSplitGroupDialog(
    existingGroup: SplitGroupEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, description: String, initialMembersRaw: String) -> Unit
) {
    var name by remember(existingGroup) { mutableStateOf(existingGroup?.name ?: "") }
    var description by remember(existingGroup) { mutableStateOf(existingGroup?.description ?: "") }
    var iconKey by remember(existingGroup) { mutableStateOf(existingGroup?.iconKey ?: "group") }
    var initialMembers by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingGroup == null) "Create Split Group" else "Edit Split Group") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name (e.g. Goa Trip)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_group_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_group_desc_input")
                )
                Text("Group Icon", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    splitGroupIconOptions.forEach { option ->
                        FilterChip(
                            selected = iconKey == option.key,
                            onClick = { iconKey = option.key },
                            leadingIcon = { Icon(option.icon, contentDescription = null) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("split_group_icon_${option.key}")
                        )
                    }
                }
                if (existingGroup == null) {
                    OutlinedTextField(
                        value = initialMembers,
                        onValueChange = { initialMembers = it },
                        label = { Text("Initial Members (comma-separated)") },
                        placeholder = { Text("Aarav, Priya, Kabir") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_group_members_input")
                    )
                    Text(
                        text = "'Me' is added automatically as the current user.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, iconKey, description, initialMembers) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("split_group_save_submit")
            ) {
                Text(if (existingGroup == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddEditParticipantDialog(
    existing: SplitParticipantEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, email: String, notes: String) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var email by remember(existing) { mutableStateOf(existing?.email ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Participant" else "Edit Participant") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_person_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_person_phone_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_person_email_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("split_person_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, email, notes) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("split_person_save_submit")
            ) {
                Text(if (existing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SplitPersonDetailSheet(
    balanceSummary: ParticipantBalanceSummary,
    groupExpenses: List<SplitExpenseWithShares>,
    groupSettlements: List<SplitSettlementEntity>,
    participantMap: Map<String, SplitParticipantEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemoveOrArchive: () -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val person = balanceSummary.participant
    var confirmRemove by remember { mutableStateOf(false) }

    val relatedExpenses = remember(groupExpenses, person.id) {
        groupExpenses.filter { expItem ->
            expItem.expense.paidByParticipantId == person.id ||
                expItem.shares.any { it.participantId == person.id }
        }
    }
    val relatedSettlements = remember(groupSettlements, person.id) {
        groupSettlements.filter {
            it.fromParticipantId == person.id || it.toParticipantId == person.id
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person.name + if (person.isCurrentUser) " (You)" else "",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            val contact = listOfNotNull(person.phone, person.email).joinToString(" • ")
                            if (contact.isNotBlank()) {
                                Text(
                                    text = contact,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("person_detail_close_btn")
                        ) {
                            Text("Close")
                        }
                    }

                    FinVaultCard {
                        SheetDetailRow("Total Paid", MoneyPaise(balanceSummary.totalPaidExpensesPaise).formatInr())
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SheetDetailRow("Total Share", MoneyPaise(balanceSummary.totalExpenseSharePaise).formatInr())
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SheetDetailRow("Amount Owed", MoneyPaise(balanceSummary.amountOwedPaise).formatInr())
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SheetDetailRow("Amount to Receive", MoneyPaise(balanceSummary.amountToReceivePaise).formatInr())
                        if (!person.notes.isNullOrBlank()) {
                            HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                            SheetDetailRow("Notes", person.notes)
                        }
                    }

                    Text("Transaction History", style = MaterialTheme.typography.titleSmall)
                    if (relatedExpenses.isEmpty() && relatedSettlements.isEmpty()) {
                        Text(
                            text = "No split expenses or settlements recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FinVaultCard {
                            relatedExpenses.forEachIndexed { idx, item ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = semantic.cardBorder,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                                val paidByPerson = item.expense.paidByParticipantId == person.id
                                val share = item.shares.firstOrNull { it.participantId == person.id }?.shareAmountPaise ?: 0L
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.expense.description, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${DateTimeUtils.formatShortDate(item.expense.expenseDateEpochMs)} • " +
                                                if (paidByPerson) "Paid ${MoneyPaise(item.expense.totalAmountPaise).formatInr()}" else "Share ${MoneyPaise(share).formatInr()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = MoneyPaise(share).formatInr(),
                                        style = FinVaultNumericTypography.rowAmount
                                    )
                                }
                            }
                            relatedSettlements.forEachIndexed { idx, set ->
                                if (idx > 0 || relatedExpenses.isNotEmpty()) {
                                    HorizontalDivider(
                                        color = semantic.cardBorder,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                                val fromName = participantMap[set.fromParticipantId]?.name ?: "Member"
                                val toName = participantMap[set.toParticipantId]?.name ?: "Member"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Settlement: $fromName → $toName", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = DateTimeUtils.formatShortDate(set.settlementDateEpochMs),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = MoneyPaise(set.amountPaise).formatInr(),
                                        style = FinVaultNumericTypography.rowAmount,
                                        color = semantic.income
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!person.isCurrentUser) {
                            OutlinedButton(
                                onClick = { confirmRemove = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("person_detail_delete_btn")
                            ) {
                                Text(if (relatedExpenses.isEmpty() && relatedSettlements.isEmpty()) "Delete" else "Archive")
                            }
                        }
                        Button(
                            onClick = onEdit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("person_detail_edit_btn")
                        ) {
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        val hasActivity = relatedExpenses.isNotEmpty() || relatedSettlements.isNotEmpty()
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (hasActivity) "Archive ${person.name}?" else "Delete ${person.name}?") },
            text = {
                Text(
                    if (balanceSummary.netBalancePaise != 0L) {
                        "${person.name} still has an unsettled balance. Settle balances to ₹0.00 before deleting or archiving."
                    } else if (hasActivity) {
                        "${person.name} is referenced by historical splits and will be safely archived while keeping audit totals intact."
                    } else {
                        "Remove ${person.name} from this group?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemove = false
                        onRemoveOrArchive()
                    },
                    enabled = balanceSummary.netBalancePaise == 0L,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_person_remove_btn")
                ) {
                    Text(if (hasActivity) "Archive" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SplitExpenseDetailSheet(
    item: SplitExpenseWithShares,
    participantMap: Map<String, SplitParticipantEntity>,
    accountMap: Map<String, AccountEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val exp = item.expense
    val semantic = LocalFinVaultColors.current
    val payer = participantMap[exp.paidByParticipantId]
    val account = exp.paidFromAccountId?.let { accountMap[it] }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = exp.description,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("split_exp_detail_close_btn")
                        ) {
                            Text("Close")
                        }
                    }
                    Text(
                        text = MoneyPaise(exp.totalAmountPaise).formatInr(),
                        style = FinVaultNumericTypography.heroAmount,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FinVaultCard {
                        SheetDetailRow("Date", DateTimeUtils.formatDateTime(exp.expenseDateEpochMs))
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SheetDetailRow("Paid By", payer?.name ?: "Member")
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        SheetDetailRow("Split Method", exp.splitMethod.name.replace('_', ' '))
                        if (account != null) {
                            HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                            SheetDetailRow("Paid From Account", account.name)
                        }
                        if (exp.ledgerTransactionId != null) {
                            HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Finance Ledger", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FinVaultStatusPill(text = "Synced (${exp.ledgerTransactionId.take(8)})")
                            }
                        }
                        if (!exp.notes.isNullOrBlank()) {
                            HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                            SheetDetailRow("Notes", exp.notes)
                        }
                    }

                    Text("Participant Breakdown", style = MaterialTheme.typography.titleSmall)
                    FinVaultCard {
                        item.shares.forEachIndexed { idx, share ->
                            if (idx > 0) {
                                HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            val memberName = participantMap[share.participantId]?.name ?: "Member"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(memberName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = MoneyPaise(share.shareAmountPaise).formatInr(),
                                    style = FinVaultNumericTypography.rowAmount
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("split_exp_detail_delete_btn")
                        ) {
                            Text("Delete")
                        }
                        Button(
                            onClick = onEdit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("split_exp_detail_edit_btn")
                        ) {
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Split Expense?") },
            text = {
                Text(
                    if (exp.ledgerTransactionId != null) {
                        "This removes the group expense and atomically reverses its linked Finance ledger transaction and account balance."
                    } else {
                        "This removes the group expense and recalculates all group member balances."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("split_exp_confirm_delete_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SheetDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
