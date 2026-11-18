package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.CardStatementEntity
import com.example.core.database.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

data class CreditCardWithDetails(
    @Embedded val card: CreditCardEntity,
    @Relation(
        parentColumn = "account_id",
        entityColumn = "id"
    )
    val account: AccountEntity,
    @Relation(
        parentColumn = "account_id",
        entityColumn = "card_account_id"
    )
    val statements: List<CardStatementEntity>
)

@Dao
interface CreditCardDao {

    @Transaction
    @Query("SELECT * FROM credit_cards ORDER BY created_at_epoch_ms ASC")
    fun observeCreditCardsWithDetails(): Flow<List<CreditCardWithDetails>>

    @Query("SELECT * FROM credit_cards WHERE account_id = :accountId LIMIT 1")
    suspend fun getCardByAccountId(accountId: String): CreditCardEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCreditCard(card: CreditCardEntity)

    @Update
    suspend fun updateCreditCard(card: CreditCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatement(statement: CardStatementEntity)

    @Query("SELECT * FROM card_statements WHERE card_account_id = :accountId ORDER BY period_end_epoch_ms DESC")
    fun observeStatementsForCard(accountId: String): Flow<List<CardStatementEntity>>

    @Query("SELECT * FROM card_statements WHERE card_account_id = :accountId AND status != 'PAID' ORDER BY due_date_epoch_ms ASC, created_at_epoch_ms ASC")
    suspend fun getUnpaidStatementsOldestFirst(accountId: String): List<CardStatementEntity>

    @Query("SELECT * FROM card_statements WHERE card_account_id = :accountId ORDER BY due_date_epoch_ms ASC, created_at_epoch_ms ASC")
    suspend fun getStatementsForCardOnce(accountId: String): List<CardStatementEntity>

    @Update
    suspend fun updateStatement(statement: CardStatementEntity)
}
