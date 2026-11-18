package com.example.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.database.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE month_key = :monthKey ORDER BY category_id IS NOT NULL ASC, created_at_epoch_ms ASC")
    fun observeBudgetsForMonth(monthKey: String): Flow<List<BudgetEntity>>

    @Query(
        "SELECT * FROM budgets WHERE month_key = :monthKey AND " +
            "((:categoryId IS NULL AND category_id IS NULL) OR category_id = :categoryId) LIMIT 1"
    )
    suspend fun findBudget(monthKey: String, categoryId: String?): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: String)
}
