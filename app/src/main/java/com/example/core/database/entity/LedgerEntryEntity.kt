package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LedgerAccountClass {
    ASSET,
    LIABILITY,
    INCOME,
    EXPENSE,
    EQUITY
}

enum class EntrySide {
    DEBIT,
    CREDIT
}

@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = LedgerTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["transaction_id"]),
        Index(value = ["account_id"]),
        Index(value = ["category_id"])
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "ledger_class")
    val ledgerClass: LedgerAccountClass,
    @ColumnInfo(name = "entry_side")
    val entrySide: EntrySide,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "entry_order")
    val entryOrder: Int,
    val memo: String? = null
)
