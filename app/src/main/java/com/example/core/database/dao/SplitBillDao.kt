package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.core.database.entity.SplitExpenseEntity
import com.example.core.database.entity.SplitExpenseShareEntity
import com.example.core.database.entity.SplitGroupEntity
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity
import kotlinx.coroutines.flow.Flow

data class SplitExpenseWithShares(
    @Embedded val expense: SplitExpenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "expense_id"
    )
    val shares: List<SplitExpenseShareEntity>
)

@Dao
interface SplitBillDao {

    @Query("SELECT * FROM split_groups ORDER BY last_activity_epoch_ms DESC, created_at_epoch_ms DESC")
    fun observeAllGroups(): Flow<List<SplitGroupEntity>>

    @Query("SELECT * FROM split_groups WHERE id = :groupId LIMIT 1")
    fun observeGroupById(groupId: String): Flow<SplitGroupEntity?>

    @Query("SELECT * FROM split_participants ORDER BY is_current_user DESC, created_at_epoch_ms ASC")
    fun observeAllParticipants(): Flow<List<SplitParticipantEntity>>

    @Query(
        "SELECT * FROM split_participants " +
            "WHERE group_id = :groupId " +
            "ORDER BY is_current_user DESC, is_archived ASC, created_at_epoch_ms ASC"
    )
    fun observeParticipantsForGroup(groupId: String): Flow<List<SplitParticipantEntity>>

    @Transaction
    @Query(
        "SELECT * FROM split_expenses " +
            "WHERE is_deleted = 0 " +
            "ORDER BY expense_date_epoch_ms DESC, created_at_epoch_ms DESC"
    )
    fun observeAllActiveExpensesWithShares(): Flow<List<SplitExpenseWithShares>>

    @Transaction
    @Query(
        "SELECT * FROM split_expenses " +
            "WHERE group_id = :groupId AND is_deleted = 0 " +
            "ORDER BY expense_date_epoch_ms DESC, created_at_epoch_ms DESC"
    )
    fun observeActiveExpensesForGroup(groupId: String): Flow<List<SplitExpenseWithShares>>

    @Query(
        "SELECT * FROM split_settlements " +
            "WHERE is_deleted = 0 " +
            "ORDER BY settlement_date_epoch_ms DESC, created_at_epoch_ms DESC"
    )
    fun observeAllActiveSettlements(): Flow<List<SplitSettlementEntity>>

    @Query(
        "SELECT * FROM split_settlements " +
            "WHERE group_id = :groupId AND is_deleted = 0 " +
            "ORDER BY settlement_date_epoch_ms DESC, created_at_epoch_ms DESC"
    )
    fun observeActiveSettlementsForGroup(groupId: String): Flow<List<SplitSettlementEntity>>

