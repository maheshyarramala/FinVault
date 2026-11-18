package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.entity.DebtRecordEntity
import com.example.core.database.entity.DebtRepaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt_records WHERE status != 'ARCHIVED' ORDER BY updated_at_epoch_ms DESC")
    fun observeDebts(): Flow<List<DebtRecordEntity>>

    @Query("SELECT * FROM debt_records WHERE status = 'ACTIVE' ORDER BY due_epoch_ms IS NULL, due_epoch_ms ASC")
    fun observeActiveDebts(): Flow<List<DebtRecordEntity>>

    @Query("SELECT * FROM debt_records WHERE id = :debtId LIMIT 1")
    suspend fun getDebt(debtId: String): DebtRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebt(debt: DebtRecordEntity)

    @Update
    suspend fun updateDebt(debt: DebtRecordEntity)

    @Query("SELECT * FROM debt_repayments WHERE debt_id = :debtId ORDER BY paid_at_epoch_ms DESC")
    fun observeRepayments(debtId: String): Flow<List<DebtRepaymentEntity>>

    @Query("SELECT * FROM debt_repayments WHERE debt_id = :debtId ORDER BY paid_at_epoch_ms DESC")
    suspend fun getRepayments(debtId: String): List<DebtRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRepayment(repayment: DebtRepaymentEntity)

    @Update
    suspend fun updateRepayment(repayment: DebtRepaymentEntity)

    @Query("SELECT * FROM debt_repayments WHERE id = :repaymentId LIMIT 1")
    suspend fun getRepaymentById(repaymentId: String): DebtRepaymentEntity?

    @Query("SELECT * FROM debt_repayments WHERE id = :repaymentId LIMIT 1")
    suspend fun getRepaymentsForEditById(repaymentId: String): List<DebtRepaymentEntity>

    @androidx.room.Delete
    suspend fun deleteRepayment(repayment: DebtRepaymentEntity)

    @Query("SELECT * FROM debt_repayments WHERE idempotency_key = :key LIMIT 1")
    suspend fun getRepaymentByIdempotencyKey(key: String): DebtRepaymentEntity?

    @Query("SELECT COALESCE(SUM(outstanding_principal_paise), 0) FROM debt_records WHERE type = 'LENT' AND status = 'ACTIVE'")
    fun observeTotalLentOutstandingPaise(): Flow<Long>

    @Query("SELECT COALESCE(SUM(outstanding_principal_paise), 0) FROM debt_records WHERE type = 'BORROWED' AND status = 'ACTIVE'")
    fun observeTotalBorrowedOutstandingPaise(): Flow<Long>
}
