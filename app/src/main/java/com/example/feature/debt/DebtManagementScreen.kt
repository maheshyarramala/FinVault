package com.example.feature.debt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.MoneyPaise
import com.example.core.database.entity.DebtType
import com.example.core.database.entity.DebtInterestType
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.DebtRepaymentEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DebtManagementScreen(
    viewModel: DebtManagementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsAllowed = granted }
    var searchQuery by remember { mutableStateOf("") }
    var debtFilter by remember { mutableStateOf("ACTIVE") }
    var title by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var debtType by remember { mutableStateOf(DebtType.LENT) }
    var interestType by remember { mutableStateOf(DebtInterestType.NONE) }
    var annualRate by remember { mutableStateOf("0") }
    var fixedInterest by remember { mutableStateOf("0") }
    var selectedAccountId by remember { mutableStateOf("") }
    val debtAccounts = state.accounts.filter { it.accountType != AccountType.CREDIT_CARD }

    LaunchedEffect(debtAccounts) {
        if (selectedAccountId.isBlank() && debtAccounts.isNotEmpty()) {
            selectedAccountId = debtAccounts.first().id
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("debt_management_screen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Debt Management", style = MaterialTheme.typography.headlineSmall)
                    Text("Track money lent, borrowed and repayments", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onNavigateBack) { Text("Back") }
            }
        }
        if (state.message != null) item {
            Text(state.message!!, color = MaterialTheme.colorScheme.primary)
        }
        if (state.error != null) item {
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add debt", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = debtType == DebtType.LENT,
                            onClick = { debtType = DebtType.LENT },
                            label = { Text("I lent") }
                        )
                        FilterChip(
                            selected = debtType == DebtType.BORROWED,
                            onClick = { debtType = DebtType.BORROWED },
                            label = { Text("I borrowed") }
                        )
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Debt title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = person,
                        onValueChange = { person = it },
                        label = { Text("Person / lender") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Principal (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due date (YYYY-MM-DD, optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text("Interest terms", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DebtInterestType.entries.forEach { option ->
                            FilterChip(
                                selected = interestType == option,
                                onClick = { interestType = option },
                                label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    if (interestType == DebtInterestType.SIMPLE || interestType == DebtInterestType.COMPOUND) {
                        OutlinedTextField(
                            value = annualRate,
                            onValueChange = { annualRate = it },
                            label = { Text("Annual rate (%)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    if (interestType == DebtInterestType.FIXED) {
                        OutlinedTextField(
                            value = fixedInterest,
                            onValueChange = { fixedInterest = it },
                            label = { Text("Fixed interest (₹)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Text("Account used for the original money movement", style = MaterialTheme.typography.labelMedium)
                    AccountChoices(
                        accounts = debtAccounts.map { it.id to it.name },
                        selectedId = selectedAccountId,
                        onSelect = { selectedAccountId = it }
                    )
                    Button(
                        onClick = {
                            viewModel.createDebt(
                                title, person, amount, debtType, selectedAccountId,
                                interestType, annualRate, fixedInterest, dueDate
                            )
                            title = ""
                            person = ""
                            amount = ""
                            dueDate = ""
                        },
                        enabled = title.isNotBlank() && person.isNotBlank() &&
                            amount.isNotBlank() && selectedAccountId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("debt_create_button")
                    ) { Text("Save debt") }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your debts", style = MaterialTheme.typography.titleLarge)
                if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                    TextButton(onClick = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) { Text("Enable reminders") }
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search debts or people") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ACTIVE", "SETTLED", "ALL").forEach { filter ->
                    FilterChip(selected = debtFilter == filter, onClick = { debtFilter = filter },
                        label = { Text(filter.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                Text("Due dates remain visible in the app, but Android notifications are disabled until permission is granted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        val visibleDebts = state.debts.filter { debt ->
            (debtFilter == "ALL" || debt.status.name == debtFilter) &&
                (searchQuery.isBlank() || debt.title.contains(searchQuery, true) || debt.personName.contains(searchQuery, true))
        }
        if (visibleDebts.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No debts recorded yet. Add money you have lent or borrowed above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(visibleDebts, key = { it.id }) { debt ->
            val repayments by viewModel.observeRepayments(debt.id).collectAsState(initial = emptyList())
            var showDetails by remember(debt.id) { mutableStateOf(false) }
            var showEditDebt by remember(debt.id) { mutableStateOf(false) }
            var editTitle by remember(debt.id) { mutableStateOf(debt.title) }
            var editPrincipal by remember(debt.id) { mutableStateOf(BigDecimal(debt.principalPaise).movePointLeft(2).toPlainString()) }
            var editPerson by remember(debt.id) { mutableStateOf(debt.personName) }
            var editDueDate by remember(debt.id) {
                mutableStateOf(debt.dueEpochMs?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                } ?: "")
            }
            var editNotes by remember(debt.id) { mutableStateOf(debt.notes.orEmpty()) }
            var editingRepayment by remember(debt.id) { mutableStateOf<DebtRepaymentEntity?>(null) }
            var deletingRepayment by remember(debt.id) { mutableStateOf<DebtRepaymentEntity?>(null) }
            var editRepaymentPrincipal by remember(debt.id) { mutableStateOf("") }
            var editRepaymentInterest by remember(debt.id) { mutableStateOf("0") }
            var editRepaymentAccountId by remember(debt.id) { mutableStateOf("") }
            var repayPrincipal by remember(debt.id) { mutableStateOf("") }
            var repayInterest by remember(debt.id) { mutableStateOf("0") }
            var showArchiveConfirm by remember(debt.id) { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().testTag("debt_card_${debt.id}")) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(debt.title, style = MaterialTheme.typography.titleMedium)
                        Text(debt.status.name, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${debt.personName} • ${if (debt.type == DebtType.LENT) "Lent" else "Borrowed"}")
                    Text("Principal: ${MoneyPaise(debt.principalPaise).formatInr()}")
                    Text(
                        "Outstanding principal: ${MoneyPaise(debt.outstandingPrincipalPaise).formatInr()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Unpaid interest: ${MoneyPaise(debt.interestOutstandingPaise).formatInr()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide details" else "Details & history")
                        }
                        if (debt.status.name != "ARCHIVED") {
                            TextButton(onClick = { showEditDebt = true }) { Text("Edit debt") }
                        }
                    }
                    if (showDetails) {
                        Text("Repayment history", style = MaterialTheme.typography.titleSmall)
                        if (repayments.isEmpty()) {
                            Text("No repayments recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            repayments.forEachIndexed { index, repayment ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        "${Instant.ofEpochMilli(repayment.paidAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()} • ${MoneyPaise(repayment.totalPaise).formatInr()}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "Principal ${MoneyPaise(repayment.principalPaise).formatInr()} • Interest ${MoneyPaise(repayment.interestPaise).formatInr()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (index == 0 && debt.status.name != "ARCHIVED") {
                                        Row {
                                            TextButton(onClick = {
                                                editingRepayment = repayment
                                                editRepaymentAccountId = repayment.paymentAccountId
                                                editRepaymentPrincipal = BigDecimal(repayment.principalPaise).movePointLeft(2).toPlainString()
                                                editRepaymentInterest = BigDecimal(repayment.interestPaise).movePointLeft(2).toPlainString()
                                            }) { Text("Edit latest") }
                                            TextButton(onClick = { deletingRepayment = repayment }) { Text("Delete latest") }
                                        }
                                    }
                                }
                                if (index < repayments.lastIndex) androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    }
                    debt.dueEpochMs?.let { dueEpochMs ->
                        val dueLocalDate = Instant.ofEpochMilli(dueEpochMs)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val overdue = debt.status.name == "ACTIVE" &&
                            (debt.outstandingPrincipalPaise > 0L || debt.interestOutstandingPaise > 0L) && dueLocalDate.isBefore(LocalDate.now())
                        Text(
                            text = if (overdue) "Overdue • Due ${dueLocalDate}" else "Due ${dueLocalDate}",
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (debt.status.name == "ACTIVE") {
                        Spacer(Modifier.height(4.dp))
                        Text("Record repayment", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = repayPrincipal,
                            onValueChange = { repayPrincipal = it },
                            label = { Text("Principal (₹)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = repayInterest,
                            onValueChange = { repayInterest = it },
                            label = { Text("Interest (₹)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.recordRepayment(
                                    debt.id,
                                    repayPrincipal,
                                    repayInterest,
                                    selectedAccountId
                                )
                                repayPrincipal = ""
                                repayInterest = "0"
                            },
                            enabled = repayPrincipal.isNotBlank() && selectedAccountId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (debt.type == DebtType.LENT) "Record received" else "Record paid") }
                    }
                    if (debt.status.name != "ARCHIVED") {
                        TextButton(onClick = { showArchiveConfirm = true }) { Text("Archive debt") }
                    }
                    if (showEditDebt) {
                        AlertDialog(
                            onDismissRequest = { showEditDebt = false },
                            title = { Text("Edit debt details") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Title") })
                                    OutlinedTextField(editPrincipal, { editPrincipal = it }, label = { Text("Principal (₹)") })
                                    OutlinedTextField(editPerson, { editPerson = it }, label = { Text("Person") })
                                    OutlinedTextField(editDueDate, { editDueDate = it }, label = { Text("Due date (YYYY-MM-DD)") })
                                    OutlinedTextField(editNotes, { editNotes = it }, label = { Text("Notes") })
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.updateDebtDetails(debt.id, editTitle, editPerson, editDueDate, editNotes, editPrincipal)
                                    showEditDebt = false
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { showEditDebt = false }) { Text("Cancel") } }
                        )
                    }
                    editingRepayment?.let { repayment ->
                        AlertDialog(
                            onDismissRequest = { editingRepayment = null },
                            title = { Text("Edit latest repayment") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(editRepaymentPrincipal, { editRepaymentPrincipal = it }, label = { Text("Principal (₹)") })
                                    OutlinedTextField(editRepaymentInterest, { editRepaymentInterest = it }, label = { Text("Interest (₹)") })
                                    Text("Only the most recent repayment is editable to protect later interest calculations.",
                                        style = MaterialTheme.typography.bodySmall)
                                    Text("Receive / pay through account", style = MaterialTheme.typography.labelMedium)
                                    AccountChoices(
                                        accounts = debtAccounts.map { it.id to it.name },
                                        selectedId = editRepaymentAccountId,
                                        onSelect = { editRepaymentAccountId = it }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.editLatestRepayment(
                                        repayment.id, editRepaymentPrincipal, editRepaymentInterest,
                                        editRepaymentAccountId
                                    )
                                    editingRepayment = null
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { editingRepayment = null }) { Text("Cancel") } }
                        )
                    }
                    deletingRepayment?.let { repayment ->
                        AlertDialog(
                            onDismissRequest = { deletingRepayment = null },
                            title = { Text("Delete repayment?") },
                            text = { Text("The linked Finance transaction will be reversed and the outstanding debt recalculated.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.deleteLatestRepayment(repayment.id)
                                    deletingRepayment = null
                                }) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { deletingRepayment = null }) { Text("Cancel") } }
                        )
                    }
                    if (showArchiveConfirm) {
                        AlertDialog(
                            onDismissRequest = { showArchiveConfirm = false },
                            title = { Text("Archive this debt?") },
                            text = {
                                Text("This will reverse the original debt movement and all repayments in Finance, while keeping the debt history archived.")
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showArchiveConfirm = false
                                        viewModel.archiveDebt(debt.id)
                                    }
                                ) { Text("Archive") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChoices(
    accounts: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    if (accounts.isEmpty()) {
        Text("Create a Bank or Cash account in Finance first.", color = MaterialTheme.colorScheme.error)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            accounts.forEach { (id, name) ->
                FilterChip(
                    selected = selectedId == id,
                    onClick = { onSelect(id) },
                    label = { Text(name) }
                )
            }
        }
    }
}
