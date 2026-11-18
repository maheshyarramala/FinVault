package com.example.core.data.repository

import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.dao.AccountStatementEntryRow
import com.example.core.database.dao.CategorySpendAggregate
import com.example.core.database.dao.LedgerDao
import com.example.core.database.dao.LedgerTransactionWithEntries
import com.example.core.database.entity.AccountEntity
import com.example.core.domain.ledger.AccountReconciliationResult
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.GlobalLedgerIntegrityReport
import com.example.core.domain.ledger.LedgerPostResult
import com.example.core.domain.ledger.LedgerPostSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.example.core.database.dao.DebtLedgerSummaryRow
import com.example.core.database.dao.LoanEmiLedgerSummaryRow
import com.example.core.database.dao.SplitBillLedgerSummaryRow
import com.example.core.database.entity.LedgerTransactionType

data class MonthlyCashFlowBar(
    val monthKey: String,
    val shortLabel: String,
    val income: MoneyPaise,
    val expense: MoneyPaise
)

data class DebtLedgerSummary(
    val owedToYou: MoneyPaise,
    val youOwe: MoneyPaise,
    val netDebtPosition: MoneyPaise,
    val transactionCount: Int
)

data class SplitBillLedgerSummary(
    val totalSplitPaid: MoneyPaise,
    val totalReimbursed: MoneyPaise,
    val netOutflow: MoneyPaise,
    val transactionCount: Int
)

data class LoanEmiLedgerSummary(
    val totalPrincipalPaid: MoneyPaise,
    val totalInterestPaid: MoneyPaise,
    val currentMonthPaid: MoneyPaise,
    val paymentCount: Int
)

