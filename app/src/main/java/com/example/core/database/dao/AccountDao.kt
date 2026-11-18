package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.database.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY is_default DESC, name ASC")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY is_default DESC, name ASC")
    suspend fun getActiveAccountsOnce(): List<AccountEntity>

    @Query(
        "SELECT COALESCE(SUM(current_balance_paise), 0) FROM accounts " +
            "WHERE is_archived = 0 AND account_type != 'CREDIT_CARD'"
    )
    fun observeTotalLiquidBalancePaise(): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(current_balance_paise), 0) FROM accounts " +
            "WHERE is_archived = 0 AND account_type = 'CREDIT_CARD'"
    )
    fun observeTotalCreditCardOutstandingPaise(): Flow<Long>

    @Query("SELECT COUNT(*) FROM accounts WHERE is_archived = 0")
    fun observeActiveAccountCount(): Flow<Int>

    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query(
        "UPDATE accounts SET is_default = 0, updated_at_epoch_ms = :updatedAtEpochMs " +
            "WHERE id != :accountId AND is_default = 1"
    )
    suspend fun clearOtherDefaults(accountId: String, updatedAtEpochMs: Long)

    @Query(
        "UPDATE accounts SET is_archived = 1, is_default = 0, updated_at_epoch_ms = :updatedAtEpochMs " +
            "WHERE id = :accountId"
    )
    suspend fun archiveAccount(accountId: String, updatedAtEpochMs: Long): Int

    @Transaction
    suspend fun updateAccountMetadata(
        accountId: String,
        name: String,
        institutionName: String?,
        accountLast4: String?,
        isDefault: Boolean,
        updatedAtEpochMs: Long
    ): AccountEntity {
        val current = getAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val updated = current.copy(
            name = name.trim(),
            institutionName = institutionName?.trim()?.ifBlank { null },
            accountLast4 = accountLast4?.trim()?.ifBlank { null },
            isDefault = isDefault,
            updatedAtEpochMs = updatedAtEpochMs
        )
        updateAccount(updated)
        if (isDefault) {
            clearOtherDefaults(accountId, updatedAtEpochMs)
        }
        return updated
    }

    @Query(
        "UPDATE accounts SET current_balance_paise = current_balance_paise + :deltaPaise, " +
            "updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :accountId"
    )
    suspend fun adjustBalancePaise(
        accountId: String,
        deltaPaise: Long,
        updatedAtEpochMs: Long
    ): Int
}
