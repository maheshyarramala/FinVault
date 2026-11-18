package com.example.feature.debt

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.MoneyPaise
import com.example.core.database.entity.DebtType
import com.example.core.database.entity.DebtInterestType

@Composable
fun DebtManagementScreen(
    viewModel: DebtManagementViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var debtType by remember { mutableStateOf(DebtType.LENT) }
    var interestType by remember { mutableStateOf(DebtInterestType.NONE) }
    var annualRate by remember { mutableStateOf("0") }
    var fixedInterest by remember { mutableStateOf("0") }
    var selectedAccountId by remember { mutableStateOf("") }

    LaunchedEffect(state.accounts) {
        if (selectedAccountId.isBlank() && state.accounts.isNotEmpty()) {
            selectedAccountId = state.accounts.first().id
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
                        accounts = state.accounts.map { it.id to it.name },
                        selectedId = selectedAccountId,
                        onSelect = { selectedAccountId = it }
                    )
                    Button(
                        onClick = {
                            viewModel.createDebt(
                                title, person, amount, debtType, selectedAccountId,
                                interestType, annualRate, fixedInterest
                            )
                            title = ""
                            person = ""
                            amount = ""
                        },
                        enabled = title.isNotBlank() && person.isNotBlank() &&
                            amount.isNotBlank() && selectedAccountId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("debt_create_button")
                    ) { Text("Save debt") }
                }
            }
        }
        item {
            Text("Your debts", style = MaterialTheme.typography.titleLarge)
        }
        if (state.debts.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "No debts recorded yet. Add money you have lent or borrowed above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
        items(state.debts, key = { it.id }) { debt ->
            var repayPrincipal by remember(debt.id) { mutableStateOf("") }
            var repayInterest by remember(debt.id) { mutableStateOf("0") }
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
                        "Outstanding: ${MoneyPaise(debt.outstandingPrincipalPaise).formatInr()}",
                        style = MaterialTheme.typography.titleMedium
                    )
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
