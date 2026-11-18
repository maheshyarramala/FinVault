package com.example.core.domain.ledger

import com.example.core.common.DateTimeUtils
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.PaymentMethod
import java.util.UUID

sealed interface LedgerPostSpec {
    val idempotencyKey: String
    val timestampEpochMs: Long
    val description: String
    val notes: String?
    val sourceModule: String
    val sourceRecordId: String?

    data class Expense(
        val accountId: String,
        val amountPaise: Long,
        val categoryId: String?,
        val paymentMethod: PaymentMethod = PaymentMethod.BANK,
        val isSplitExpense: Boolean = false,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = if (isSplitExpense) "SPLIT_BILL" else "FINANCE",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class CreditCardExpense(
        val cardAccountId: String,
        val amountPaise: Long,
        val categoryId: String?,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "CREDIT_CARD",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class Income(
        val destinationAccountId: String,
        val amountPaise: Long,
        val categoryId: String?,
        val isSplitReimbursement: Boolean = false,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = if (isSplitReimbursement) "SPLIT_BILL" else "FINANCE",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class Transfer(
        val fromAccountId: String,
        val toAccountId: String,
        val amountPaise: Long,
        override val description: String = "Account Transfer",
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "FINANCE",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class CreditCardPayment(
        val fromAssetAccountId: String,
        val cardAccountId: String,
        val amountPaise: Long,
        override val description: String = "Credit Card Payment",
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "CREDIT_CARD",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class LoanEmiPayment(
        val fromAccountId: String,
        val principalPaise: Long,
        val interestPaise: Long,
        val interestCategoryId: String?,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "LOAN",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class LoanPrepayment(
        val fromAccountId: String,
        val principalPaise: Long,
        val feePaise: Long = 0L,
        val feeCategoryId: String? = null,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "LOAN",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class DebtMovement(
        val accountId: String,
        val amountPaise: Long,
        val kind: DebtLedgerKind,
        /** Principal component of a repayment; defaults to the full amount for principal-only movements. */
        val principalPaise: Long = amountPaise,
        /** Interest component, classified separately from principal in the ledger. */
        val interestPaise: Long = 0L,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "DEBT",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec

    data class SplitSettlement(
        val fromAccountId: String,
        val amountPaise: Long,
        val participantName: String,
        override val description: String,
        override val notes: String? = null,
        override val timestampEpochMs: Long = DateTimeUtils.nowEpochMs(),
        override val idempotencyKey: String = UUID.randomUUID().toString(),
        override val sourceModule: String = "SPLIT_BILL",
        override val sourceRecordId: String? = null
    ) : LedgerPostSpec
}

enum class DebtLedgerKind {
    LENT_OUT,
    BORROWED_IN,
    REPAYMENT_RECEIVED,
    REPAYMENT_PAID
}

data class CreateAccountSpec(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accountType: AccountType,
    val openingBalancePaise: Long = 0L,
    val institutionName: String? = null,
    val accountLast4: String? = null,
    val isDefault: Boolean = false,
    val idempotencyKey: String = "acc_open_$id",
    val timestampEpochMs: Long = DateTimeUtils.nowEpochMs()
)

data class LedgerPostResult(
    val transactionId: String,
    val wasIdempotentReplay: Boolean
)

data class AccountReconciliationResult(
    val accountId: String,
    val storedBalancePaise: Long,
    val ledgerDerivedBalancePaise: Long,
    val isReconciled: Boolean
)

data class GlobalLedgerIntegrityReport(
    val totalDebitsPaise: Long,
    val totalCreditsPaise: Long,
    val isDoubleEntryBalanced: Boolean,
    val accountReconciliations: List<AccountReconciliationResult>
) {
    val isFullyHealthy: Boolean
        get() = isDoubleEntryBalanced && accountReconciliations.all { it.isReconciled }
}
