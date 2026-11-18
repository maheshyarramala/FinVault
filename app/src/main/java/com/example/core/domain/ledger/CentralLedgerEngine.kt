package com.example.core.domain.ledger

import androidx.room.withTransaction
import com.example.core.common.DateTimeUtils
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.EntrySide
import com.example.core.database.entity.LedgerAccountClass
import com.example.core.database.entity.LedgerEntryEntity
import com.example.core.database.entity.LedgerTransactionEntity
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Centralized double-entry financial transaction engine.
 * Every monetary mutation across Finance, Credit Cards, Split Bill, Debt, and Loans
 * must execute through this engine inside atomic database transactions.
 */
class CentralLedgerEngine(
    private val database: FinVaultDatabase
) {
    private val accountDao = database.accountDao()
    private val ledgerDao = database.ledgerDao()
    private val mutex = Mutex()

    suspend fun createAccount(spec: CreateAccountSpec): AccountEntity = mutex.withLock {
        require(spec.name.isNotBlank()) { "Account name cannot be blank" }
        require(spec.openingBalancePaise >= 0L) {
            "Opening balance must be non-negative paise"
        }

        return database.withTransaction {
            val existing = accountDao.getAccountById(spec.id)
            if (existing != null) return@withTransaction existing

            val account = AccountEntity(
                id = spec.id,
                name = spec.name.trim(),
                accountType = spec.accountType,
                openingBalancePaise = spec.openingBalancePaise,
                currentBalancePaise = 0L,
                institutionName = spec.institutionName?.trim()?.ifBlank { null },
                accountLast4 = spec.accountLast4?.trim()?.ifBlank { null },
                isDefault = spec.isDefault,
                isArchived = false,
                createdAtEpochMs = spec.timestampEpochMs,
                updatedAtEpochMs = spec.timestampEpochMs
            )
            accountDao.insertAccount(account)
            if (spec.isDefault) {
                accountDao.clearOtherDefaults(account.id, spec.timestampEpochMs)
            }

            if (spec.openingBalancePaise > 0L) {
                val txId = UUID.randomUUID().toString()
                val isCard = spec.accountType == AccountType.CREDIT_CARD
                val entries = if (isCard) {
                    listOf(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = null,
                            ledgerClass = LedgerAccountClass.EQUITY,
                            entrySide = EntrySide.DEBIT,
                            amountPaise = spec.openingBalancePaise,
                            entryOrder = 0,
                            memo = "Opening card liability"
                        ),
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = account.id,
                            ledgerClass = LedgerAccountClass.LIABILITY,
                            entrySide = EntrySide.CREDIT,
                            amountPaise = spec.openingBalancePaise,
                            entryOrder = 1,
                            memo = "Opening balance for ${account.name}"
                        )
                    )
                } else {
                    listOf(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = account.id,
                            ledgerClass = LedgerAccountClass.ASSET,
                            entrySide = EntrySide.DEBIT,
                            amountPaise = spec.openingBalancePaise,
                            entryOrder = 0,
                            memo = "Opening balance for ${account.name}"
                        ),
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = null,
                            ledgerClass = LedgerAccountClass.EQUITY,
                            entrySide = EntrySide.CREDIT,
                            amountPaise = spec.openingBalancePaise,
                            entryOrder = 1,
                            memo = "Opening equity"
                        )
                    )
                }

                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.OPENING_BALANCE,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.openingBalancePaise,
                    categoryId = null,
                    paymentMethod = accountTypeToPaymentMethod(spec.accountType),
                    description = "Opening balance: ${account.name}",
                    sourceModule = "ACCOUNTS",
                    sourceRecordId = account.id,
                    createdAtEpochMs = spec.timestampEpochMs,
                    updatedAtEpochMs = spec.timestampEpochMs
                )
                commitBalancedTransaction(tx, entries, spec.timestampEpochMs)
            }

            accountDao.getAccountById(account.id)
                ?: error("Account disappeared inside transaction")
        }
    }

    /**
     * Scoped handle passed inside [withAtomicLedgerLock] so external domain repositories
     * (such as Split Bill) can persist module records and Finance ledger records in a
     * single atomic SQLite transaction with rollback safety.
     */
    inner class LedgerTransactionScope internal constructor() {
        suspend fun post(spec: LedgerPostSpec): LedgerPostResult = postUnchecked(spec)

        suspend fun editTransaction(
            originalTransactionId: String,
            updatedSpec: LedgerPostSpec
        ): LedgerPostResult = editTransactionUnchecked(originalTransactionId, updatedSpec)

        suspend fun reverseOrDeleteTransaction(
            transactionId: String,
            idempotencyKey: String = "rev_$transactionId",
            reason: String = "Deleted by user"
        ): LedgerPostResult = reverseOrDeleteUnchecked(transactionId, idempotencyKey, reason)
    }

    suspend fun <T> withAtomicLedgerLock(block: suspend LedgerTransactionScope.() -> T): T =
        mutex.withLock {
            database.withTransaction {
                LedgerTransactionScope().block()
            }
        }

    suspend fun post(spec: LedgerPostSpec): LedgerPostResult = mutex.withLock {
        database.withTransaction {
            postUnchecked(spec)
        }
    }

    private suspend fun postUnchecked(spec: LedgerPostSpec): LedgerPostResult {
        require(spec.idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }
        require(spec.description.isNotBlank()) { "Description cannot be blank" }

        val existing = ledgerDao.getTransactionByIdempotencyKey(spec.idempotencyKey)
        if (existing != null) {
            return LedgerPostResult(
                transactionId = existing.id,
                wasIdempotentReplay = true
            )
        }

        val now = DateTimeUtils.nowEpochMs()
        val (tx, entries) = buildTransactionAndEntries(spec, now)
        commitBalancedTransaction(tx, entries, now)
        return LedgerPostResult(transactionId = tx.id, wasIdempotentReplay = false)
    }

    /**
     * Non-destructive deletion / voiding: voids the target transaction and posts
     * an offsetting double-entry REVERSAL transaction that restores account balances.
     */
    suspend fun reverseOrDeleteTransaction(
        transactionId: String,
        idempotencyKey: String = "rev_$transactionId",
        reason: String = "Deleted by user"
    ): LedgerPostResult = mutex.withLock {
        database.withTransaction {
            reverseOrDeleteUnchecked(transactionId, idempotencyKey, reason)
        }
    }

    private suspend fun reverseOrDeleteUnchecked(
        transactionId: String,
        idempotencyKey: String,
        reason: String
    ): LedgerPostResult {
        require(idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }

        val replay = ledgerDao.getTransactionByIdempotencyKey(idempotencyKey)
        if (replay != null) {
            return LedgerPostResult(
                transactionId = replay.id,
                wasIdempotentReplay = true
            )
        }

        val original = ledgerDao.getTransactionById(transactionId)
            ?: throw IllegalArgumentException("Transaction not found: $transactionId")
        check(!original.isVoided) { "Transaction $transactionId is already voided" }

        val now = DateTimeUtils.nowEpochMs()
        val reversalId = postReversalEntries(
            original = original,
            reversalIdempotencyKey = idempotencyKey,
            reason = reason,
            replacedById = null,
            now = now
        )
        return LedgerPostResult(transactionId = reversalId, wasIdempotentReplay = false)
    }

    /**
     * Atomically edits an existing transaction by reversing its original entries
     * and posting a replacement double-entry transaction, linking the audit trail.
     */
    suspend fun editTransaction(
        originalTransactionId: String,
        updatedSpec: LedgerPostSpec
    ): LedgerPostResult = mutex.withLock {
        database.withTransaction {
            editTransactionUnchecked(originalTransactionId, updatedSpec)
        }
    }

    private suspend fun editTransactionUnchecked(
        originalTransactionId: String,
        updatedSpec: LedgerPostSpec
    ): LedgerPostResult {
        require(updatedSpec.idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }
        require(updatedSpec.description.isNotBlank()) { "Description cannot be blank" }

        val replay = ledgerDao.getTransactionByIdempotencyKey(updatedSpec.idempotencyKey)
        if (replay != null) {
            return LedgerPostResult(
                transactionId = replay.id,
                wasIdempotentReplay = true
            )
        }

        val original = ledgerDao.getTransactionById(originalTransactionId)
            ?: throw IllegalArgumentException("Transaction not found: $originalTransactionId")
        check(!original.isVoided) { "Cannot edit an already voided transaction" }
        check(original.txType != LedgerTransactionType.REVERSAL) {
            "Cannot edit a reversal transaction"
        }

        val now = DateTimeUtils.nowEpochMs()
        val (replacementTx, replacementEntries) = buildTransactionAndEntries(updatedSpec, now)

        commitBalancedTransaction(replacementTx, replacementEntries, now)

        postReversalEntries(
            original = original,
            reversalIdempotencyKey = "edit_rev_${original.id}_${updatedSpec.idempotencyKey}",
            reason = "Edited: ${updatedSpec.description}",
            replacedById = replacementTx.id,
            now = now
        )

        return LedgerPostResult(transactionId = replacementTx.id, wasIdempotentReplay = false)
    }

    suspend fun reconcileAccountBalance(accountId: String): AccountReconciliationResult {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        val debitMinusCredit = ledgerDao.computeNetDebitMinusCreditForAccount(accountId)
        val ledgerDerived = if (account.accountType == AccountType.CREDIT_CARD) {
            -debitMinusCredit
        } else {
            debitMinusCredit
        }
        return AccountReconciliationResult(
            accountId = account.id,
            storedBalancePaise = account.currentBalancePaise,
            ledgerDerivedBalancePaise = ledgerDerived,
            isReconciled = account.currentBalancePaise == ledgerDerived
        )
    }

    suspend fun verifyGlobalLedgerIntegrity(): GlobalLedgerIntegrityReport {
        val totalDebits = ledgerDao.sumAllDebitsPaise()
        val totalCredits = ledgerDao.sumAllCreditsPaise()
        val accounts = accountDao.getActiveAccountsOnce()
        val reconciliations = accounts.map { reconcileAccountBalance(it.id) }
        return GlobalLedgerIntegrityReport(
            totalDebitsPaise = totalDebits,
            totalCreditsPaise = totalCredits,
            isDoubleEntryBalanced = totalDebits == totalCredits,
            accountReconciliations = reconciliations
        )
    }

    private suspend fun postReversalEntries(
        original: LedgerTransactionEntity,
        reversalIdempotencyKey: String,
        reason: String,
        replacedById: String?,
        now: Long
    ): String {
        val originalEntries = ledgerDao.getEntriesForTransaction(original.id)
        check(originalEntries.isNotEmpty()) {
            "Original transaction ${original.id} has no ledger entries"
        }

        ledgerDao.markTransactionVoided(
            transactionId = original.id,
            replacedById = replacedById,
            updatedAtEpochMs = now
        )

        val reversalTxId = UUID.randomUUID().toString()
        val flippedEntries = originalEntries.mapIndexed { index, entry ->
            entry.copy(
                id = UUID.randomUUID().toString(),
                transactionId = reversalTxId,
                entrySide = if (entry.entrySide == EntrySide.DEBIT) {
                    EntrySide.CREDIT
                } else {
                    EntrySide.DEBIT
                },
                entryOrder = index,
                memo = "Reversal of ${original.id}"
            )
        }

        val reversalTx = LedgerTransactionEntity(
            id = reversalTxId,
            idempotencyKey = reversalIdempotencyKey,
            txType = LedgerTransactionType.REVERSAL,
            txTimestampEpochMs = now,
            totalAmountPaise = original.totalAmountPaise,
            categoryId = original.categoryId,
            paymentMethod = original.paymentMethod,
            description = "Reversal: ${original.description}",
            notes = reason,
            sourceModule = original.sourceModule,
            sourceRecordId = original.sourceRecordId,
            reversesTransactionId = original.id,
            replacedByTransactionId = null,
            isVoided = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        commitBalancedTransaction(reversalTx, flippedEntries, now)
        return reversalTxId
    }

    private suspend fun buildTransactionAndEntries(
        spec: LedgerPostSpec,
        now: Long
    ): Pair<LedgerTransactionEntity, List<LedgerEntryEntity>> {
        val txId = UUID.randomUUID().toString()
        return when (spec) {
            is LedgerPostSpec.Expense -> {
                require(spec.amountPaise > 0L) { "Expense amount must be > 0 paise" }
                val account = requireActiveAssetAccount(spec.accountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = if (spec.isSplitExpense) {
                        LedgerTransactionType.SPLIT_EXPENSE
                    } else {
                        LedgerTransactionType.EXPENSE
                    },
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = spec.categoryId,
                    paymentMethod = spec.paymentMethod,
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = null,
                        categoryId = spec.categoryId,
                        ledgerClass = LedgerAccountClass.EXPENSE,
                        entrySide = EntrySide.DEBIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 0,
                        memo = spec.description
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = account.id,
                        categoryId = spec.categoryId,
                        ledgerClass = LedgerAccountClass.ASSET,
                        entrySide = EntrySide.CREDIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 1,
                        memo = "Paid from ${account.name}"
                    )
                )
                tx to entries
            }

            is LedgerPostSpec.CreditCardExpense -> {
                require(spec.amountPaise > 0L) { "Card expense amount must be > 0 paise" }
                val card = requireActiveCardAccount(spec.cardAccountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.CREDIT_CARD_EXPENSE,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = spec.categoryId,
                    paymentMethod = PaymentMethod.CREDIT_CARD,
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = null,
                        categoryId = spec.categoryId,
                        ledgerClass = LedgerAccountClass.EXPENSE,
                        entrySide = EntrySide.DEBIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 0,
                        memo = spec.description
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = card.id,
                        categoryId = spec.categoryId,
                        ledgerClass = LedgerAccountClass.LIABILITY,
                        entrySide = EntrySide.CREDIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 1,
                        memo = "Charged to ${card.name}"
                    )
                )
                tx to entries
            }

            is LedgerPostSpec.Income -> {
                require(spec.amountPaise > 0L) { "Income amount must be > 0 paise" }
                val account = requireActiveAssetAccount(spec.destinationAccountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = if (spec.isSplitReimbursement) {
                        LedgerTransactionType.SPLIT_REIMBURSEMENT
                    } else {
                        LedgerTransactionType.INCOME
                    },
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = spec.categoryId,
                    paymentMethod = accountTypeToPaymentMethod(account.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = account.id,
                        categoryId = spec.categoryId,
                        ledgerClass = LedgerAccountClass.ASSET,
                        entrySide = EntrySide.DEBIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 0,
                        memo = "Credited to ${account.name}"
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = null,
                        categoryId = spec.categoryId,
                        ledgerClass = if (spec.isSplitReimbursement) {
                            LedgerAccountClass.ASSET
                        } else {
                            LedgerAccountClass.INCOME
                        },
                        entrySide = EntrySide.CREDIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 1,
                        memo = if (spec.isSplitReimbursement) {
                            "Split Bill receivable settled"
                        } else {
                            spec.description
                        }
                    )
                )
                tx to entries
            }

            is LedgerPostSpec.Transfer -> {
                require(spec.amountPaise > 0L) { "Transfer amount must be > 0 paise" }
                require(spec.fromAccountId != spec.toAccountId) {
                    "Transfer source and destination accounts must differ"
                }
                val fromAccount = requireActiveAssetAccount(spec.fromAccountId)
                val toAccount = requireActiveAssetAccount(spec.toAccountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.TRANSFER,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = null,
                    paymentMethod = accountTypeToPaymentMethod(fromAccount.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = toAccount.id,
                        ledgerClass = LedgerAccountClass.ASSET,
                        entrySide = EntrySide.DEBIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 0,
                        memo = "Transfer in from ${fromAccount.name}"
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = fromAccount.id,
                        ledgerClass = LedgerAccountClass.ASSET,
                        entrySide = EntrySide.CREDIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 1,
                        memo = "Transfer out to ${toAccount.name}"
                    )
                )
                tx to entries
            }

            is LedgerPostSpec.CreditCardPayment -> {
                require(spec.amountPaise > 0L) { "Payment amount must be > 0 paise" }
                val fromAccount = requireActiveAssetAccount(spec.fromAssetAccountId)
                val cardAccount = requireActiveCardAccount(spec.cardAccountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.CREDIT_CARD_PAYMENT,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = null,
                    paymentMethod = accountTypeToPaymentMethod(fromAccount.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = cardAccount.id,
                        ledgerClass = LedgerAccountClass.LIABILITY,
                        entrySide = EntrySide.DEBIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 0,
                        memo = "Payment toward ${cardAccount.name}"
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = fromAccount.id,
                        ledgerClass = LedgerAccountClass.ASSET,
                        entrySide = EntrySide.CREDIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 1,
                        memo = "Paid from ${fromAccount.name}"
                    )
                )
                tx to entries
            }

            is LedgerPostSpec.LoanEmiPayment -> {
                require(spec.principalPaise >= 0L && spec.interestPaise >= 0L) {
                    "Principal and interest must be non-negative"
                }
                val totalEmiPaise = spec.principalPaise + spec.interestPaise
                require(totalEmiPaise > 0L) { "EMI payment total must be > 0 paise" }
                val account = requireActiveAssetAccount(spec.fromAccountId)

                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.LOAN_EMI,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = totalEmiPaise,
                    categoryId = spec.interestCategoryId,
                    paymentMethod = accountTypeToPaymentMethod(account.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )

                val entries = buildList {
                    var order = 0
                    if (spec.principalPaise > 0L) {
                        add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                categoryId = null,
                                ledgerClass = LedgerAccountClass.LIABILITY,
                                entrySide = EntrySide.DEBIT,
                                amountPaise = spec.principalPaise,
                                entryOrder = order++,
                                memo = "Loan principal reduction"
                            )
                        )
                    }
                    if (spec.interestPaise > 0L) {
                        add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                categoryId = spec.interestCategoryId,
                                ledgerClass = LedgerAccountClass.EXPENSE,
                                entrySide = EntrySide.DEBIT,
                                amountPaise = spec.interestPaise,
                                entryOrder = order++,
                                memo = "Loan interest expense"
                            )
                        )
                    }
                    add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = account.id,
                            categoryId = null,
                            ledgerClass = LedgerAccountClass.ASSET,
                            entrySide = EntrySide.CREDIT,
                            amountPaise = totalEmiPaise,
                            entryOrder = order,
                            memo = "EMI paid from ${account.name}"
                        )
                    )
                }
                tx to entries
            }

            is LedgerPostSpec.LoanPrepayment -> {
                require(spec.principalPaise > 0L && spec.feePaise >= 0L) {
                    "Prepayment principal must be > 0 and fee >= 0"
                }
                val totalPaise = spec.principalPaise + spec.feePaise
                val account = requireActiveAssetAccount(spec.fromAccountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.LOAN_PREPAYMENT,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = totalPaise,
                    categoryId = spec.feeCategoryId,
                    paymentMethod = accountTypeToPaymentMethod(account.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = buildList {
                    var order = 0
                    add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = null,
                            ledgerClass = LedgerAccountClass.LIABILITY,
                            entrySide = EntrySide.DEBIT,
                            amountPaise = spec.principalPaise,
                            entryOrder = order++,
                            memo = "Loan principal prepayment"
                        )
                    )
                    if (spec.feePaise > 0L) {
                        add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                categoryId = spec.feeCategoryId,
                                ledgerClass = LedgerAccountClass.EXPENSE,
                                entrySide = EntrySide.DEBIT,
                                amountPaise = spec.feePaise,
                                entryOrder = order++,
                                memo = "Loan prepayment fee"
                            )
                        )
                    }
                    add(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = account.id,
                            ledgerClass = LedgerAccountClass.ASSET,
                            entrySide = EntrySide.CREDIT,
                            amountPaise = totalPaise,
                            entryOrder = order,
                            memo = "Prepayment from ${account.name}"
                        )
                    )
                }
                tx to entries
            }

            is LedgerPostSpec.DebtMovement -> {
                require(spec.amountPaise > 0L) { "Debt amount must be > 0 paise" }
                require(spec.principalPaise >= 0L && spec.interestPaise >= 0L) {
                    "Debt principal and interest cannot be negative"
                }
                require(Math.addExact(spec.principalPaise, spec.interestPaise) == spec.amountPaise) {
                    "Debt total must equal principal plus interest"
                }
                if (spec.kind == DebtLedgerKind.LENT_OUT || spec.kind == DebtLedgerKind.BORROWED_IN) {
                    require(spec.principalPaise == spec.amountPaise && spec.interestPaise == 0L) {
                        "Initial debt movements must contain principal only"
                    }
                }
                val account = requireActiveAssetAccount(spec.accountId)
                val txType = when (spec.kind) {
                    DebtLedgerKind.LENT_OUT -> LedgerTransactionType.DEBT_LENT
                    DebtLedgerKind.BORROWED_IN -> LedgerTransactionType.DEBT_BORROWED
                    DebtLedgerKind.REPAYMENT_RECEIVED,
                    DebtLedgerKind.REPAYMENT_PAID -> LedgerTransactionType.DEBT_REPAYMENT
                }
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = txType,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = null,
                    paymentMethod = accountTypeToPaymentMethod(account.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = when (spec.kind) {
                    DebtLedgerKind.LENT_OUT -> listOf(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = null,
                            ledgerClass = LedgerAccountClass.ASSET,
                            entrySide = EntrySide.DEBIT,
                            amountPaise = spec.amountPaise,
                            entryOrder = 0,
                            memo = "Debt receivable"
                        ),
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = account.id,
                            ledgerClass = LedgerAccountClass.ASSET,
                            entrySide = EntrySide.CREDIT,
                            amountPaise = spec.amountPaise,
                            entryOrder = 1,
                            memo = "Money lent from ${account.name}"
                        )
                    )

                    DebtLedgerKind.BORROWED_IN -> listOf(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = account.id,
                            ledgerClass = LedgerAccountClass.ASSET,
                            entrySide = EntrySide.DEBIT,
                            amountPaise = spec.amountPaise,
                            entryOrder = 0,
                            memo = "Money borrowed into ${account.name}"
                        ),
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            transactionId = txId,
                            accountId = null,
                            ledgerClass = LedgerAccountClass.LIABILITY,
                            entrySide = EntrySide.CREDIT,
                            amountPaise = spec.amountPaise,
                            entryOrder = 1,
                            memo = "Debt payable"
                        )
                    )

                    DebtLedgerKind.REPAYMENT_RECEIVED -> buildList {
                        add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = account.id,
                                ledgerClass = LedgerAccountClass.ASSET,
                                entrySide = EntrySide.DEBIT,
                                amountPaise = spec.amountPaise,
                                entryOrder = 0,
                                memo = "Debt repayment received in ${account.name}"
                            )
                        )
                        if (spec.principalPaise > 0L) add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                ledgerClass = LedgerAccountClass.ASSET,
                                entrySide = EntrySide.CREDIT,
                                amountPaise = spec.principalPaise,
                                entryOrder = 1,
                                memo = "Debt receivable principal settled"
                            )
                        )
                        if (spec.interestPaise > 0L) add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                ledgerClass = LedgerAccountClass.INCOME,
                                entrySide = EntrySide.CREDIT,
                                amountPaise = spec.interestPaise,
                                entryOrder = 2,
                                memo = "Interest received on lent amount"
                            )
                        )
                    }

                    DebtLedgerKind.REPAYMENT_PAID -> buildList {
                        if (spec.principalPaise > 0L) add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                ledgerClass = LedgerAccountClass.LIABILITY,
                                entrySide = EntrySide.DEBIT,
                                amountPaise = spec.principalPaise,
                                entryOrder = 0,
                                memo = "Debt payable principal reduced"
                            )
                        )
                        if (spec.interestPaise > 0L) add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = null,
                                ledgerClass = LedgerAccountClass.EXPENSE,
                                entrySide = EntrySide.DEBIT,
                                amountPaise = spec.interestPaise,
                                entryOrder = 1,
                                memo = "Interest expense on borrowed amount"
                            )
                        )
                        add(
                            LedgerEntryEntity(
                                id = UUID.randomUUID().toString(),
                                transactionId = txId,
                                accountId = account.id,
                                ledgerClass = LedgerAccountClass.ASSET,
                                entrySide = EntrySide.CREDIT,
                                amountPaise = spec.amountPaise,
                                entryOrder = 2,
                                memo = "Debt repayment from ${account.name}"
                            )
                        )
                    }
                }
                tx to entries
            }

            is LedgerPostSpec.SplitSettlement -> {
                require(spec.amountPaise > 0L) { "Settlement amount must be > 0 paise" }
                val account = requireActiveAssetAccount(spec.fromAccountId)
                val tx = LedgerTransactionEntity(
                    id = txId,
                    idempotencyKey = spec.idempotencyKey,
                    txType = LedgerTransactionType.SPLIT_SETTLEMENT,
                    txTimestampEpochMs = spec.timestampEpochMs,
                    totalAmountPaise = spec.amountPaise,
                    categoryId = null,
                    paymentMethod = accountTypeToPaymentMethod(account.accountType),
                    description = spec.description.trim(),
                    notes = spec.notes,
                    sourceModule = spec.sourceModule,
                    sourceRecordId = spec.sourceRecordId,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                val entries = listOf(
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = null,
                        ledgerClass = LedgerAccountClass.LIABILITY,
                        entrySide = EntrySide.DEBIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 0,
                        memo = "Split settlement paid to ${spec.participantName}"
                    ),
                    LedgerEntryEntity(
                        id = UUID.randomUUID().toString(),
                        transactionId = txId,
                        accountId = account.id,
                        ledgerClass = LedgerAccountClass.ASSET,
                        entrySide = EntrySide.CREDIT,
                        amountPaise = spec.amountPaise,
                        entryOrder = 1,
                        memo = "Paid from ${account.name}"
                    )
                )
                tx to entries
            }
        }
    }

    private suspend fun commitBalancedTransaction(
        transaction: LedgerTransactionEntity,
        entries: List<LedgerEntryEntity>,
        now: Long
    ) {
        val totalDebits = entries.filter { it.entrySide == EntrySide.DEBIT }.sumOf { it.amountPaise }
        val totalCredits = entries.filter { it.entrySide == EntrySide.CREDIT }.sumOf { it.amountPaise }
        check(totalDebits > 0L && totalDebits == totalCredits) {
            "Double-entry imbalance: debits=$totalDebits credits=$totalCredits"
        }

        ledgerDao.insertTransaction(transaction)
        ledgerDao.insertEntries(entries)

        for (entry in entries) {
            val accId = entry.accountId ?: continue
            val account = accountDao.getAccountById(accId)
                ?: throw IllegalStateException("Referenced account $accId missing")
            val delta = if (account.accountType == AccountType.CREDIT_CARD) {
                if (entry.entrySide == EntrySide.CREDIT) entry.amountPaise else -entry.amountPaise
            } else {
                if (entry.entrySide == EntrySide.DEBIT) entry.amountPaise else -entry.amountPaise
            }
            val updatedRows = accountDao.adjustBalancePaise(accId, delta, now)
            check(updatedRows == 1) { "Failed to update balance for account $accId" }
        }
    }

    private suspend fun requireActiveAssetAccount(accountId: String): AccountEntity {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Account not found: $accountId")
        check(!account.isArchived) { "Account ${account.name} is archived" }
        require(account.accountType != AccountType.CREDIT_CARD) {
            "Expected asset account, got CREDIT_CARD (${account.name})"
        }
        return account
    }

    private suspend fun requireActiveCardAccount(accountId: String): AccountEntity {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Credit card account not found: $accountId")
        check(!account.isArchived) { "Card ${account.name} is archived" }
        require(account.accountType == AccountType.CREDIT_CARD) {
            "Expected CREDIT_CARD account, got ${account.accountType} (${account.name})"
        }
        return account
    }

    private fun accountTypeToPaymentMethod(type: AccountType): PaymentMethod = when (type) {
        AccountType.BANK -> PaymentMethod.BANK
        AccountType.CASH -> PaymentMethod.CASH
        AccountType.SAVINGS -> PaymentMethod.SAVINGS
        AccountType.CREDIT_CARD -> PaymentMethod.CREDIT_CARD
        AccountType.CUSTOM -> PaymentMethod.CUSTOM
    }
}
