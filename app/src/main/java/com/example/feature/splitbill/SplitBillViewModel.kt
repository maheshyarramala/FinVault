package com.example.feature.splitbill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.SplitBillPortfolioSummary
import com.example.core.data.repository.SplitBillRepository
import com.example.core.data.repository.SplitGroupDetailSnapshot
import com.example.core.data.repository.SplitGroupOverview
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.domain.split.SplitInputSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SplitBillHubUiState(
    val isLoading: Boolean = true,
    val totalGroupExpenses: MoneyPaise = MoneyPaise.ZERO,
    val youOwe: MoneyPaise = MoneyPaise.ZERO,
    val youAreOwed: MoneyPaise = MoneyPaise.ZERO,
    val unsettledAmount: MoneyPaise = MoneyPaise.ZERO,
    val searchQuery: String = "",
    val selectedFilter: SplitGroupFilter = SplitGroupFilter.ACTIVE,
    val allGroups: List<SplitGroupOverview> = emptyList(),
    val filteredGroups: List<SplitGroupOverview> = emptyList(),
    val activeOverlay: SplitHubOverlay = SplitHubOverlay.None,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SplitBillHubViewModel(
    private val splitBillRepository: SplitBillRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(SplitGroupFilter.ACTIVE)
    private val activeOverlay = MutableStateFlow<SplitHubOverlay>(SplitHubOverlay.None)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private data class HubControlState(
        val query: String,
        val filter: SplitGroupFilter,
        val overlay: SplitHubOverlay,
        val status: String?,
        val error: String?
    )

    private val controlStateFlow = combine(
        searchQuery,
        selectedFilter,
        activeOverlay,
        statusMessage,
        errorMessage
    ) { query, filter, overlay, status, error ->
        HubControlState(query, filter, overlay, status, error)
    }

    val uiState: StateFlow<SplitBillHubUiState> = combine(
        splitBillRepository.portfolioSummary,
        controlStateFlow
    ) { portfolio: SplitBillPortfolioSummary, controls: HubControlState ->
        val filtered = portfolio.groupOverviews.filter { overview ->
            val matchesQuery = controls.query.isBlank() ||
                overview.group.name.contains(controls.query, ignoreCase = true) ||
                (overview.group.description?.contains(controls.query, ignoreCase = true) == true)

            val matchesFilter = when (controls.filter) {
                SplitGroupFilter.ACTIVE -> !overview.group.isArchived
                SplitGroupFilter.YOU_OWE -> !overview.group.isArchived && overview.youOwe.isPositive
                SplitGroupFilter.OWED_TO_YOU -> !overview.group.isArchived && overview.youAreOwed.isPositive
                SplitGroupFilter.SETTLED -> !overview.group.isArchived && !overview.unsettledAmount.isPositive
                SplitGroupFilter.ARCHIVED -> overview.group.isArchived
            }
            matchesQuery && matchesFilter
        }

        SplitBillHubUiState(
            isLoading = false,
            totalGroupExpenses = portfolio.totalGroupExpenses,
            youOwe = portfolio.youOwe,
            youAreOwed = portfolio.youAreOwed,
            unsettledAmount = portfolio.unsettledAmount,
            searchQuery = controls.query,
            selectedFilter = controls.filter,
            allGroups = portfolio.groupOverviews,
            filteredGroups = filtered,
            activeOverlay = controls.overlay,
            statusMessage = controls.status,
            errorMessage = controls.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SplitBillHubUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFilter(filter: SplitGroupFilter) {
        selectedFilter.value = filter
    }

    fun openCreateGroup() {
        activeOverlay.value = SplitHubOverlay.CreateGroup
    }

    fun openEditGroup(overview: SplitGroupOverview) {
        activeOverlay.value = SplitHubOverlay.EditGroup(overview)
    }

    fun dismissOverlay() {
        activeOverlay.value = SplitHubOverlay.None
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    fun createGroup(
        name: String,
        iconKey: String,
        description: String,
        initialMembersRaw: String
    ) {
        if (name.isBlank()) {
            errorMessage.value = "Group name cannot be blank."
            return
        }
        val memberNames = initialMembersRaw
            .split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        viewModelScope.launch {
            runCatching {
                splitBillRepository.createGroup(
                    name = name,
                    iconKey = iconKey,
                    description = description,
                    initialMemberNames = memberNames
                )
            }.onSuccess { group ->
                activeOverlay.value = SplitHubOverlay.None
                statusMessage.value = "Group '${group.name}' created"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to create group"
            }
        }
    }

    fun updateGroup(groupId: String, name: String, iconKey: String, description: String) {
        if (name.isBlank()) {
            errorMessage.value = "Group name cannot be blank."
            return
        }
        viewModelScope.launch {
            runCatching {
                splitBillRepository.updateGroup(groupId, name, iconKey, description)
            }.onSuccess {
                activeOverlay.value = SplitHubOverlay.None
                statusMessage.value = "Group '${it.name}' updated"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update group"
            }
        }
    }

    fun setGroupArchived(groupId: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching {
                splitBillRepository.setGroupArchived(groupId, archived)
            }.onSuccess {
                statusMessage.value = if (archived) "Group archived" else "Group restored"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update archive status"
            }
        }
    }

    companion object {
        fun factory(splitBillRepository: SplitBillRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SplitBillHubViewModel(splitBillRepository) as T
                }
            }
    }
}

data class SplitGroupDetailUiState(
    val isLoading: Boolean = true,
    val groupNotFound: Boolean = false,
    val selectedTab: SplitGroupDetailTab = SplitGroupDetailTab.OVERVIEW,
    val snapshot: SplitGroupDetailSnapshot? = null,
    val eligibleAccounts: List<AccountEntity> = emptyList(),
    val allAccounts: List<AccountEntity> = emptyList(),
    val activeOverlay: SplitGroupOverlay = SplitGroupOverlay.None,
    val isSubmitting: Boolean = false,
    val groupDeleted: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SplitGroupDetailViewModel(
    private val groupId: String,
    private val splitBillRepository: SplitBillRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(SplitGroupDetailTab.OVERVIEW)
    private val activeOverlay = MutableStateFlow<SplitGroupOverlay>(SplitGroupOverlay.None)
    private val isSubmitting = MutableStateFlow(false)
    private val groupDeleted = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private data class DetailControlState(
        val tab: SplitGroupDetailTab,
        val overlay: SplitGroupOverlay,
        val submitting: Boolean,
        val deleted: Boolean,
        val status: String?,
        val error: String?
    )

    private val controlStateFlow = combine<Any?, DetailControlState>(
        selectedTab,
        activeOverlay,
        isSubmitting,
        groupDeleted,
        statusMessage,
        errorMessage
    ) { values ->
        DetailControlState(
            tab = values[0] as SplitGroupDetailTab,
            overlay = values[1] as SplitGroupOverlay,
            submitting = values[2] as Boolean,
            deleted = values[3] as Boolean,
            status = values[4] as String?,
            error = values[5] as String?
        )
    }

    val uiState: StateFlow<SplitGroupDetailUiState> = combine(
        splitBillRepository.observeGroupDetail(groupId),
        accountRepository.activeAccounts,
        controlStateFlow
    ) { snapshot, accounts, controls ->
        val bankAndCash = accounts.filter {
            !it.isArchived && (it.accountType == AccountType.BANK || it.accountType == AccountType.CASH)
        }
        SplitGroupDetailUiState(
            isLoading = false,
            groupNotFound = snapshot == null && !controls.deleted,
            selectedTab = controls.tab,
            snapshot = snapshot,
            eligibleAccounts = bankAndCash,
            allAccounts = accounts,
            activeOverlay = controls.overlay,
            isSubmitting = controls.submitting,
            groupDeleted = controls.deleted,
            statusMessage = controls.status,
            errorMessage = controls.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SplitGroupDetailUiState()
    )

    fun selectTab(tab: SplitGroupDetailTab) {
        selectedTab.value = tab
    }

    fun openEditGroup() {
        activeOverlay.value = SplitGroupOverlay.EditGroup
    }

    fun openAddParticipant() {
        activeOverlay.value = SplitGroupOverlay.AddParticipant
    }

    fun openPersonDetail(participantId: String) {
        activeOverlay.value = SplitGroupOverlay.PersonDetail(participantId)
    }

    fun openEditParticipant(participant: com.example.core.database.entity.SplitParticipantEntity) {
        activeOverlay.value = SplitGroupOverlay.EditParticipant(participant)
    }

    fun openAddExpense() {
        activeOverlay.value = SplitGroupOverlay.ExpenseForm(editingExpense = null)
    }

    fun openEditExpense(expense: com.example.core.database.dao.SplitExpenseWithShares) {
        activeOverlay.value = SplitGroupOverlay.ExpenseForm(editingExpense = expense)
    }

    fun openExpenseDetail(expenseId: String) {
        activeOverlay.value = SplitGroupOverlay.ExpenseDetail(expenseId)
    }

    fun openRecordSettlement(
        fromParticipantId: String? = null,
        toParticipantId: String? = null,
        amountPaise: Long? = null
    ) {
        activeOverlay.value = SplitGroupOverlay.SettlementForm(
            editingSettlement = null,
            prefillFromId = fromParticipantId,
            prefillToId = toParticipantId,
            prefillAmountPaise = amountPaise
        )
    }

    fun openEditSettlement(settlement: com.example.core.database.entity.SplitSettlementEntity) {
        activeOverlay.value = SplitGroupOverlay.SettlementForm(editingSettlement = settlement)
    }

    fun dismissOverlay() {
        activeOverlay.value = SplitGroupOverlay.None
    }

    fun clearMessages() {
        statusMessage.value = null
        errorMessage.value = null
    }

    fun updateGroup(name: String, iconKey: String, description: String) {
        if (name.isBlank()) {
            errorMessage.value = "Group name cannot be blank."
            return
        }
        viewModelScope.launch {
            runCatching {
                splitBillRepository.updateGroup(groupId, name, iconKey, description)
            }.onSuccess {
                activeOverlay.value = SplitGroupOverlay.None
                statusMessage.value = "Group updated"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update group"
            }
        }
    }

    fun toggleGroupArchived(archived: Boolean) {
        viewModelScope.launch {
            runCatching {
                splitBillRepository.setGroupArchived(groupId, archived)
            }.onSuccess {
                statusMessage.value = if (archived) "Group archived" else "Group restored"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update group status"
            }
        }
    }

    fun deleteGroupSafely() {
        viewModelScope.launch {
            runCatching {
                splitBillRepository.deleteGroupSafely(groupId)
            }.onSuccess {
                groupDeleted.value = true
                activeOverlay.value = SplitGroupOverlay.None
            }.onFailure {
                errorMessage.value = it.message ?: "Could not delete group"
            }
        }
    }

    fun addParticipant(name: String, phone: String, email: String, notes: String) {
        if (name.isBlank()) {
            errorMessage.value = "Participant name cannot be blank."
            return
        }
        viewModelScope.launch {
            runCatching {
                splitBillRepository.addParticipant(groupId, name, phone, email, notes)
            }.onSuccess {
                activeOverlay.value = SplitGroupOverlay.None
                statusMessage.value = "${it.name} added to group"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to add participant"
            }
        }
    }

    fun updateParticipant(participantId: String, name: String, phone: String, email: String, notes: String) {
        if (name.isBlank()) {
            errorMessage.value = "Participant name cannot be blank."
            return
        }
        viewModelScope.launch {
            runCatching {
                splitBillRepository.updateParticipant(participantId, name, phone, email, notes)
            }.onSuccess {
                activeOverlay.value = SplitGroupOverlay.PersonDetail(it.id)
                statusMessage.value = "${it.name} updated"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to update participant"
            }
        }
    }

    fun removeOrArchiveParticipantSafely(participantId: String) {
        viewModelScope.launch {
            runCatching {
                splitBillRepository.removeOrArchiveParticipantSafely(participantId)
            }.onSuccess { wasDeleted ->
                activeOverlay.value = SplitGroupOverlay.None
                statusMessage.value = if (wasDeleted) {
                    "Participant deleted"
                } else {
                    "Participant settled & archived to preserve split history"
                }
            }.onFailure {
                errorMessage.value = it.message ?: "Cannot remove participant"
            }
        }
    }

    fun submitExpense(
        editingExpenseId: String?,
        description: String,
        amountInput: String,
        expenseDateEpochMs: Long,
        paidByParticipantId: String,
        paidFromAccountId: String?,
        splitSpec: SplitInputSpec,
        notes: String
    ) {
        if (isSubmitting.value) return
        val amount = MoneyPaise.parseFromRupeeInput(amountInput)
        if (amount == null || !amount.isPositive) {
            errorMessage.value = "Enter a valid positive expense amount in ₹."
            return
        }
        if (description.isBlank()) {
            errorMessage.value = "Enter an expense description."
            return
        }

        viewModelScope.launch {
            isSubmitting.value = true
            try {
                runCatching {
                    if (editingExpenseId == null) {
                        splitBillRepository.addExpense(
                            groupId = groupId,
                            description = description,
                            totalAmountPaise = amount.paise,
                            expenseDateEpochMs = expenseDateEpochMs,
                            paidByParticipantId = paidByParticipantId,
                            paidFromAccountId = paidFromAccountId,
                            splitSpec = splitSpec,
                            notes = notes
                        )
                    } else {
                        splitBillRepository.editExpense(
                            expenseId = editingExpenseId,
                            description = description,
                            totalAmountPaise = amount.paise,
                            expenseDateEpochMs = expenseDateEpochMs,
                            paidByParticipantId = paidByParticipantId,
                            paidFromAccountId = paidFromAccountId,
                            splitSpec = splitSpec,
                            notes = notes
                        )
                    }
                }.onSuccess {
                    activeOverlay.value = SplitGroupOverlay.None
                    statusMessage.value = if (editingExpenseId == null) {
                        "Expense '${it.description}' saved"
                    } else {
                        "Expense '${it.description}' updated"
                    }
                }.onFailure {
                    errorMessage.value = it.message ?: "Failed to save group expense"
                }
            } finally {
                isSubmitting.value = false
            }
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            runCatching {
                splitBillRepository.deleteExpense(expenseId)
            }.onSuccess {
                activeOverlay.value = SplitGroupOverlay.None
                statusMessage.value = "Group expense deleted & synced with ledger"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to delete group expense"
            }
        }
    }

    fun submitSettlement(
        editingSettlementId: String?,
        fromParticipantId: String,
        toParticipantId: String,
        amountInput: String,
        settlementDateEpochMs: Long,
        accountId: String?,
        notes: String
    ) {
        if (isSubmitting.value) return
        val amount = MoneyPaise.parseFromRupeeInput(amountInput)
        if (amount == null || !amount.isPositive) {
            errorMessage.value = "Enter a valid positive settlement amount in ₹."
            return
        }

        viewModelScope.launch {
            isSubmitting.value = true
            try {
                runCatching {
                    if (editingSettlementId == null) {
                        splitBillRepository.recordSettlement(
                            groupId = groupId,
                            fromParticipantId = fromParticipantId,
                            toParticipantId = toParticipantId,
                            amountPaise = amount.paise,
                            settlementDateEpochMs = settlementDateEpochMs,
                            accountId = accountId,
                            notes = notes
                        )
                    } else {
                        splitBillRepository.editSettlement(
                            settlementId = editingSettlementId,
                            fromParticipantId = fromParticipantId,
                            toParticipantId = toParticipantId,
                            amountPaise = amount.paise,
                            settlementDateEpochMs = settlementDateEpochMs,
                            accountId = accountId,
                            notes = notes
                        )
                    }
                }.onSuccess {
                    activeOverlay.value = SplitGroupOverlay.None
                    statusMessage.value = if (editingSettlementId == null) {
                        "Settlement of ${amount.formatInr()} recorded"
                    } else {
                        "Settlement updated"
                    }
                }.onFailure {
                    errorMessage.value = it.message ?: "Failed to record settlement"
                }
            } finally {
                isSubmitting.value = false
            }
        }
    }

    fun deleteSettlement(settlementId: String) {
        viewModelScope.launch {
            runCatching {
                splitBillRepository.deleteSettlement(settlementId)
            }.onSuccess {
                statusMessage.value = "Settlement reversed & removed"
            }.onFailure {
                errorMessage.value = it.message ?: "Failed to delete settlement"
            }
        }
    }

    companion object {
        fun factory(
            groupId: String,
            splitBillRepository: SplitBillRepository,
            accountRepository: AccountRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SplitGroupDetailViewModel(groupId, splitBillRepository, accountRepository) as T
            }
        }
    }
}
