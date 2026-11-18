package com.example.core.data.repository

import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.BudgetDao
import com.example.core.database.dao.CategoryDao
import com.example.core.database.dao.LedgerDao
import com.example.core.database.entity.BudgetEntity
import com.example.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

data class BudgetProgressItem(
    val id: String,
    val categoryId: String?,
    val categoryName: String,
    val monthKey: String,
    val limit: MoneyPaise,
    val spent: MoneyPaise,
    val remaining: MoneyPaise,
    val previousMonthSpent: MoneyPaise,
    val alertThresholdPct: Int,
    val usagePercentage: Int,
    val isAlertTriggered: Boolean,
    val isOverBudget: Boolean
)

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val ledgerDao: LedgerDao,
    private val categoryDao: CategoryDao
) {
    fun observeBudgetsWithProgress(monthKey: String): Flow<List<BudgetProgressItem>> {
        val (startMs, endMs) = DateTimeUtils.monthRangeFromKey(monthKey)
        val prevKey = DateTimeUtils.shiftMonthKey(monthKey, -1)
        val (prevStartMs, prevEndMs) = DateTimeUtils.monthRangeFromKey(prevKey)

        return combine(
            budgetDao.observeBudgetsForMonth(monthKey),
            ledgerDao.observeCategoryExpenseTotals(startMs, endMs),
            ledgerDao.observeCategoryExpenseTotals(prevStartMs, prevEndMs),
            categoryDao.observeAllCategories()
        ) { budgets, currentSpends, prevSpends, categories ->
            val categoryMap = categories.associateBy { it.id }
            val childIdsByParent = categories.filter { it.parentId != null }.groupBy { it.parentId!! }

            budgets.map { budget ->
                val matchingIds = matchingCategoryIds(budget.categoryId, childIdsByParent)
                val spentPaise = if (budget.categoryId == null) {
                    currentSpends.sumOf { it.totalPaise }
                } else {
                    currentSpends.filter { it.categoryId in matchingIds }.sumOf { it.totalPaise }
                }
                val prevSpentPaise = if (budget.categoryId == null) {
                    prevSpends.sumOf { it.totalPaise }
                } else {
                    prevSpends.filter { it.categoryId in matchingIds }.sumOf { it.totalPaise }
                }

                val limitPaise = budget.limitPaise
                val remainingPaise = limitPaise - spentPaise
                val pct = if (limitPaise > 0L) {
                    ((spentPaise.toDouble() / limitPaise.toDouble()) * 100.0).toInt()
                } else {
                    0
                }
                val name = budget.categoryId?.let { categoryMap[it]?.name ?: "Category" }
                    ?: "Overall Monthly Budget"

                BudgetProgressItem(
                    id = budget.id,
                    categoryId = budget.categoryId,
                    categoryName = name,
                    monthKey = budget.monthKey,
                    limit = MoneyPaise(limitPaise),
                    spent = MoneyPaise(spentPaise),
                    remaining = MoneyPaise(remainingPaise),
                    previousMonthSpent = MoneyPaise(prevSpentPaise),
                    alertThresholdPct = budget.alertThresholdPct,
                    usagePercentage = pct,
                    isAlertTriggered = pct >= budget.alertThresholdPct,
                    isOverBudget = spentPaise > limitPaise
                )
            }
        }
    }

    suspend fun upsertBudget(
        monthKey: String,
        categoryId: String?,
        limitPaise: Long,
        alertThresholdPct: Int = 80
    ): BudgetEntity {
        require(limitPaise > 0L) { "Budget limit must be > 0 paise" }
        val now = DateTimeUtils.nowEpochMs()
        val existing = budgetDao.findBudget(monthKey, categoryId)
        val entity = existing?.copy(
            limitPaise = limitPaise,
            alertThresholdPct = alertThresholdPct.coerceIn(50, 100),
            updatedAtEpochMs = now
        ) ?: BudgetEntity(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            monthKey = monthKey,
            limitPaise = limitPaise,
            alertThresholdPct = alertThresholdPct.coerceIn(50, 100),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        budgetDao.upsertBudget(entity)
        return entity
    }

    suspend fun deleteBudget(budgetId: String) {
        budgetDao.deleteBudgetById(budgetId)
    }

    private fun matchingCategoryIds(
        categoryId: String?,
        childIdsByParent: Map<String, List<CategoryEntity>>
    ): Set<String> {
        if (categoryId == null) return emptySet()
        val children = childIdsByParent[categoryId].orEmpty().map { it.id }
        return buildSet {
            add(categoryId)
            addAll(children)
        }
    }
}
