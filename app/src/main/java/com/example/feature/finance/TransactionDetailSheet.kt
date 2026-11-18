package com.example.feature.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors

@Composable
fun TransactionDetailSheet(
    item: LedgerTransactionWithEntries,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onEdit: (LedgerTransactionWithEntries) -> Unit,
    onDelete: (String) -> Unit
) {
    val tx = item.transaction
    val semantic = LocalFinVaultColors.current
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryName = remember(categories, tx.categoryId) {
        tx.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: "Uncategorized"
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val isSplitSynced = tx.sourceModule == "SPLIT_BILL"
    val canEdit = !tx.isVoided && !isSplitSynced && tx.txType in setOf(
        LedgerTransactionType.EXPENSE,
        LedgerTransactionType.CREDIT_CARD_EXPENSE,
        LedgerTransactionType.INCOME,
        LedgerTransactionType.TRANSFER,
        LedgerTransactionType.CREDIT_CARD_PAYMENT
    )
    val canDelete = !tx.isVoided && !isSplitSynced &&
        tx.txType != LedgerTransactionType.OPENING_BALANCE &&
        tx.txType != LedgerTransactionType.REVERSAL

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tx.description,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("tx_detail_close_button")
                        ) {
                            Text("Close")
                        }
                    }
                    Text(
                        text = MoneyPaise(tx.totalAmountPaise).formatInr(),
                        style = FinVaultNumericTypography.heroAmount,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${tx.txType.name.replace('_', ' ')} • ${DateTimeUtils.formatDateTime(tx.txTimestampEpochMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FinVaultCard {
                        DetailRow("Category", categoryName)
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        DetailRow("Payment Method", tx.paymentMethod.name.replace('_', ' '))
                        HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                        DetailRow("Module", tx.sourceModule)
                        if (!tx.notes.isNullOrBlank()) {
                            HorizontalDivider(color = semantic.cardBorder, modifier = Modifier.padding(vertical = 8.dp))
                            DetailRow("Notes", tx.notes)
                        }
                    }

                    Text("Double-Entry Audit Legs", style = MaterialTheme.typography.titleSmall)
                    FinVaultCard {
                        item.entries.forEachIndexed { idx, entry ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = semantic.cardBorder,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                            val targetName = entry.accountId?.let { accountMap[it]?.name }
                                ?: entry.ledgerClass.name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${entry.entrySide.name}: $targetName",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = MoneyPaise(entry.amountPaise).formatInr(),
                                    style = FinVaultNumericTypography.rowAmount
                                )
                            }
                        }
                    }

                    if (!canEdit || !canDelete) {
                        Text(
                            text = when {
                                isSplitSynced -> "Synced with Split Bill. Edit or delete this entry in Split Bill to keep group shares and balances accurate."
                                !canDelete -> "System opening balance and reversal entries are immutable in the ledger."
                                else -> "Specialized entries must be reversed rather than edited directly."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (canEdit || canDelete) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (canDelete) {
                                OutlinedButton(
                                    onClick = { confirmDelete = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tx_detail_delete_button")
                                ) {
                                    Text("Delete")
                                }
                            }
                            if (canEdit) {
                                Button(
                                    onClick = { onEdit(item) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tx_detail_edit_button")
                                ) {
                                    Text("Edit")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Reverse & Delete Transaction?") },
            text = { Text("This posts an atomic reversal entry in the ledger and restores affected account balances.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDelete(tx.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("tx_confirm_delete_button")
                ) {
                    Text("Reverse & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
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
