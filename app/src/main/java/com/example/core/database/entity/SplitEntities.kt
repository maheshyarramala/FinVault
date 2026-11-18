package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SplitMethod {
    EQUAL,
    UNEQUAL_AMOUNT,
    PERCENTAGE,
    CUSTOM_SHARE
}

enum class SettlementDirection {
    OUTGOING_PAYMENT,
    INCOMING_REIMBURSEMENT,
    INTERNAL
}

@Entity(
    tableName = "split_groups",
    indices = [
        Index(value = ["is_archived"]),
        Index(value = ["last_activity_epoch_ms"])
    ]
)
data class SplitGroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "icon_key")
    val iconKey: String = "group",
    val description: String? = null,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "last_activity_epoch_ms")
    val lastActivityEpochMs: Long
)

@Entity(
    tableName = "split_participants",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["is_current_user"]),
        Index(value = ["is_archived"])
    ]
)
data class SplitParticipantEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "is_current_user")
    val isCurrentUser: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "split_expenses",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SplitParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["paid_by_participant_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["paid_from_account_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LedgerTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["group_id"]),
        Index(value = ["paid_by_participant_id"]),
        Index(value = ["paid_from_account_id"]),
        Index(value = ["ledger_transaction_id"]),
        Index(value = ["expense_date_epoch_ms"]),
        Index(value = ["is_deleted"])
    ]
)
data class SplitExpenseEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    val description: String,
    @ColumnInfo(name = "total_amount_paise")
    val totalAmountPaise: Long,
    @ColumnInfo(name = "expense_date_epoch_ms")
    val expenseDateEpochMs: Long,
    @ColumnInfo(name = "paid_by_participant_id")
    val paidByParticipantId: String,
    @ColumnInfo(name = "paid_from_account_id")
    val paidFromAccountId: String? = null,
    @ColumnInfo(name = "ledger_transaction_id")
    val ledgerTransactionId: String? = null,
    @ColumnInfo(name = "split_method")
    val splitMethod: SplitMethod,
    val notes: String? = null,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "split_expense_shares",
    foreignKeys = [
        ForeignKey(
            entity = SplitExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SplitParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participant_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["expense_id", "participant_id"], unique = true),
        Index(value = ["expense_id"]),
        Index(value = ["participant_id"])
    ]
)
data class SplitExpenseShareEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "expense_id")
    val expenseId: String,
    @ColumnInfo(name = "participant_id")
    val participantId: String,
    @ColumnInfo(name = "share_amount_paise")
    val shareAmountPaise: Long,
    @ColumnInfo(name = "percentage_bps")
    val percentageBps: Int? = null,
    @ColumnInfo(name = "custom_units_scaled")
    val customUnitsScaled: Long? = null
)

@Entity(
    tableName = "split_settlements",
    foreignKeys = [
        ForeignKey(
            entity = SplitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SplitParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_participant_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SplitParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_participant_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LedgerTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["ledger_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["group_id"]),
        Index(value = ["from_participant_id"]),
        Index(value = ["to_participant_id"]),
        Index(value = ["account_id"]),
        Index(value = ["ledger_transaction_id"]),
        Index(value = ["settlement_date_epoch_ms"]),
        Index(value = ["is_deleted"])
    ]
)
data class SplitSettlementEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "group_id")
    val groupId: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "from_participant_id")
    val fromParticipantId: String,
    @ColumnInfo(name = "to_participant_id")
    val toParticipantId: String,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "settlement_date_epoch_ms")
    val settlementDateEpochMs: Long,
    val notes: String? = null,
    @ColumnInfo(name = "account_id")
    val accountId: String? = null,
    @ColumnInfo(name = "ledger_transaction_id")
    val ledgerTransactionId: String? = null,
    val direction: SettlementDirection,
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long
)
