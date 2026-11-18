package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardStatementStatus {
    OPEN,
    CLOSED,
    PARTIALLY_PAID,
    PAID
}

@Entity(
    tableName = "card_statements",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["card_account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["card_account_id"]),
        Index(value = ["due_date_epoch_ms"])
    ]
)
data class CardStatementEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "card_account_id")
    val cardAccountId: String,
    @ColumnInfo(name = "period_start_epoch_ms")
    val periodStartEpochMs: Long,
    @ColumnInfo(name = "period_end_epoch_ms")
    val periodEndEpochMs: Long,
    @ColumnInfo(name = "due_date_epoch_ms")
    val dueDateEpochMs: Long,
    @ColumnInfo(name = "statement_balance_paise")
    val statementBalancePaise: Long,
    @ColumnInfo(name = "paid_amount_paise")
    val paidAmountPaise: Long = 0L,
    val status: CardStatementStatus = CardStatementStatus.OPEN,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long
)
