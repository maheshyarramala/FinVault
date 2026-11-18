package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.example.core.database.entity.LedgerEntryEntity
import com.example.core.database.entity.LedgerTransactionEntity
import kotlinx.coroutines.flow.Flow

data class LedgerTransactionWithEntries(
    @Embedded val transaction: LedgerTransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transaction_id"
    )
    val entries: List<LedgerEntryEntity>
)

data class AccountStatementEntryRow(
    val entryId: String,
    val transactionId: String,
    val txType: String,
    val txTimestampEpochMs: Long,
    val description: String,
    val entrySide: String,
    val amountPaise: Long,
    val isVoided: Boolean
)

data class CategorySpendAggregate(
    val categoryId: String?,
    val totalPaise: Long
)

data class DebtLedgerSummaryRow(
    val totalLentPaise: Long,
    val totalLentRepaidPaise: Long,
    val totalBorrowedPaise: Long,
    val totalBorrowedRepaidPaise: Long,
    val activeDebtTxCount: Int
)

data class SplitBillLedgerSummaryRow(
    val totalSplitPaidPaise: Long,
    val totalReimbursedPaise: Long,
    val splitTxCount: Int
)

data class LoanEmiLedgerSummaryRow(
    val totalPrincipalPaidPaise: Long,
    val totalInterestPaidPaise: Long,
    val currentMonthPaidPaise: Long,
    val loanPaymentCount: Int
)

@Dao
interface LedgerDao {

    @Transaction
    @Query(
        "SELECT * FROM ledger_transactions " +
            "WHERE is_voided = 0 AND tx_type != 'REVERSAL' " +
            "ORDER BY tx_timestamp_epoch_ms DESC, created_at_epoch_ms DESC"
    )
    fun observeActiveTransactionsWithEntries(): Flow<List<LedgerTransactionWithEntries>>

    @Transaction
    @Query(
        "SELECT * FROM ledger_transactions " +
            "ORDER BY tx_timestamp_epoch_ms DESC, created_at_epoch_ms DESC"
    )
    fun observeFullAuditTrailWithEntries(): Flow<List<LedgerTransactionWithEntries>>

    @Transaction
    @Query(
        "SELECT * FROM ledger_transactions " +
            "WHERE is_voided = 0 AND tx_type != 'REVERSAL' " +
            "ORDER BY tx_timestamp_epoch_ms DESC, created_at_epoch_ms DESC LIMIT :limit"
    )
    fun observeRecentTransactions(limit: Int): Flow<List<LedgerTransactionWithEntries>>

    @Query(
        "SELECT le.id AS entryId, lt.id AS transactionId, lt.tx_type AS txType, " +
            "lt.tx_timestamp_epoch_ms AS txTimestampEpochMs, lt.description AS description, " +
            "le.entry_side AS entrySide, le.amount_paise AS amountPaise, lt.is_voided AS isVoided " +
            "FROM ledger_entries le " +
            "INNER JOIN ledger_transactions lt ON le.transaction_id = lt.id " +
            "WHERE le.account_id = :accountId " +
            "ORDER BY lt.tx_timestamp_epoch_ms DESC, lt.created_at_epoch_ms DESC"
    )
    fun observeAccountStatementEntries(accountId: String): Flow<List<AccountStatementEntryRow>>