class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val engine: CentralLedgerEngine
) {
    val activeTransactions: Flow<List<LedgerTransactionWithEntries>> =
        ledgerDao.observeActiveTransactionsWithEntries()

    val fullAuditTrail: Flow<List<LedgerTransactionWithEntries>> =
        ledgerDao.observeFullAuditTrailWithEntries()

    fun observeRecentTransactions(limit: Int = 10): Flow<List<LedgerTransactionWithEntries>> =
        ledgerDao.observeRecentTransactions(limit)

    fun observeAccountStatementEntries(accountId: String): Flow<List<AccountStatementEntryRow>> =
        ledgerDao.observeAccountStatementEntries(accountId)

    fun observeMonthlyIncome(nowMs: Long = DateTimeUtils.nowEpochMs()): Flow<MoneyPaise> {
        val (start, end) = DateTimeUtils.currentMonthRange(nowMs)
        return observeIncomeInRange(start, end)
    }

    fun observeMonthlyExpenses(nowMs: Long = DateTimeUtils.nowEpochMs()): Flow<MoneyPaise> {
        val (start, end) = DateTimeUtils.currentMonthRange(nowMs)
        return observeExpensesInRange(start, end)
    }

    fun observeIncomeInRange(startEpochMs: Long, endEpochMs: Long): Flow<MoneyPaise> =
        ledgerDao.observeIncomeSumPaise(startEpochMs, endEpochMs).map { MoneyPaise(it) }

    fun observeExpensesInRange(startEpochMs: Long, endEpochMs: Long): Flow<MoneyPaise> =
        ledgerDao.observeExpenseSumPaise(startEpochMs, endEpochMs).map { MoneyPaise(it) }

    fun observePersonalDirectExpensesInRange(startEpochMs: Long, endEpochMs: Long): Flow<MoneyPaise> =
        ledgerDao.observePersonalDirectExpenseSumPaise(startEpochMs, endEpochMs).map { MoneyPaise(it) }

    fun observeCategoryExpensesForMonth(monthKey: String): Flow<List<CategorySpendAggregate>> {
        val (start, end) = DateTimeUtils.monthRangeFromKey(monthKey)
        return ledgerDao.observeCategoryExpenseTotals(start, end)
    }

    fun observeMonthlyCashFlowTrend(
        monthsCount: Int = 6,
        nowMs: Long = DateTimeUtils.nowEpochMs()
    ): Flow<List<MonthlyCashFlowBar>> {
        val currentMonthKey = DateTimeUtils.monthKey(nowMs)
        val monthKeys = (-(monthsCount - 1)..0).map { delta ->
            DateTimeUtils.shiftMonthKey(currentMonthKey, delta)
        }
        val (rangeStart, _) = DateTimeUtils.monthRangeFromKey(monthKeys.first())
        val (_, rangeEnd) = DateTimeUtils.monthRangeFromKey(monthKeys.last())

        return ledgerDao.observeActiveTransactionsInRange(rangeStart, rangeEnd).map { txs ->
            val grouped = txs.groupBy { DateTimeUtils.monthKey(it.txTimestampEpochMs) }
            monthKeys.map { key ->
                val list = grouped[key].orEmpty()
                val incomePaise = list
                    .filter { it.txType == LedgerTransactionType.INCOME }
                    .sumOf { it.totalAmountPaise }
                val expensePaise = list
                    .filter {
                        it.txType == LedgerTransactionType.EXPENSE ||
                            it.txType == LedgerTransactionType.CREDIT_CARD_EXPENSE ||
                            it.txType == LedgerTransactionType.SPLIT_EXPENSE
                    }
                    .sumOf { it.totalAmountPaise }
                MonthlyCashFlowBar(
                    monthKey = key,
                    shortLabel = DateTimeUtils.formatShortMonthLabel(key),
                    income = MoneyPaise(incomePaise),
                    expense = MoneyPaise(expensePaise)
                )
            }
        }
    }

    fun observeDebtLedgerSummary(): Flow<DebtLedgerSummary> =
        ledgerDao.observeDebtLedgerSummary().map { row: DebtLedgerSummaryRow ->
            val receivable = (row.totalLentPaise - row.totalLentRepaidPaise).coerceAtLeast(0L)
            val payable = (row.totalBorrowedPaise - row.totalBorrowedRepaidPaise).coerceAtLeast(0L)
            DebtLedgerSummary(
                owedToYou = MoneyPaise(receivable),
                youOwe = MoneyPaise(payable),
                netDebtPosition = MoneyPaise(receivable - payable),
                transactionCount = row.activeDebtTxCount
            )
        }

    fun observeSplitBillLedgerSummary(): Flow<SplitBillLedgerSummary> =
        ledgerDao.observeSplitBillLedgerSummary().map { row: SplitBillLedgerSummaryRow ->
            SplitBillLedgerSummary(
                totalSplitPaid = MoneyPaise(row.totalSplitPaidPaise),
                totalReimbursed = MoneyPaise(row.totalReimbursedPaise),
                netOutflow = MoneyPaise(row.totalSplitPaidPaise - row.totalReimbursedPaise),
                transactionCount = row.splitTxCount
            )
        }

    fun observeLoanEmiLedgerSummary(nowMs: Long = DateTimeUtils.nowEpochMs()): Flow<LoanEmiLedgerSummary> {
        val (monthStart, monthEnd) = DateTimeUtils.currentMonthRange(nowMs)
        return ledgerDao.observeLoanEmiLedgerSummary(monthStart, monthEnd).map { row: LoanEmiLedgerSummaryRow ->
            LoanEmiLedgerSummary(
                totalPrincipalPaid = MoneyPaise(row.totalPrincipalPaidPaise),
                totalInterestPaid = MoneyPaise(row.totalInterestPaidPaise),
                currentMonthPaid = MoneyPaise(row.currentMonthPaidPaise),
                paymentCount = row.loanPaymentCount
            )
        }
    }

    suspend fun getTransactionWithEntries(transactionId: String): LedgerTransactionWithEntries? =
        ledgerDao.getTransactionWithEntriesById(transactionId)

    suspend fun createAccount(spec: CreateAccountSpec): AccountEntity =
        engine.createAccount(spec)

    suspend fun postTransaction(spec: LedgerPostSpec): LedgerPostResult =
        engine.post(spec)

    suspend fun editTransaction(
        originalTransactionId: String,
        updatedSpec: LedgerPostSpec
    ): LedgerPostResult = engine.editTransaction(originalTransactionId, updatedSpec)

    suspend fun reverseOrDeleteTransaction(
        transactionId: String,
        idempotencyKey: String = "rev_$transactionId",
        reason: String = "Deleted by user"
    ): LedgerPostResult = engine.reverseOrDeleteTransaction(transactionId, idempotencyKey, reason)

    suspend fun reconcileAccount(accountId: String): AccountReconciliationResult =
        engine.reconcileAccountBalance(accountId)

    suspend fun verifyGlobalIntegrity(): GlobalLedgerIntegrityReport =
        engine.verifyGlobalLedgerIntegrity()
}
