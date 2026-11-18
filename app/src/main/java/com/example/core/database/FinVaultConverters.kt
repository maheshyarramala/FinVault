package com.example.core.database

import androidx.room.TypeConverter
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CardStatementStatus
import com.example.core.database.entity.CategoryType
import com.example.core.database.entity.EntrySide
import com.example.core.database.entity.LedgerAccountClass
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringFrequency
import com.example.core.database.entity.SettlementDirection
import com.example.core.database.entity.SplitMethod

class FinVaultConverters {
    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(raw: String): AccountType = AccountType.valueOf(raw)

    @TypeConverter
    fun fromCategoryType(type: CategoryType): String = type.name

    @TypeConverter
    fun toCategoryType(raw: String): CategoryType = CategoryType.valueOf(raw)

    @TypeConverter
    fun fromLedgerTransactionType(type: LedgerTransactionType): String = type.name

    @TypeConverter
    fun toLedgerTransactionType(raw: String): LedgerTransactionType =
        LedgerTransactionType.valueOf(raw)

    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod): String = method.name

    @TypeConverter
    fun toPaymentMethod(raw: String): PaymentMethod = PaymentMethod.valueOf(raw)

    @TypeConverter
    fun fromLedgerAccountClass(accountClass: LedgerAccountClass): String = accountClass.name

    @TypeConverter
    fun toLedgerAccountClass(raw: String): LedgerAccountClass = LedgerAccountClass.valueOf(raw)

    @TypeConverter
    fun fromEntrySide(side: EntrySide): String = side.name

    @TypeConverter
    fun toEntrySide(raw: String): EntrySide = EntrySide.valueOf(raw)

    @TypeConverter
    fun fromCardStatementStatus(status: CardStatementStatus): String = status.name

    @TypeConverter
    fun toCardStatementStatus(raw: String): CardStatementStatus = CardStatementStatus.valueOf(raw)

    @TypeConverter
    fun fromRecurringFrequency(frequency: RecurringFrequency): String = frequency.name

    @TypeConverter
    fun toRecurringFrequency(raw: String): RecurringFrequency = RecurringFrequency.valueOf(raw)

    @TypeConverter
    fun fromSplitMethod(method: SplitMethod): String = method.name

    @TypeConverter
    fun toSplitMethod(raw: String): SplitMethod = SplitMethod.valueOf(raw)

    @TypeConverter
    fun fromSettlementDirection(direction: SettlementDirection): String = direction.name

    @TypeConverter
    fun toSettlementDirection(raw: String): SettlementDirection = SettlementDirection.valueOf(raw)
}