    // Synchronous / suspend lookups inside database transactions
    @Query("SELECT * FROM split_groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): SplitGroupEntity?

    @Query("SELECT * FROM split_participants WHERE id = :participantId LIMIT 1")
    suspend fun getParticipantById(participantId: String): SplitParticipantEntity?

    @Query("SELECT * FROM split_participants WHERE group_id = :groupId ORDER BY is_current_user DESC, created_at_epoch_ms ASC")
    suspend fun getParticipantsForGroupOnce(groupId: String): List<SplitParticipantEntity>

    @Query("SELECT * FROM split_expenses WHERE id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: String): SplitExpenseEntity?

    @Query("SELECT * FROM split_expenses WHERE idempotency_key = :idempotencyKey LIMIT 1")
    suspend fun getExpenseByIdempotencyKey(idempotencyKey: String): SplitExpenseEntity?

    @Query("SELECT * FROM split_expenses WHERE group_id = :groupId AND is_deleted = 0")
    suspend fun getActiveExpensesForGroupOnce(groupId: String): List<SplitExpenseEntity>

    @Query("SELECT * FROM split_expense_shares WHERE expense_id = :expenseId")
    suspend fun getSharesForExpenseOnce(expenseId: String): List<SplitExpenseShareEntity>

    @Query("SELECT * FROM split_expense_shares WHERE expense_id IN (SELECT id FROM split_expenses WHERE group_id = :groupId AND is_deleted = 0)")
    suspend fun getActiveSharesForGroupOnce(groupId: String): List<SplitExpenseShareEntity>

    @Query("SELECT * FROM split_settlements WHERE id = :settlementId LIMIT 1")
    suspend fun getSettlementById(settlementId: String): SplitSettlementEntity?

    @Query("SELECT * FROM split_settlements WHERE idempotency_key = :idempotencyKey LIMIT 1")
    suspend fun getSettlementByIdempotencyKey(idempotencyKey: String): SplitSettlementEntity?

    @Query("SELECT * FROM split_settlements WHERE group_id = :groupId AND is_deleted = 0")
    suspend fun getActiveSettlementsForGroupOnce(groupId: String): List<SplitSettlementEntity>

    // Reference counts for safe delete vs archive validation
    @Query(
        "SELECT (" +
            "(SELECT COUNT(*) FROM split_expenses WHERE paid_by_participant_id = :participantId) + " +
            "(SELECT COUNT(*) FROM split_expense_shares WHERE participant_id = :participantId) + " +
            "(SELECT COUNT(*) FROM split_settlements WHERE from_participant_id = :participantId OR to_participant_id = :participantId)" +
            ")"
    )
    suspend fun countParticipantReferences(participantId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: SplitGroupEntity)

    @Update
    suspend fun updateGroup(group: SplitGroupEntity)

    @Query("UPDATE split_groups SET is_archived = :isArchived, updated_at_epoch_ms = :updatedAt WHERE id = :groupId")
    suspend fun setGroupArchived(groupId: String, isArchived: Boolean, updatedAt: Long)

    @Query("UPDATE split_groups SET last_activity_epoch_ms = :activityAt, updated_at_epoch_ms = :updatedAt WHERE id = :groupId")
    suspend fun touchGroupActivity(groupId: String, activityAt: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParticipant(participant: SplitParticipantEntity)

    @Update
    suspend fun updateParticipant(participant: SplitParticipantEntity)

    @Query("UPDATE split_participants SET is_archived = :isArchived, updated_at_epoch_ms = :updatedAt WHERE id = :participantId")
    suspend fun setParticipantArchived(participantId: String, isArchived: Boolean, updatedAt: Long)

    @Query("DELETE FROM split_participants WHERE id = :participantId")
    suspend fun deleteParticipantById(participantId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: SplitExpenseEntity)

    @Update
    suspend fun updateExpense(expense: SplitExpenseEntity)

    @Query("UPDATE split_expenses SET is_deleted = 1, updated_at_epoch_ms = :updatedAt WHERE id = :expenseId")
    suspend fun markExpenseDeleted(expenseId: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShares(shares: List<SplitExpenseShareEntity>)

    @Query("DELETE FROM split_expense_shares WHERE expense_id = :expenseId")
    suspend fun deleteSharesForExpense(expenseId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettlement(settlement: SplitSettlementEntity)

    @Update
    suspend fun updateSettlement(settlement: SplitSettlementEntity)

    @Query("UPDATE split_settlements SET is_deleted = 1, updated_at_epoch_ms = :updatedAt WHERE id = :settlementId")
    suspend fun markSettlementDeleted(settlementId: String, updatedAt: Long)

    // Strict child-to-parent safe cleanup for settled/empty group deletion
    @Query("DELETE FROM split_expense_shares WHERE expense_id IN (SELECT id FROM split_expenses WHERE group_id = :groupId)")
    suspend fun hardDeleteSharesByGroup(groupId: String)

    @Query("DELETE FROM split_settlements WHERE group_id = :groupId")
    suspend fun hardDeleteSettlementsByGroup(groupId: String)

    @Query("DELETE FROM split_expenses WHERE group_id = :groupId")
    suspend fun hardDeleteExpensesByGroup(groupId: String)

    @Query("DELETE FROM split_participants WHERE group_id = :groupId")
    suspend fun hardDeleteParticipantsByGroup(groupId: String)

    @Query("DELETE FROM split_groups WHERE id = :groupId")
    suspend fun hardDeleteGroupById(groupId: String)
}
