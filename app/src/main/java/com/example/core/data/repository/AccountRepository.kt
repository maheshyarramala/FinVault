package com.example.core.data.repository

import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.AccountDao
import com.example.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepository(
    private val accountDao: AccountDao
) {
    val activeAccounts: Flow<List<AccountEntity>> = accountDao.observeActiveAccounts()

    val totalLiquidBalance: Flow<MoneyPaise> = accountDao.observeTotalLiquidBalancePaise()
        .map { MoneyPaise(it) }

    val totalCreditCardOutstanding: Flow<MoneyPaise> =
        accountDao.observeTotalCreditCardOutstandingPaise().map { MoneyPaise(it) }

    val activeAccountCount: Flow<Int> = accountDao.observeActiveAccountCount()

    suspend fun updateAccountDetails(
        accountId: String,
        name: String,
        institutionName: String?,
        last4: String?,
        isDefault: Boolean
    ): AccountEntity {
        require(name.isNotBlank()) { "Account name cannot be blank." }
        return accountDao.updateAccountMetadata(
            accountId = accountId,
            name = name,
            institutionName = institutionName,
            accountLast4 = last4,
            isDefault = isDefault,
            updatedAtEpochMs = DateTimeUtils.nowEpochMs()
        )
    }

    suspend fun archiveAccount(accountId: String) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        check(account.currentBalancePaise == 0L) {
            "Transfer or settle remaining balance (${MoneyPaise(account.currentBalancePaise).formatInr()}) before archiving ${account.name}."
        }
        accountDao.archiveAccount(accountId, DateTimeUtils.nowEpochMs())
    }
}
