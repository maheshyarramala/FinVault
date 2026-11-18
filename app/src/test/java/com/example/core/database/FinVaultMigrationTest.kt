package com.example.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.database.migration.FinVaultMigrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinVaultMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FinVaultDatabase::class.java
    )

    @Test
    fun `migrate 1 to 2 preserves accounts and categories and creates ledger tables`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO accounts (
                    id, name, account_type, opening_balance_paise, current_balance_paise,
                    institution_name, account_last4, is_default, is_archived,
                    created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'acc_1', 'HDFC Bank', 'BANK', 100000, 100000,
                    'HDFC', '1234', 1, 0, 1000, 1000
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO categories (
                    id, name, type, parent_id, icon_key, color_hex, is_system, sort_order
                ) VALUES (
                    'cat_1', 'Food & Dining', 'EXPENSE', NULL, 'restaurant', 4293548556, 1, 1
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            FinVaultMigrations.MIGRATION_1_2
        )

        migratedDb.query("SELECT current_balance_paise FROM accounts WHERE id = 'acc_1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(100000L, cursor.getLong(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM ledger_transactions").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun `migrate 2 to 3 preserves ledger transactions and creates cards budgets and recurring tables`() {
        helper.createDatabase(TEST_DB_2_3, 2).apply {
            execSQL(
                """
                INSERT INTO accounts (
                    id, name, account_type, opening_balance_paise, current_balance_paise,
                    institution_name, account_last4, is_default, is_archived,
                    created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'acc_2', 'ICICI Bank', 'BANK', 500000, 450000,
                    'ICICI', '5678', 1, 0, 2000, 2000
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO categories (
                    id, name, type, parent_id, icon_key, color_hex, is_system, sort_order
                ) VALUES (
                    'cat_food', 'Food & Dining', 'EXPENSE', NULL, 'restaurant', 4293548556, 1, 1
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO ledger_transactions (
                    id, idempotency_key, tx_type, tx_timestamp_epoch_ms, total_amount_paise,
                    category_id, payment_method, description, notes, source_module,
                    source_record_id, reverses_transaction_id, replaced_by_transaction_id,
                    is_voided, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'tx_1', 'idem_1', 'EXPENSE', 3000, 50000,
                    'cat_food', 'BANK', 'Groceries', NULL, 'FINANCE',
                    NULL, NULL, NULL, 0, 3000, 3000
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_2_3,
            3,
            true,
            FinVaultMigrations.MIGRATION_2_3
        )

        migratedDb.query("SELECT total_amount_paise FROM ledger_transactions WHERE id = 'tx_1'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(50000L, cursor.getLong(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM credit_cards").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM card_statements").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM budgets").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM recurring_rules").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun `migrate 1 to 3 chained validates full schema`() {
        helper.createDatabase(TEST_DB_1_3, 1).apply {
            close()
        }
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_1_3,
            3,
            true,
            FinVaultMigrations.MIGRATION_1_2,
            FinVaultMigrations.MIGRATION_2_3
        )
        migratedDb.close()
    }

    @Test
    fun `migrate 3 to 4 preserves v3 data and creates all split bill tables`() {
        helper.createDatabase(TEST_DB_3_4, 3).apply {
            execSQL(
                """
                INSERT INTO accounts (
                    id, name, account_type, opening_balance_paise, current_balance_paise,
                    institution_name, account_last4, is_default, is_archived,
                    created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'acc_3', 'Axis Bank', 'BANK', 200000, 180000,
                    'Axis', '4321', 1, 0, 5000, 5000
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO ledger_transactions (
                    id, idempotency_key, tx_type, tx_timestamp_epoch_ms, total_amount_paise,
                    category_id, payment_method, description, notes, source_module,
                    source_record_id, reverses_transaction_id, replaced_by_transaction_id,
                    is_voided, created_at_epoch_ms, updated_at_epoch_ms
                ) VALUES (
                    'tx_v3', 'idem_v3', 'EXPENSE', 6000, 20000,
                    NULL, 'BANK', 'Train Ticket', NULL, 'FINANCE',
                    NULL, NULL, NULL, 0, 6000, 6000
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_3_4,
            4,
            true,
            FinVaultMigrations.MIGRATION_3_4
        )

        migratedDb.query("SELECT current_balance_paise FROM accounts WHERE id = 'acc_3'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(180000L, cursor.getLong(0))
        }
        migratedDb.query("SELECT total_amount_paise FROM ledger_transactions WHERE id = 'tx_v3'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(20000L, cursor.getLong(0))
        }
        listOf(
            "split_groups",
            "split_participants",
            "split_expenses",
            "split_expense_shares",
            "split_settlements"
        ).forEach { table ->
            migratedDb.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        migratedDb.close()
    }

    @Test
    fun `migrate 1 to 4 chained validates full schema`() {
        helper.createDatabase(TEST_DB_1_4, 1).apply {
            close()
        }
        val migratedDb = helper.runMigrationsAndValidate(
            TEST_DB_1_4,
            4,
            true,
            FinVaultMigrations.MIGRATION_1_2,
            FinVaultMigrations.MIGRATION_2_3,
            FinVaultMigrations.MIGRATION_3_4
        )
        migratedDb.close()
    }

    companion object {
        private const val TEST_DB = "finvault_migration_test.db"
        private const val TEST_DB_2_3 = "finvault_migration_2_3_test.db"
        private const val TEST_DB_1_3 = "finvault_migration_1_3_test.db"
        private const val TEST_DB_3_4 = "finvault_migration_3_4_test.db"
        private const val TEST_DB_1_4 = "finvault_migration_1_4_test.db"
    }
}