    @Query(
        "SELECT COALESCE(SUM(total_amount_paise), 0) FROM ledger_transactions " +
            "WHERE is_voided = 0 " +
            "AND tx_type = 'INCOME' " +
            "AND tx_timestamp_epoch_ms BETWEEN :startEpochMs AND :endEpochMs"
    )
    fun observeIncomeSumPaise(startEpochMs: Long, endEpochMs: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(total_amount_paise), 0) FROM ledger_transactions " +
            "WHERE is_voided = 0 " +
            "AND tx_type IN ('EXPENSE', 'CREDIT_CARD_EXPENSE', 'SPLIT_EXPENSE') " +
            "AND tx_timestamp_epoch_ms BETWEEN :startEpochMs AND :endEpochMs"
    )
    fun observeExpenseSumPaise(startEpochMs: Long, endEpochMs: Long): Flow<Long>

    @Query(
        "SELECT COALESCE(SUM(total_amount_paise), 0) FROM ledger_transactions " +
            "WHERE is_voided = 0 " +
            "AND tx_type IN ('EXPENSE', 'CREDIT_CARD_EXPENSE') " +
            "AND tx_timestamp_epoch_ms BETWEEN :startEpochMs AND :endEpochMs"
    )
    fun observePersonalDirectExpenseSumPaise(startEpochMs: Long, endEpochMs: Long): Flow<Long>

    @Query(
        "SELECT category_id AS categoryId, COALESCE(SUM(total_amount_paise), 0) AS totalPaise " +
            "FROM ledger_transactions " +
            "WHERE is_voided = 0 " +
            "AND tx_type IN ('EXPENSE', 'CREDIT_CARD_EXPENSE', 'SPLIT_EXPENSE') " +
            "AND tx_timestamp_epoch_ms BETWEEN :startEpochMs AND :endEpochMs " +
            "GROUP BY category_id"
    )
    fun observeCategoryExpenseTotals(
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<List<CategorySpendAggregate>>

    @Query(
        "SELECT * FROM ledger_transactions " +
            "WHERE is_voided = 0 AND tx_type != 'REVERSAL' " +
            "AND tx_timestamp_epoch_ms BETWEEN :startEpochMs AND :endEpochMs " +
            "ORDER BY tx_timestamp_epoch_ms ASC"
    )
    fun observeActiveTransactionsInRange(
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<List<LedgerTransactionEntity>>

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN lt.tx_type = 'DEBT_LENT' AND le.ledger_class = 'ASSET' AND le.account_id IS NULL AND le.entry_side = 'DEBIT' THEN le.amount_paise ELSE 0 END), 0) AS totalLentPaise, " +
            "COALESCE(SUM(CASE WHEN lt.tx_type = 'DEBT_REPAYMENT' AND le.ledger_class = 'ASSET' AND le.account_id IS NULL AND le.entry_side = 'CREDIT' THEN le.amount_paise ELSE 0 END), 0) AS totalLentRepaidPaise, " +
            "COALESCE(SUM(CASE WHEN lt.tx_type = 'DEBT_BORROWED' AND le.ledger_class = 'LIABILITY' AND le.account_id IS NULL AND le.entry_side = 'CREDIT' THEN le.amount_paise ELSE 0 END), 0) AS totalBorrowedPaise, " +
            "COALESCE(SUM(CASE WHEN lt.tx_type = 'DEBT_REPAYMENT' AND le.ledger_class = 'LIABILITY' AND le.account_id IS NULL AND le.entry_side = 'DEBIT' THEN le.amount_paise ELSE 0 END), 0) AS totalBorrowedRepaidPaise, " +
            "COUNT(DISTINCT lt.id) AS activeDebtTxCount " +
            "FROM ledger_entries le " +
            "INNER JOIN ledger_transactions lt ON le.transaction_id = lt.id " +
            "WHERE lt.is_voided = 0 AND lt.tx_type IN ('DEBT_LENT', 'DEBT_BORROWED', 'DEBT_REPAYMENT')"
    )
    fun observeDebtLedgerSummary(): Flow<DebtLedgerSummaryRow>

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN tx_type = 'SPLIT_EXPENSE' THEN total_amount_paise ELSE 0 END), 0) AS totalSplitPaidPaise, " +
            "COALESCE(SUM(CASE WHEN tx_type = 'SPLIT_REIMBURSEMENT' THEN total_amount_paise ELSE 0 END), 0) AS totalReimbursedPaise, " +
            "COUNT(*) AS splitTxCount " +
            "FROM ledger_transactions " +
            "WHERE is_voided = 0 AND tx_type IN ('SPLIT_EXPENSE', 'SPLIT_REIMBURSEMENT')"
    )
    fun observeSplitBillLedgerSummary(): Flow<SplitBillLedgerSummaryRow>

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN lt.tx_type IN ('LOAN_EMI', 'LOAN_PREPAYMENT') AND le.ledger_class = 'LIABILITY' AND le.entry_side = 'DEBIT' THEN le.amount_paise ELSE 0 END), 0) AS totalPrincipalPaidPaise, " +
            "COALESCE(SUM(CASE WHEN lt.tx_type IN ('LOAN_EMI', 'LOAN_PREPAYMENT') AND le.ledger_class = 'EXPENSE' AND le.entry_side = 'DEBIT' THEN le.amount_paise ELSE 0 END), 0) AS totalInterestPaidPaise, " +
            "COALESCE(SUM(CASE WHEN lt.tx_type IN ('LOAN_EMI', 'LOAN_PREPAYMENT') AND le.ledger_class = 'ASSET' AND le.entry_side = 'CREDIT' AND lt.tx_timestamp_epoch_ms BETWEEN :monthStartEpochMs AND :monthEndEpochMs THEN le.amount_paise ELSE 0 END), 0) AS currentMonthPaidPaise, " +
            "COUNT(DISTINCT lt.id) AS loanPaymentCount " +
            "FROM ledger_entries le " +
            "INNER JOIN ledger_transactions lt ON le.transaction_id = lt.id " +
            "WHERE lt.is_voided = 0 AND lt.tx_type IN ('LOAN_EMI', 'LOAN_PREPAYMENT')"
    )
    fun observeLoanEmiLedgerSummary(
        monthStartEpochMs: Long,
        monthEndEpochMs: Long
    ): Flow<LoanEmiLedgerSummaryRow>

    @Query("SELECT * FROM ledger_transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: String): LedgerTransactionEntity?

    @Query(
        "SELECT * FROM ledger_transactions WHERE source_module = 'DEBT' " +
            "AND source_record_id = :debtId " +
            "ORDER BY created_at_epoch_ms DESC, tx_timestamp_epoch_ms DESC"
    )
    suspend fun getAllDebtTransactions(debtId: String): List<LedgerTransactionEntity>

    @Transaction
    @Query("SELECT * FROM ledger_transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionWithEntriesById(transactionId: String): LedgerTransactionWithEntries?

    @Query("SELECT * FROM ledger_transactions WHERE idempotency_key = :idempotencyKey LIMIT 1")
    suspend fun getTransactionByIdempotencyKey(idempotencyKey: String): LedgerTransactionEntity?

    @Query("SELECT * FROM ledger_entries WHERE transaction_id = :transactionId ORDER BY entry_order ASC")
    suspend fun getEntriesForTransaction(transactionId: String): List<LedgerEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: LedgerTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<LedgerEntryEntity>)

    @Query(
        "UPDATE ledger_transactions SET is_voided = 1, " +
            "replaced_by_transaction_id = :replacedById, " +
            "updated_at_epoch_ms = :updatedAtEpochMs " +
            "WHERE id = :transactionId"
    )
    suspend fun markTransactionVoided(
        transactionId: String,
        replacedById: String?,
        updatedAtEpochMs: Long
    )

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN entry_side = 'DEBIT' THEN amount_paise ELSE -amount_paise END), 0) " +
            "FROM ledger_entries WHERE account_id = :accountId"
    )
    suspend fun computeNetDebitMinusCreditForAccount(accountId: String): Long

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN entry_side = 'DEBIT' THEN amount_paise ELSE 0 END), 0) " +
            "FROM ledger_entries"
    )
    suspend fun sumAllDebitsPaise(): Long

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN entry_side = 'CREDIT' THEN amount_paise ELSE 0 END), 0) " +
            "FROM ledger_entries"
    )
    suspend fun sumAllCreditsPaise(): Long
}
