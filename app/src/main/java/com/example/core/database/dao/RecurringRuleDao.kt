package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.database.entity.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules WHERE is_active = 1 ORDER BY next_run_epoch_ms ASC")
    fun observeActiveRules(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :ruleId LIMIT 1")
    suspend fun getRuleById(ruleId: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RecurringRuleEntity)

    @Update
    suspend fun updateRule(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: String)
}
