package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LedgerTransactionType {
    OPENING_BALANCE,
    INCOME,
    EXPENSE,
    TRANSFER,
    CREDIT_CARD_EXPENSE,
    CREDIT_CARD_PAYMENT,
    SPLIT_EXPENSE,
    SPLIT_REIMBURSEMENT,
    SPLIT_SETTLEMENT,
    DEBT_LENT,
    DEBT_BORROWED,
    DEBT_REPAYMENT,
    LOAN_EMI,
    LOAN_PREPAYMENT,
    REVERSAL
}

enum class PaymentMethod {
    BANK,
    CASH,
    SAVINGS,
    CREDIT_CARD,
    CUSTOM
}

@Entity(
    tableName = "ledger_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LedgerTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["reverses_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LedgerTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["replaced_by_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["tx_timestamp_epoch_ms"]),
        Index(value = ["tx_type"]),
        Index(value = ["category_id"]),
        Index(value = ["reverses_transaction_id"]),
        Index(value = ["replaced_by_transaction_id"]),
        Index(value = ["is_voided"])
    ]
)
data class LedgerTransactionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "tx_type")
    val txType: LedgerTransactionType,
    @ColumnInfo(name = "tx_timestamp_epoch_ms")
    val txTimestampEpochMs: Long,
    @ColumnInfo(name = "total_amount_paise")
    val totalAmountPaise: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    val description: String,
    val notes: String? = null,
    @ColumnInfo(name = "source_module")
    val sourceModule: String = "FINANCE",
    @ColumnInfo(name = "source_record_id")
    val sourceRecordId: String? = null,
    @ColumnInfo(name = "reverses_transaction_id")
    val reversesTransactionId: String? = null,
    @ColumnInfo(name = "replaced_by_transaction_id")
    val replacedByTransactionId: String? = null,
    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
