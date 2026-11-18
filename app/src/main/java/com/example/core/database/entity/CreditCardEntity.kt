package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "credit_cards",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CreditCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "credit_limit_paise")
    val creditLimitPaise: Long,
    @ColumnInfo(name = "billing_cycle_day")
    val billingCycleDay: Int,
    @ColumnInfo(name = "statement_day")
    val statementDay: Int,
    @ColumnInfo(name = "due_day")
    val dueDay: Int,
    @ColumnInfo(name = "reminder_enabled")
    val reminderEnabled: Boolean = true,
    val network: String? = null,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
