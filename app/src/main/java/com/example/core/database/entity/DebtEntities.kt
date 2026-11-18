package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DebtType { LENT, BORROWED }
enum class DebtInterestType { NONE, SIMPLE, COMPOUND, FIXED }
enum class DebtCompoundingFrequency { MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY }
enum class DebtStatus { ACTIVE, SETTLED, ARCHIVED }

@Entity(
    tableName = "debt_records",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("account_id"), Index("status"), Index("type"), Index("created_at_epoch_ms")]
)
data class DebtRecordEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "person_name") val personName: String,
    val type: DebtType,
    @ColumnInfo(name = "principal_paise") val principalPaise: Long,
    @ColumnInfo(name = "outstanding_principal_paise") val outstandingPrincipalPaise: Long,
    @ColumnInfo(name = "annual_interest_rate_bps") val annualInterestRateBps: Int,
    @ColumnInfo(name = "interest_type") val interestType: DebtInterestType,
    @ColumnInfo(name = "fixed_interest_paise") val fixedInterestPaise: Long,
    @ColumnInfo(name = "compounding_frequency") val compoundingFrequency: DebtCompoundingFrequency,
    @ColumnInfo(name = "start_epoch_ms") val startEpochMs: Long,
    @ColumnInfo(name = "due_epoch_ms") val dueEpochMs: Long?,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "initial_ledger_transaction_id") val initialLedgerTransactionId: String,
    val status: DebtStatus,
    val notes: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long
)

@Entity(
    tableName = "debt_repayments",
    foreignKeys = [
        ForeignKey(
            entity = DebtRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["debt_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("debt_id"), Index("payment_account_id"), Index("paid_at_epoch_ms"), Index(value = ["idempotency_key"], unique = true)]
)
data class DebtRepaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "payment_account_id") val paymentAccountId: String,
    @ColumnInfo(name = "principal_paise") val principalPaise: Long,
    @ColumnInfo(name = "interest_paise") val interestPaise: Long,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "ledger_transaction_id") val ledgerTransactionId: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "paid_at_epoch_ms") val paidAtEpochMs: Long,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long
)
