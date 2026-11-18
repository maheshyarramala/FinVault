package com.example.feature.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.DebtRepository
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.DebtRecordEntity
import com.example.core.database.entity.DebtInterestType
import com.example.core.database.entity.DebtType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebtManagementUiState(
    val debts: List<DebtRecordEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    val error: String? = null
)

class DebtManagementViewModel(
    private val debtRepository: DebtRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebtManagementUiState())
    val uiState: StateFlow<DebtManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            debtRepository.debts.collect { debts ->
                _uiState.update { it.copy(debts = debts, isLoading = false) }
            }
        }
        viewModelScope.launch {
            accountRepository.activeAccounts.collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
    }

    fun createDebt(
        title: String,
        person: String,
        amount: String,
        type: DebtType,
        accountId: String,
        interestType: DebtInterestType = DebtInterestType.NONE,
        annualRatePercent: String = "0",
        fixedInterest: String = "0",
        dueDate: String = ""
    ) {
        viewModelScope.launch {
            runCatching {
                val rateBps = BigDecimal(annualRatePercent.ifBlank { "0" })
                    .multiply(BigDecimal("100"))
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .intValueExact()
                debtRepository.createDebt(
                    title = title,
                    personName = person,
                    type = type,
                    principalPaise = amount.toPaise(),
                    accountId = accountId,
                    interestType = interestType,
                    annualInterestRateBps = rateBps,
                    fixedInterestPaise = fixedInterest.ifBlank { "0" }.toPaise(),
                    dueEpochMs = dueDate.trim().takeIf { it.isNotEmpty() }?.let { date ->
                        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                )
            }.onSuccess {
                _uiState.update { it.copy(message = "Debt saved", error = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Unable to save debt", message = null) }
            }
        }
    }

    fun recordRepayment(
        debtId: String,
        principal: String,
        interest: String,
        accountId: String
    ) {
        viewModelScope.launch {
            runCatching {
                debtRepository.recordRepayment(
                    debtId = debtId,
                    principalPaise = principal.toPaise(),
                    interestPaise = interest.ifBlank { "0" }.toPaise(),
                    paymentAccountId = accountId
                )
            }.onSuccess {
                _uiState.update { it.copy(message = "Repayment recorded", error = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Unable to record repayment", message = null) }
            }
        }
    }

    companion object {
        fun factory(
            debtRepository: DebtRepository,
            accountRepository: AccountRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DebtManagementViewModel(debtRepository, accountRepository) as T
        }
    }

    fun archiveDebt(debtId: String) {
        viewModelScope.launch {
            runCatching { debtRepository.archiveDebt(debtId) }
                .onSuccess { _uiState.update { it.copy(message = "Debt archived and ledger movements reversed", error = null) } }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "Unable to archive debt", message = null) }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(message = null, error = null) }
    }
}

private fun String.toPaise(): Long {
    val amount = BigDecimal(trim())
    require(amount >= BigDecimal.ZERO) { "Amount cannot be negative" }
    return amount.setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .longValueExact()
}
