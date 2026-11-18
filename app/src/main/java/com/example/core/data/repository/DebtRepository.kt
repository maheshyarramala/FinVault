package com.example.core.data.repository

import com.example.core.common.DateTimeUtils
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.DebtCompoundingFrequency
import com.example.core.database.entity.DebtInterestType
import com.example.core.database.entity.DebtRecordEntity
import com.example.core.database.entity.DebtRepaymentEntity
import com.example.core.database.entity.DebtStatus
import com.example.core.database.entity.DebtType
import com.example.core.domain.debt.DebtInterestCalculator
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.DebtLedgerKind
import com.example.core.domain.ledger.LedgerPostSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Persists personal debts and posts every cash movement through the central ledger.
 * Principal is tracked as receivable/payable; only interest is income/expense.
 */
class DebtRepository(
    private val database: FinVaultDatabase,
    private val ledgerEngine: CentralLedgerEngine
) {
    private val debtDao = database.debtDao()

    val debts: Flow<List<DebtRecordEntity>> = debtDao.observeDebts()
    val activeDebts: Flow<List<DebtRecordEntity>> = debtDao.observeActiveDebts()
    val totalLentOutstandingPaise: Flow<Long> = debtDao.observeTotalLentOutstandingPaise()
    val totalBorrowedOutstandingPaise: Flow<Long> = debtDao.observeTotalBorrowedOutstandingPaise()

    fun observeRepayments(debtId: String): Flow<List<DebtRepaymentEntity>> =
        debtDao.observeRepayments(debtId)

    suspend fun getDebt(debtId: String): DebtRecordEntity? = debtDao.getDebt(debtId)

    suspend fun createDebt(
        title: String,
        personName: String,
        type: DebtType,
        principalPaise: Long,
        accountId: String,
        interestType: DebtInterestType = DebtInterestType.NONE,
        annualInterestRateBps: Int = 0,
        fixedInterestPaise: Long = 0L,
        compoundingFrequency: DebtCompoundingFrequency = DebtCompoundingFrequency.MONTHLY,
        startEpochMs: Long = DateTimeUtils.nowEpochMs(),
        dueEpochMs: Long? = null,
        notes: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): DebtRecordEntity {
        require(title.isNotBlank()) { "Debt title cannot be blank" }
        require(personName.isNotBlank()) { "Person name cannot be blank" }
        require(principalPaise > 0L) { "Principal must be greater than zero" }
        require(annualInterestRateBps in 0..100_000)
        require(fixedInterestPaise >= 0L)
        require(dueEpochMs == null || dueEpochMs >= startEpochMs)

        val id = UUID.nameUUIDFromBytes("finvault-debt:$idempotencyKey".toByteArray()).toString()
        return ledgerEngine.withAtomicLedgerLock {
            val existing = debtDao.getDebt(id)
            if (existing != null) return@withAtomicLedgerLock existing
            val ledgerResult = post(
                LedgerPostSpec.DebtMovement(
                    accountId = accountId,
                    amountPaise = principalPaise,
                    kind = if (type == DebtType.LENT) DebtLedgerKind.LENT_OUT else DebtLedgerKind.BORROWED_IN,
                    description = if (type == DebtType.LENT) "Money lent: $title" else "Money borrowed: $title",
                    timestampEpochMs = startEpochMs,
                    idempotencyKey = "debt_create_$idempotencyKey",
                    sourceRecordId = id
                )
            )
            DebtRecordEntity(
                id = id,
                title = title.trim(),
                personName = personName.trim(),
                type = type,
                principalPaise = principalPaise,
                outstandingPrincipalPaise = principalPaise,
                annualInterestRateBps = annualInterestRateBps,
                interestType = interestType,
                fixedInterestPaise = fixedInterestPaise,
                compoundingFrequency = compoundingFrequency,
                startEpochMs = startEpochMs,
                dueEpochMs = dueEpochMs,
                accountId = accountId,
                initialLedgerTransactionId = ledgerResult.transactionId,
                status = DebtStatus.ACTIVE,
                notes = notes?.trim()?.ifBlank { null },
                createdAtEpochMs = DateTimeUtils.nowEpochMs(),
                updatedAtEpochMs = DateTimeUtils.nowEpochMs()
            ).also { debtDao.insertDebt(it) }
        }
    }

    suspend fun accruedInterestPaise(
        debtId: String,
        asOfDate: LocalDate = LocalDate.now()
    ): Long {
        val debt = debtDao.getDebt(debtId) ?: throw IllegalArgumentException("Debt not found")
        val terms = DebtInterestCalculator.Terms(
            type = when (debt.interestType) {
                DebtInterestType.NONE -> DebtInterestCalculator.InterestType.NONE
                DebtInterestType.SIMPLE -> DebtInterestCalculator.InterestType.SIMPLE
                DebtInterestType.COMPOUND -> DebtInterestCalculator.InterestType.COMPOUND
                DebtInterestType.FIXED -> DebtInterestCalculator.InterestType.FIXED
            },
            annualRateBps = debt.annualInterestRateBps,
            fixedInterestPaise = debt.fixedInterestPaise,
            frequency = when (debt.compoundingFrequency) {
                DebtCompoundingFrequency.MONTHLY -> DebtInterestCalculator.CompoundingFrequency.MONTHLY
                DebtCompoundingFrequency.QUARTERLY -> DebtInterestCalculator.CompoundingFrequency.QUARTERLY
                DebtCompoundingFrequency.HALF_YEARLY -> DebtInterestCalculator.CompoundingFrequency.HALF_YEARLY
                DebtCompoundingFrequency.YEARLY -> DebtInterestCalculator.CompoundingFrequency.YEARLY
            }
        )
        val lastRepayment = debtDao.getRepayments(debtId).maxByOrNull { it.paidAtEpochMs }
        val accrualStartEpochMs = lastRepayment?.paidAtEpochMs ?: debt.startEpochMs
        val startDate = Instant.ofEpochMilli(accrualStartEpochMs)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        return DebtInterestCalculator.calculate(
            principalPaise = debt.outstandingPrincipalPaise,
            terms = terms,
            startDate = startDate,
            asOfDate = asOfDate
        ).interestPaise
    }

    suspend fun recordRepayment(
        debtId: String,
        principalPaise: Long,
        interestPaise: Long,
        paymentAccountId: String,
        paidAtEpochMs: Long = DateTimeUtils.nowEpochMs(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        notes: String? = null
    ): DebtRepaymentEntity {
        require(principalPaise >= 0L && interestPaise >= 0L)
        val totalPaise = Math.addExact(principalPaise, interestPaise)
        require(totalPaise > 0L) { "Repayment must be greater than zero" }

        return ledgerEngine.withAtomicLedgerLock {
            debtDao.getRepaymentByIdempotencyKey(idempotencyKey)?.let {
                return@withAtomicLedgerLock it
            }
            val debt = debtDao.getDebt(debtId) ?: throw IllegalArgumentException("Debt not found")
            check(debt.status == DebtStatus.ACTIVE) { "Only active debts can be repaid" }
            require(principalPaise <= debt.outstandingPrincipalPaise) {
                "Principal repayment exceeds outstanding principal"
            }
            val ledgerResult = post(
                LedgerPostSpec.DebtMovement(
                    accountId = paymentAccountId,
                    amountPaise = totalPaise,
                    kind = if (debt.type == DebtType.LENT) {
                        DebtLedgerKind.REPAYMENT_RECEIVED
                    } else {
                        DebtLedgerKind.REPAYMENT_PAID
                    },
                    principalPaise = principalPaise,
                    interestPaise = interestPaise,
                    description = if (debt.type == DebtType.LENT) {
                        "Repayment received: ${debt.title}"
                    } else {
                        "Debt repayment: ${debt.title}"
                    },
                    notes = notes,
                    timestampEpochMs = paidAtEpochMs,
                    idempotencyKey = "debt_repayment_$idempotencyKey",
                    sourceRecordId = debt.id
                )
            )
            val now = DateTimeUtils.nowEpochMs()
            val repayment = DebtRepaymentEntity(
                id = UUID.randomUUID().toString(),
                debtId = debt.id,
                paymentAccountId = paymentAccountId,
                principalPaise = principalPaise,
                interestPaise = interestPaise,
                totalPaise = totalPaise,
                ledgerTransactionId = ledgerResult.transactionId,
                idempotencyKey = idempotencyKey,
                paidAtEpochMs = paidAtEpochMs,
                createdAtEpochMs = now
            )
            debtDao.insertRepayment(repayment)
            val remaining = debt.outstandingPrincipalPaise - principalPaise
            debtDao.updateDebt(
                debt.copy(
                    outstandingPrincipalPaise = remaining,
                    status = if (remaining == 0L) DebtStatus.SETTLED else DebtStatus.ACTIVE,
                    updatedAtEpochMs = now
                )
            )
            repayment
        }
    }
}
