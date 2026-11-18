package com.example.core.data.repository

import com.example.core.common.DateTimeUtils
import com.example.core.database.dao.AccountDao
import com.example.core.database.dao.RecurringRuleDao
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringFrequency
import com.example.core.database.entity.RecurringRuleEntity
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.LedgerPostResult
import com.example.core.domain.ledger.LedgerPostSpec
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RecurringRuleRepository(
    private val recurringRuleDao: RecurringRuleDao,
    private val accountDao: AccountDao,
    private val ledgerEngine: CentralLedgerEngine
) {
    val activeRules: Flow<List<RecurringRuleEntity>> = recurringRuleDao.observeActiveRules()

    suspend fun createRule(
        txType: LedgerTransactionType,
        amountPaise: Long,
        accountId: String,
        categoryId: String?,
        paymentMethod: PaymentMethod,
        description: String,
        notes: String?,
        frequency: RecurringFrequency,
        firstRunEpochMs: Long
    ): RecurringRuleEntity {
        val nextRun = DateTimeUtils.advanceByFrequency(firstRunEpochMs, frequency)
        val rule = RecurringRuleEntity(
            id = UUID.randomUUID().toString(),
            txType = txType,
            amountPaise = amountPaise,
            accountId = accountId,
            categoryId = categoryId,
            paymentMethod = paymentMethod,
            description = description.trim(),
            notes = notes,
            frequency = frequency,
            nextRunEpochMs = nextRun,
            isActive = true,
            createdAtEpochMs = DateTimeUtils.nowEpochMs()
        )
        recurringRuleDao.insertRule(rule)
        return rule
    }

    suspend fun postNextOccurrence(ruleId: String): LedgerPostResult? {
        val rule = recurringRuleDao.getRuleById(ruleId) ?: return null
        val account = accountDao.getAccountById(rule.accountId) ?: return null
        val idempotencyKey = "rec_${rule.id}_${rule.nextRunEpochMs}"

        val spec: LedgerPostSpec = when {
            rule.txType == LedgerTransactionType.INCOME -> LedgerPostSpec.Income(
                destinationAccountId = rule.accountId,
                amountPaise = rule.amountPaise,
                categoryId = rule.categoryId,
                description = rule.description,
                notes = rule.notes,
                timestampEpochMs = rule.nextRunEpochMs,
                idempotencyKey = idempotencyKey,
                sourceModule = "RECURRING",
                sourceRecordId = rule.id
            )
            account.accountType == AccountType.CREDIT_CARD -> LedgerPostSpec.CreditCardExpense(
                cardAccountId = rule.accountId,
                amountPaise = rule.amountPaise,
                categoryId = rule.categoryId,
                description = rule.description,
                notes = rule.notes,
                timestampEpochMs = rule.nextRunEpochMs,
                idempotencyKey = idempotencyKey,
                sourceModule = "RECURRING",
                sourceRecordId = rule.id
            )
            else -> LedgerPostSpec.Expense(
                accountId = rule.accountId,
                amountPaise = rule.amountPaise,
                categoryId = rule.categoryId,
                paymentMethod = rule.paymentMethod,
                description = rule.description,
                notes = rule.notes,
                timestampEpochMs = rule.nextRunEpochMs,
                idempotencyKey = idempotencyKey,
                sourceModule = "RECURRING",
                sourceRecordId = rule.id
            )
        }

        val result = ledgerEngine.post(spec)
        val nextScheduled = DateTimeUtils.advanceByFrequency(rule.nextRunEpochMs, rule.frequency)
        recurringRuleDao.updateRule(rule.copy(nextRunEpochMs = nextScheduled))
        return result
    }

    suspend fun deleteRule(ruleId: String) {
        recurringRuleDao.deleteRuleById(ruleId)
    }
}
