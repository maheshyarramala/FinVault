package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecurringFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["category_id"]),
        Index(value = ["next_run_epoch_ms"])
    ]
)
data class RecurringRuleEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "tx_type")
    val txType: LedgerTransactionType,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod = PaymentMethod.BANK,
    val description: String,
    val notes: String? = null,
    val frequency: RecurringFrequency,
    @ColumnInfo(name = "next_run_epoch_ms")
    val nextRunEpochMs: Long,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)
