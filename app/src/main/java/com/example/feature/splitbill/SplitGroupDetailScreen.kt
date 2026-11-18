package com.example.feature.splitbill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.SplitGroupDetailSnapshot
import com.example.core.database.dao.SplitExpenseWithShares
import com.example.core.database.entity.SettlementDirection
import com.example.core.database.entity.SplitSettlementEntity
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultEmptyStateCard
import com.example.core.designsystem.component.FinVaultInfoRow
import com.example.core.designsystem.component.FinVaultMetricTile
import com.example.core.designsystem.component.FinVaultSectionHeader
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors

@Composable
fun SplitGroupDetailScreen(
    viewModel: SplitGroupDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }
    var confirmDeleteSettlementId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.groupDeleted) {
        if (uiState.groupDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val snapshot = uiState.snapshot

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("split_group_loading")
                )
            }

            snapshot == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FinVaultEmptyStateCard(
                        icon = Icons.Filled.Groups,
                        title = "Group Unavailable",
                        body = "This Split Bill group could not be found.",
                        actionLabel = "Back to Groups",
                        onAction = onNavigateBack
                    )
                }
            }

            else -> {
                val overview = snapshot.overview
                val participantMap = remember(overview.participants) {
                    overview.participants.associateBy { it.id }
                }
                val accountMap = remember(uiState.allAccounts) {
                    uiState.allAccounts.associateBy { it.id }
                }
                val balanceMap = remember(overview.computedSummary.participantBalances) {
                    overview.computedSummary.participantBalances.associateBy { it.participant.id }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("split_group_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = overview.group.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${overview.activeMemberCount} members • Total ${overview.totalExpenses.formatInr()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = viewModel::openAddExpense,
                                modifier = Modifier.testTag("split_group_add_expense_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text("Expense")
                            }
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.testTag("split_group_more_btn")
                                ) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Group options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Group") },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.openEditGroup()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (overview.group.isArchived) "Restore Group" else "Archive Group") },
                                        leadingIcon = {
                                            Icon(
                                                if (overview.group.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            viewModel.toggleGroupArchived(!overview.group.isArchived)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Group") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            confirmDeleteGroup = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 5-Tab Navigation
                    ScrollableTabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        edgePadding = 12.dp,
                        modifier = Modifier.testTag("split_group_tab_row")
                    ) {
                        SplitGroupDetailTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    viewModel.selectTab(tab)
                                },
                                text = { Text(tab.label) },
                                modifier = Modifier.testTag("split_tab_${tab.name.lowercase()}")
                            )
                        }
                    }

                    // Tab Body
                    when (uiState.selectedTab) {
                        SplitGroupDetailTab.OVERVIEW -> OverviewTab(
                            snapshot = snapshot,
                            participantMap = participantMap,
                            onAddExpense = viewModel::openAddExpense,
                            onAddPerson = viewModel::openAddParticipant,
                            onSettleSuggested = { fromId, toId, amountPaise ->
                                viewModel.openRecordSettlement(fromId, toId, amountPaise)
                            },
                            onOpenExpense = viewModel::openExpenseDetail
                        )

                        SplitGroupDetailTab.EXPENSES -> ExpensesTab(
                            expenses = snapshot.expenses,
                            participantMap = participantMap,
                            accountMap = accountMap,
                            onAddExpense = viewModel::openAddExpense,
                            onOpenExpense = viewModel::openExpenseDetail
                        )

                        SplitGroupDetailTab.PEOPLE -> PeopleTab(
                            snapshot = snapshot,
                            balanceMap = balanceMap,
                            onAddPerson = viewModel::openAddParticipant,
                            onOpenPersonDetail = viewModel::openPersonDetail
                        )

                        SplitGroupDetailTab.BALANCES -> BalancesTab(
                            snapshot = snapshot,
                            onSettleSuggested = { fromId, toId, amountPaise ->
                                viewModel.openRecordSettlement(fromId, toId, amountPaise)
                            },
                            onRecordCustomSettlement = { viewModel.openRecordSettlement() }
                        )

                        SplitGroupDetailTab.SETTLEMENTS -> SettlementsTab(
                            settlements = snapshot.settlements,
                            participantMap = participantMap,
                            accountMap = accountMap,
                            onRecordSettlement = { viewModel.openRecordSettlement() },
                            onEditSettlement = viewModel::openEditSettlement,
                            onRequestDeleteSettlement = { confirmDeleteSettlementId = it }
                        )
                    }
                }

                // Active overlays inside Group Detail
                when (val overlay = uiState.activeOverlay) {
                    SplitGroupOverlay.None -> Unit

                    SplitGroupOverlay.EditGroup -> {
                        CreateEditSplitGroupDialog(
                            existingGroup = overview.group,
                            onDismiss = viewModel::dismissOverlay,
                            onSave = { name, iconKey, desc, _ ->
                                viewModel.updateGroup(name, iconKey, desc)
                            }
                        )
                    }

                    SplitGroupOverlay.AddParticipant -> {
                        AddEditParticipantDialog(
                            existing = null,
                            onDismiss = viewModel::dismissOverlay,
                            onSave = { name, phone, email, notes ->
                                viewModel.addParticipant(name, phone, email, notes)
                            }
                        )
                    }

                    is SplitGroupOverlay.EditParticipant -> {
                        AddEditParticipantDialog(
                            existing = overlay.participant,
                            onDismiss = viewModel::dismissOverlay,
                            onSave = { name, phone, email, notes ->
                                viewModel.updateParticipant(overlay.participant.id, name, phone, email, notes)
                            }
                        )
                    }

                    is SplitGroupOverlay.PersonDetail -> {
                        val balance = balanceMap[overlay.participantId]
                        if (balance != null) {
                            SplitPersonDetailSheet(
                                balanceSummary = balance,
                                groupExpenses = snapshot.expenses,
                                groupSettlements = snapshot.settlements,
                                participantMap = participantMap,
                                onDismiss = viewModel::dismissOverlay,
                                onEdit = { viewModel.openEditParticipant(balance.participant) },
                                onRemoveOrArchive = {
                                    viewModel.removeOrArchiveParticipantSafely(balance.participant.id)
                                }
                            )
                        }
                    }

                    is SplitGroupOverlay.ExpenseForm -> {
                        AddEditSplitExpenseSheet(
                            participants = overview.participants.filter { !it.isArchived },
                            eligibleAccounts = uiState.eligibleAccounts,
                            existingExpense = overlay.editingExpense,
                            isSubmitting = uiState.isSubmitting,
                            onDismiss = viewModel::dismissOverlay,
                            onSubmit = { desc, amountInput, dateMs, paidById, accountId, splitSpec, notes ->
                                viewModel.submitExpense(
                                    editingExpenseId = overlay.editingExpense?.expense?.id,
                                    description = desc,
                                    amountInput = amountInput,
                                    expenseDateEpochMs = dateMs,
                                    paidByParticipantId = paidById,
                                    paidFromAccountId = accountId,
                                    splitSpec = splitSpec,
                                    notes = notes
                                )
                            }
                        )
                    }

                    is SplitGroupOverlay.ExpenseDetail -> {
                        val expenseItem = snapshot.expenses.firstOrNull { it.expense.id == overlay.expenseId }
                        if (expenseItem != null) {
                            SplitExpenseDetailSheet(
                                item = expenseItem,
                                participantMap = participantMap,
                                accountMap = accountMap,
                                onDismiss = viewModel::dismissOverlay,
                                onEdit = { viewModel.openEditExpense(expenseItem) },
                                onDelete = { viewModel.deleteExpense(expenseItem.expense.id) }
                            )
                        }
                    }

                    is SplitGroupOverlay.SettlementForm -> {
                        AddEditSplitSettlementSheet(
                            participants = overview.participants.filter { !it.isArchived },
                            eligibleAccounts = uiState.eligibleAccounts,
                            existingSettlement = overlay.editingSettlement,
                            prefillFromId = overlay.prefillFromId,
                            prefillToId = overlay.prefillToId,
                            prefillAmountPaise = overlay.prefillAmountPaise,
                            isSubmitting = uiState.isSubmitting,
                            onDismiss = viewModel::dismissOverlay,
                            onSubmit = { fromId, toId, amountInput, dateMs, accountId, notes ->
                                viewModel.submitSettlement(
                                    editingSettlementId = overlay.editingSettlement?.id,
                                    fromParticipantId = fromId,
                                    toParticipantId = toId,
                                    amountInput = amountInput,
                                    settlementDateEpochMs = dateMs,
                                    accountId = accountId,
                                    notes = notes
                                )
                            }
                        )
                    }
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

    if (confirmDeleteGroup && snapshot != null) {
        val unsettled = snapshot.overview.unsettledAmount
        AlertDialog(
            onDismissRequest = { confirmDeleteGroup = false },
            title = { Text("Delete '${snapshot.overview.group.name}'?") },
            text = {
                Text(
                    if (unsettled.isPositive) {
                        "This group still has ${unsettled.formatInr()} in unsettled balances. Settle all balances first or archive the group instead to keep records consistent."
                    } else {
                        "All balances are settled. Deleting removes this group and its internal split breakdown while keeping posted Finance ledger history intact."
                    }
                )
            },
            confirmButton = {
                if (!unsettled.isPositive) {
                    Button(
                        onClick = {
                            confirmDeleteGroup = false
                            viewModel.deleteGroupSafely()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("confirm_delete_group_btn")
                    ) {
                        Text("Delete Group")
                    }
                } else {
                    Button(
                        onClick = {
                            confirmDeleteGroup = false
                            viewModel.toggleGroupArchived(true)
                        }
                    ) {
                        Text("Archive Instead")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteGroup = false }) { Text("Cancel") }
            }
        )
    }

    confirmDeleteSettlementId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { confirmDeleteSettlementId = null },
            title = { Text("Delete & Reverse Settlement?") },
            text = { Text("Group balances will recalculate and any linked Finance ledger entry will be atomically reversed.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteSettlementId = null
                        viewModel.deleteSettlement(targetId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_settlement_btn")
                ) {
                    Text("Reverse & Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSettlementId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OverviewTab(
    snapshot: SplitGroupDetailSnapshot,
    participantMap: Map<String, com.example.core.database.entity.SplitParticipantEntity>,
    onAddExpense: () -> Unit,
    onAddPerson: () -> Unit,
    onSettleSuggested: (String, String, Long) -> Unit,
    onOpenExpense: (String) -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val overview = snapshot.overview
    val summary = overview.computedSummary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("split_overview_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FinVaultMetricTile(
                    label = "Group Total",
                    value = overview.totalExpenses.formatInr(),
                    icon = Icons.Filled.ReceiptLong,
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                FinVaultMetricTile(
                    label = when {
                        overview.youAreOwed.isPositive -> "You Are Owed"
                        overview.youOwe.isPositive -> "You Owe"
                        else -> "Your Status"
                    },
                    value = when {
                        overview.youAreOwed.isPositive -> overview.youAreOwed.formatInr()
                        overview.youOwe.isPositive -> overview.youOwe.formatInr()
                        else -> MoneyPaise.ZERO.formatInr()
                    },
                    icon = Icons.Filled.CheckCircle,
                    tint = when {
                        overview.youAreOwed.isPositive -> semantic.income
                        overview.youOwe.isPositive -> semantic.expense
                        else -> MaterialTheme.colorScheme.primary
                    },
                    containerColor = when {
                        overview.youAreOwed.isPositive -> semantic.incomeContainer
                        overview.youOwe.isPositive -> semantic.expenseContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.weight(1f)
                )
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
                    onClick = onAddExpense,
                    modifier = Modifier.testTag("overview_add_expense_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("Add Expense")
                }
                OutlinedButton(
                    onClick = onAddPerson,
                    modifier = Modifier.testTag("overview_add_person_btn")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Text("Add Person")
                }
            }
        }

        item {
            FinVaultSectionHeader(
                title = "Suggested Settlements",
                trailingText = if (summary.simplifiedSettlements.isEmpty()) "All settled" else "${summary.simplifiedSettlements.size} transfers"
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (summary.simplifiedSettlements.isEmpty()) {
                FinVaultEmptyStateCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "All Group Balances Settled",
                    body = "No pending transfers required between members."
                )
            } else {
                FinVaultCard(modifier = Modifier.testTag("overview_simplified_settlements_card")) {
                    summary.simplifiedSettlements.forEachIndexed { idx, suggestion ->
                        if (idx > 0) {
                            HorizontalDivider(
                                color = semantic.cardBorder,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${suggestion.fromParticipant.name} pays ${suggestion.toParticipant.name}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = MoneyPaise(suggestion.amountPaise).formatInr(),
                                    style = FinVaultNumericTypography.rowAmount,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    onSettleSuggested(
                                        suggestion.fromParticipant.id,
                                        suggestion.toParticipant.id,
                                        suggestion.amountPaise
                                    )
                                },
                                modifier = Modifier.testTag("suggest_settle_btn_${suggestion.fromParticipant.id}_${suggestion.toParticipant.id}")
                            ) {
                                Text("Settle")
                            }
                        }
                    }
                }
            }
        }

        item {
            FinVaultSectionHeader(
                title = "Recent Group Expenses",
                trailingText = "${snapshot.expenses.size} total"
            )
        }

        if (snapshot.expenses.isEmpty()) {
            item {
                FinVaultEmptyStateCard(
                    icon = Icons.Filled.ReceiptLong,
                    title = "No Expenses Yet",
                    body = "Add a group expense and choose Paid by Me or another member."
                )
            }
        } else {
            items(
                items = snapshot.expenses.take(5),
                key = { "overview_exp_${it.expense.id}" }
            ) { item ->
                val payerName = participantMap[item.expense.paidByParticipantId]?.name ?: "Member"
                FinVaultCard(
                    modifier = Modifier
                        .clickable { onOpenExpense(item.expense.id) }
                        .testTag("overview_expense_row_${item.expense.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.expense.description, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Paid by $payerName • ${DateTimeUtils.formatShortDate(item.expense.expenseDateEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = MoneyPaise(item.expense.totalAmountPaise).formatInr(),
                            style = FinVaultNumericTypography.rowAmount
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesTab(
    expenses: List<SplitExpenseWithShares>,
    participantMap: Map<String, com.example.core.database.entity.SplitParticipantEntity>,
    accountMap: Map<String, com.example.core.database.entity.AccountEntity>,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("split_expenses_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (expenses.isEmpty()) {
            item {
                FinVaultEmptyStateCard(
                    icon = Icons.Filled.ReceiptLong,
                    title = "No Group Expenses",
                    body = "Add shared expenses split equally, by exact rupee amount, percentage, or custom shares.",
                    actionLabel = "Add Expense",
                    onAction = onAddExpense
                )
            }
        } else {
            items(items = expenses, key = { it.expense.id }) { item ->
                val exp = item.expense
                val payer = participantMap[exp.paidByParticipantId]
                val accountName = exp.paidFromAccountId?.let { accountMap[it]?.name }
                FinVaultCard(
                    modifier = Modifier
                        .clickable { onOpenExpense(exp.id) }
                        .testTag("split_expense_card_${exp.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exp.description,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Paid by ${payer?.name ?: "Member"} • ${DateTimeUtils.formatShortDate(exp.expenseDateEpochMs)} • ${item.shares.size} shares",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyPaise(exp.totalAmountPaise).formatInr(),
                                style = FinVaultNumericTypography.rowAmount
                            )
                            FinVaultStatusPill(
                                text = if (accountName != null) "Ledger: $accountName" else exp.splitMethod.name.replace('_', ' ')
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(
    snapshot: SplitGroupDetailSnapshot,
    balanceMap: Map<String, com.example.core.domain.split.ParticipantBalanceSummary>,
    onAddPerson: () -> Unit,
    onOpenPersonDetail: (String) -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val activePeople = snapshot.overview.participants.filter { !it.isArchived }
    val archivedPeople = snapshot.overview.participants.filter { it.isArchived }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("split_people_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap any person for detailed balances, history & edits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onAddPerson,
                    modifier = Modifier.testTag("split_add_person_btn")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Text("Add Person")
                }
            }
        }

        // Strict requirement: NO Edit icon button on person cards; tapping card opens Person Detail
        items(items = activePeople, key = { it.id }) { person ->
            val bal = balanceMap[person.id]
            val net = bal?.netBalancePaise ?: 0L
            val statusText = when {
                net > 0L -> "To receive ${MoneyPaise(net).formatInr()}"
                net < 0L -> "Owes ${MoneyPaise(-net).formatInr()}"
                else -> "Settled"
            }
            val pillContainer = when {
                net > 0L -> semantic.incomeContainer
                net < 0L -> semantic.expenseContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val pillContent = when {
                net > 0L -> semantic.income
                net < 0L -> semantic.expense
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            FinVaultCard(
                onClick = { onOpenPersonDetail(person.id) },
                modifier = Modifier.testTag("split_person_card_${person.id}")
            ) {
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
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
                                    text = person.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (person.isCurrentUser) {
                                    FinVaultStatusPill(text = "You")
                                }
                            }
                            val contactInfo = listOfNotNull(person.phone, person.email).joinToString(" • ")
                            if (contactInfo.isNotBlank()) {
                                Text(
                                    text = contactInfo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    FinVaultStatusPill(
                        text = statusText,
                        containerColor = pillContainer,
                        contentColor = pillContent
                    )
                }
            }
        }

        if (archivedPeople.isNotEmpty()) {
            item {
                FinVaultSectionHeader(
                    title = "Archived Members",
                    trailingText = "${archivedPeople.size}"
                )
            }
            items(items = archivedPeople, key = { "arch_${it.id}" }) { person ->
                FinVaultCard(
                    onClick = { onOpenPersonDetail(person.id) },
                    modifier = Modifier.testTag("split_archived_person_card_${person.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(person.name, style = MaterialTheme.typography.titleSmall)
                        FinVaultStatusPill(text = "Archived")
                    }
                }
            }
        }
    }
}

@Composable
private fun BalancesTab(
    snapshot: SplitGroupDetailSnapshot,
    onSettleSuggested: (String, String, Long) -> Unit,
    onRecordCustomSettlement: () -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val summary = snapshot.overview.computedSummary

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("split_balances_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FinVaultSectionHeader(
                title = "Participant Net Balances",
                trailingText = "Unsettled ${MoneyPaise(summary.groupUnsettledAmountPaise).formatInr()}"
            )
        }

        items(items = summary.participantBalances, key = { "bal_${it.participant.id}" }) { bal ->
            val net = bal.netBalancePaise
            FinVaultCard(modifier = Modifier.testTag("balance_card_${bal.participant.id}")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bal.participant.name + if (bal.participant.isCurrentUser) " (You)" else "",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            net > 0L -> "+${MoneyPaise(net).formatInr()} owed"
                            net < 0L -> "-${MoneyPaise(-net).formatInr()} owes"
                            else -> "₹0.00 settled"
                        },
                        style = FinVaultNumericTypography.rowAmount,
                        color = when {
                            net > 0L -> semantic.income
                            net < 0L -> semantic.expense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Paid ${MoneyPaise(bal.totalPaidExpensesPaise).formatInr()} • Share ${MoneyPaise(bal.totalExpenseSharePaise).formatInr()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Simplified Minimum Transfers", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onRecordCustomSettlement,
                    modifier = Modifier.testTag("balances_custom_settle_btn")
                ) {
                    Text("Custom Settle")
                }
            }
        }

        if (summary.simplifiedSettlements.isEmpty()) {
            item {
                FinVaultEmptyStateCard(
                    icon = Icons.Filled.CheckCircle,
                    title = "Group Completely Settled",
                    body = "All participant balances net to ₹0.00."
                )
            }
        } else {
            items(
                items = summary.simplifiedSettlements,
                key = { "sim_${it.fromParticipant.id}_${it.toParticipant.id}" }
            ) { suggestion ->
                FinVaultCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${suggestion.fromParticipant.name} → ${suggestion.toParticipant.name}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = MoneyPaise(suggestion.amountPaise).formatInr(),
                                style = FinVaultNumericTypography.rowAmount,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = {
                                onSettleSuggested(
                                    suggestion.fromParticipant.id,
                                    suggestion.toParticipant.id,
                                    suggestion.amountPaise
                                )
                            }
                        ) {
                            Text("Settle Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettlementsTab(
    settlements: List<SplitSettlementEntity>,
    participantMap: Map<String, com.example.core.database.entity.SplitParticipantEntity>,
    accountMap: Map<String, com.example.core.database.entity.AccountEntity>,
    onRecordSettlement: () -> Unit,
    onEditSettlement: (SplitSettlementEntity) -> Unit,
    onRequestDeleteSettlement: (String) -> Unit
) {
    val semantic = LocalFinVaultColors.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("split_settlements_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settlement History",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = onRecordSettlement,
                    modifier = Modifier.testTag("split_record_settlement_btn")
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null)
                    Text("Settle")
                }
            }
        }

        if (settlements.isEmpty()) {
            item {
                FinVaultEmptyStateCard(
                    icon = Icons.Filled.AccountBalance,
                    title = "No Settlements Recorded",
                    body = "Record partial or full repayments to update group balances and sync with your Bank or Cash accounts.",
                    actionLabel = "Record Settlement",
                    onAction = onRecordSettlement
                )
            }
        } else {
            items(items = settlements, key = { it.id }) { settlement ->
                val fromName = participantMap[settlement.fromParticipantId]?.name ?: "Member"
                val toName = participantMap[settlement.toParticipantId]?.name ?: "Member"
                val accName = settlement.accountId?.let { accountMap[it]?.name }
                val directionLabel = when (settlement.direction) {
                    SettlementDirection.OUTGOING_PAYMENT -> "Paid by You"
                    SettlementDirection.INCOMING_REIMBURSEMENT -> "Reimbursement"
                    SettlementDirection.INTERNAL -> "Group Transfer"
                }

                FinVaultCard(modifier = Modifier.testTag("split_settlement_card_${settlement.id}")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$fromName paid $toName",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${DateTimeUtils.formatShortDate(settlement.settlementDateEpochMs)} • $directionLabel" +
                                    if (accName != null) " • $accName" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!settlement.notes.isNullOrBlank()) {
                                Text(
                                    text = settlement.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyPaise(settlement.amountPaise).formatInr(),
                                style = FinVaultNumericTypography.rowAmount,
                                color = when (settlement.direction) {
                                    SettlementDirection.INCOMING_REIMBURSEMENT -> semantic.income
                                    SettlementDirection.OUTGOING_PAYMENT -> semantic.expense
                                    SettlementDirection.INTERNAL -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Row {
                                IconButton(
                                    onClick = { onEditSettlement(settlement) },
                                    modifier = Modifier.testTag("edit_settlement_btn_${settlement.id}")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit settlement")
                                }
                                IconButton(
                                    onClick = { onRequestDeleteSettlement(settlement.id) },
                                    modifier = Modifier.testTag("delete_settlement_btn_${settlement.id}")
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete settlement")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
