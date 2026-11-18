package com.example.core.data.repository

import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.AccountDao
import com.example.core.database.dao.CreditCardDao
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CardStatementEntity
import com.example.core.database.entity.CardStatementStatus
import com.example.core.database.entity.CreditCardEntity
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.LedgerPostResult
import com.example.core.domain.ledger.LedgerPostSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

data class CreditCardSummary(
    val accountId: String,
    val name: String,
    val last4: String?,
    val network: String?,
    val creditLimit: MoneyPaise,
    val outstandingBalance: MoneyPaise,
    val availableCredit: MoneyPaise,
    val utilizationPct: Int,
    val billingCycleDay: Int,
    val statementDay: Int,
    val dueDay: Int,
    val reminderEnabled: Boolean,
    val statements: List<CardStatementEntity>
)

class CreditCardRepository(
    private val creditCardDao: CreditCardDao,
    private val ledgerEngine: CentralLedgerEngine,
    private val accountDao: AccountDao? = null
) {
    val creditCards: Flow<List<CreditCardSummary>> =
        creditCardDao.observeCreditCardsWithDetails().map { items ->
            items.filter { !it.account.isArchived }.map { item ->
                val limit = item.card.creditLimitPaise
                val outstanding = item.account.currentBalancePaise
                val available = (limit - outstanding).coerceAtLeast(0L)
                val utilPct = if (limit > 0L) {
                    ((outstanding.toDouble() / limit.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                CreditCardSummary(
                    accountId = item.account.id,
                    name = item.account.name,
                    last4 = item.account.accountLast4,
                    network = item.card.network,
                    creditLimit = MoneyPaise(limit),
                    outstandingBalance = MoneyPaise(outstanding),
                    availableCredit = MoneyPaise(available),
                    utilizationPct = utilPct,
                    billingCycleDay = item.card.billingCycleDay,
                    statementDay = item.card.statementDay,
                    dueDay = item.card.dueDay,
                    reminderEnabled = item.card.reminderEnabled,
                    statements = item.statements.sortedByDescending { it.periodEndEpochMs }
                )
            }
        }

    suspend fun createCreditCard(
        name: String,
        last4: String?,
        network: String?,
        creditLimitPaise: Long,
        openingOutstandingPaise: Long,
        billingCycleDay: Int,
        statementDay: Int,
        dueDay: Int,
        reminderEnabled: Boolean = true
    ): CreditCardEntity {
        require(creditLimitPaise > 0L) { "Credit limit must be > 0 paise" }
        require(openingOutstandingPaise >= 0L) { "Outstanding balance must be >= 0" }
        val now = DateTimeUtils.nowEpochMs()
        val account = ledgerEngine.createAccount(
            CreateAccountSpec(
                name = name,
                accountType = AccountType.CREDIT_CARD,
                openingBalancePaise = openingOutstandingPaise,
                institutionName = network,
                accountLast4 = last4,
                timestampEpochMs = now
            )
        )
        val cardEntity = CreditCardEntity(
            accountId = account.id,
            creditLimitPaise = creditLimitPaise,
            billingCycleDay = billingCycleDay.coerceIn(1, 28),
            statementDay = statementDay.coerceIn(1, 28),
            dueDay = dueDay.coerceIn(1, 28),
            reminderEnabled = reminderEnabled,
            network = network?.trim()?.ifBlank { null },
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        creditCardDao.insertCreditCard(cardEntity)
        return cardEntity
    }

    suspend fun updateCreditCardDetails(
        accountId: String,
        name: String,
        last4: String?,
        network: String?,
        creditLimitPaise: Long,
        billingCycleDay: Int,
        statementDay: Int,
        dueDay: Int,
        reminderEnabled: Boolean
    ) {
        require(name.isNotBlank()) { "Card name cannot be blank" }
        require(creditLimitPaise > 0L) { "Credit limit must be > 0 paise" }
        val card = creditCardDao.getCardByAccountId(accountId)
            ?: throw IllegalArgumentException("Credit card not found: $accountId")
        val now = DateTimeUtils.nowEpochMs()
        val cleanNetwork = network?.trim()?.ifBlank { null }
        accountDao?.updateAccountMetadata(
            accountId = accountId,
            name = name,
            institutionName = cleanNetwork,
            accountLast4 = last4,
            isDefault = false,
            updatedAtEpochMs = now
        )
        creditCardDao.updateCreditCard(
            card.copy(
                creditLimitPaise = creditLimitPaise,
                billingCycleDay = billingCycleDay.coerceIn(1, 28),
                statementDay = statementDay.coerceIn(1, 28),
                dueDay = dueDay.coerceIn(1, 28),
                reminderEnabled = reminderEnabled,
                network = cleanNetwork,
                updatedAtEpochMs = now
            )
        )
    }

    suspend fun toggleReminder(accountId: String, enabled: Boolean) {
        val card = creditCardDao.getCardByAccountId(accountId) ?: return
        creditCardDao.updateCreditCard(
            card.copy(
                reminderEnabled = enabled,
                updatedAtEpochMs = DateTimeUtils.nowEpochMs()
            )
        )
    }

    suspend fun payCreditCard(
        fromAssetAccountId: String,
        cardAccountId: String,
        amountPaise: Long,
        description: String = "Credit Card Bill Payment",
        notes: String? = null,
        timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): LedgerPostResult {
        val result = ledgerEngine.post(
            LedgerPostSpec.CreditCardPayment(
                fromAssetAccountId = fromAssetAccountId,
                cardAccountId = cardAccountId,
                amountPaise = amountPaise,
                description = description,
                notes = notes,
                timestampEpochMs = timestampEpochMs,
                idempotencyKey = idempotencyKey
            )
        )
        if (!result.wasIdempotentReplay) {
            applyPaymentToStatements(cardAccountId, amountPaise)
        }
        return result
    }

    suspend fun editCardPayment(
        originalTransactionId: String,
        oldCardAccountId: String?,
        oldAmountPaise: Long,
        updatedSpec: LedgerPostSpec.CreditCardPayment
    ): LedgerPostResult {
        val result = ledgerEngine.editTransaction(originalTransactionId, updatedSpec)
        if (!result.wasIdempotentReplay) {
            if (oldCardAccountId != null && oldAmountPaise > 0L) {
                revertPaymentFromStatements(oldCardAccountId, oldAmountPaise)
            }
            applyPaymentToStatements(updatedSpec.cardAccountId, updatedSpec.amountPaise)
        }
        return result
    }

    suspend fun reverseCardPayment(
        transactionId: String,
        cardAccountId: String?,
        amountPaise: Long
    ): LedgerPostResult {
        val result = ledgerEngine.reverseOrDeleteTransaction(transactionId)
        if (!result.wasIdempotentReplay && cardAccountId != null && amountPaise > 0L) {
            revertPaymentFromStatements(cardAccountId, amountPaise)
        }
        return result
    }

    suspend fun applyPaymentToStatements(cardAccountId: String, amountPaise: Long) {
        var remaining = amountPaise
        val unpaid = creditCardDao.getUnpaidStatementsOldestFirst(cardAccountId)
        for (stmt in unpaid) {
            if (remaining <= 0L) break
            val dueOnStmt = (stmt.statementBalancePaise - stmt.paidAmountPaise).coerceAtLeast(0L)
            if (dueOnStmt == 0L) continue
            val allocation = minOf(remaining, dueOnStmt)
            val newPaid = stmt.paidAmountPaise + allocation
            val newStatus = if (newPaid >= stmt.statementBalancePaise) {
                CardStatementStatus.PAID
            } else {
                CardStatementStatus.PARTIALLY_PAID
            }
            creditCardDao.updateStatement(
                stmt.copy(
                    paidAmountPaise = newPaid,
                    status = newStatus
                )
            )
            remaining -= allocation
        }
    }

    suspend fun revertPaymentFromStatements(cardAccountId: String, amountPaise: Long) {
        var remainingToRevert = amountPaise
        val statements = creditCardDao.getStatementsForCardOnce(cardAccountId).reversed()
        for (stmt in statements) {
            if (remainingToRevert <= 0L) break
            if (stmt.paidAmountPaise <= 0L) continue
            val deduction = minOf(remainingToRevert, stmt.paidAmountPaise)
            val newPaid = stmt.paidAmountPaise - deduction
            val newStatus = when {
                stmt.statementBalancePaise <= 0L || newPaid >= stmt.statementBalancePaise -> CardStatementStatus.PAID
                newPaid > 0L -> CardStatementStatus.PARTIALLY_PAID
                else -> CardStatementStatus.CLOSED
            }
            creditCardDao.updateStatement(
                stmt.copy(
                    paidAmountPaise = newPaid,
                    status = newStatus
                )
            )
            remainingToRevert -= deduction
        }
    }

    suspend fun generateStatementSnapshot(cardAccountId: String): CardStatementEntity? {
        val card = creditCardDao.getCardByAccountId(cardAccountId) ?: return null
        val balance = ledgerEngine.reconcileAccountBalance(cardAccountId).storedBalancePaise
        val now = DateTimeUtils.nowEpochMs()
        val endCal = Calendar.getInstance().apply { timeInMillis = now }
        val startCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
        }
        val dueCal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, card.dueDay.coerceIn(1, 28))
            if (timeInMillis <= now) add(Calendar.MONTH, 1)
        }
        val statement = CardStatementEntity(
            id = UUID.randomUUID().toString(),
            cardAccountId = cardAccountId,
            periodStartEpochMs = startCal.timeInMillis,
            periodEndEpochMs = endCal.timeInMillis,
            dueDateEpochMs = dueCal.timeInMillis,
            statementBalancePaise = balance.coerceAtLeast(0L),
            paidAmountPaise = 0L,
            status = if (balance <= 0L) CardStatementStatus.PAID else CardStatementStatus.CLOSED,
            createdAtEpochMs = now
        )
        creditCardDao.insertStatement(statement)
        return statement
    }
}
