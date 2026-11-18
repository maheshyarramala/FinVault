package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType {
    BANK,
    CASH,
    SAVINGS,
    CREDIT_CARD,
    CUSTOM
}

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["account_type"]),
        Index(value = ["is_archived"])
    ]
)
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "account_type")
    val accountType: AccountType,
    @ColumnInfo(name = "opening_balance_paise")
    val openingBalancePaise: Long,
    @ColumnInfo(name = "current_balance_paise")
    val currentBalancePaise: Long,
    @ColumnInfo(name = "institution_name")
    val institutionName: String? = null,
    @ColumnInfo(name = "account_last4")
    val accountLast4: String? = null,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
