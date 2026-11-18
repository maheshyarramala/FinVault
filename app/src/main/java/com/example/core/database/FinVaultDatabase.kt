package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.core.database.dao.AccountDao
import com.example.core.database.dao.BudgetDao
import com.example.core.database.dao.CategoryDao
import com.example.core.database.dao.CreditCardDao
import com.example.core.database.dao.LedgerDao
import com.example.core.database.dao.RecurringRuleDao
import com.example.core.database.dao.SplitBillDao
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.BudgetEntity
import com.example.core.database.entity.CardStatementEntity
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CreditCardEntity
import com.example.core.database.entity.LedgerEntryEntity
import com.example.core.database.entity.LedgerTransactionEntity
import com.example.core.database.entity.RecurringRuleEntity
import com.example.core.database.entity.SplitExpenseEntity
import com.example.core.database.entity.SplitExpenseShareEntity
import com.example.core.database.entity.SplitGroupEntity
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity
import com.example.core.database.migration.FinVaultMigrations

/**
 * Primary offline-first Room SQLite database for FinVault.
 * Never configure destructive migrations; every schema bump requires an explicit Migration(n, n+1).
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        LedgerTransactionEntity::class,
        LedgerEntryEntity::class,
        CreditCardEntity::class,
        CardStatementEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        SplitGroupEntity::class,
        SplitParticipantEntity::class,
        SplitExpenseEntity::class,
        SplitExpenseShareEntity::class,
        SplitSettlementEntity::class
    ],
    version = FinVaultDatabase.DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(FinVaultConverters::class)
abstract class FinVaultDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun splitBillDao(): SplitBillDao

    companion object {
        const val DATABASE_VERSION = 4
        const val DATABASE_NAME = "finvault_ledger.db"

        fun create(context: Context): FinVaultDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FinVaultDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*FinVaultMigrations.ALL_MIGRATIONS)
                .build()
    }
}
