package com.example.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, non-destructive Room migrations for FinVaultDatabase.
 */
object FinVaultMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ledger_transactions` (
                    `id` TEXT NOT NULL,
                    `idempotency_key` TEXT NOT NULL,
                    `tx_type` TEXT NOT NULL,
                    `tx_timestamp_epoch_ms` INTEGER NOT NULL,
                    `total_amount_paise` INTEGER NOT NULL,
                    `category_id` TEXT,
                    `payment_method` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `notes` TEXT,
                    `source_module` TEXT NOT NULL,
                    `source_record_id` TEXT,
                    `reverses_transaction_id` TEXT,
                    `replaced_by_transaction_id` TEXT,
                    `is_voided` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`reverses_transaction_id`) REFERENCES `ledger_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`replaced_by_transaction_id`) REFERENCES `ledger_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_transactions_idempotency_key` " +
                    "ON `ledger_transactions` (`idempotency_key`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_tx_timestamp_epoch_ms` " +
                    "ON `ledger_transactions` (`tx_timestamp_epoch_ms`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_tx_type` " +
                    "ON `ledger_transactions` (`tx_type`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_category_id` " +
                    "ON `ledger_transactions` (`category_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_reverses_transaction_id` " +
                    "ON `ledger_transactions` (`reverses_transaction_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_replaced_by_transaction_id` " +
                    "ON `ledger_transactions` (`replaced_by_transaction_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_transactions_is_voided` " +
                    "ON `ledger_transactions` (`is_voided`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ledger_entries` (
                    `id` TEXT NOT NULL,
                    `transaction_id` TEXT NOT NULL,
                    `account_id` TEXT,
                    `category_id` TEXT,
                    `ledger_class` TEXT NOT NULL,
                    `entry_side` TEXT NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `entry_order` INTEGER NOT NULL,
                    `memo` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`transaction_id`) REFERENCES `ledger_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_transaction_id` " +
                    "ON `ledger_entries` (`transaction_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_account_id` " +
                    "ON `ledger_entries` (`account_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_category_id` " +
                    "ON `ledger_entries` (`category_id`)"
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `credit_cards` (
                    `account_id` TEXT NOT NULL,
                    `credit_limit_paise` INTEGER NOT NULL,
                    `billing_cycle_day` INTEGER NOT NULL,
                    `statement_day` INTEGER NOT NULL,
                    `due_day` INTEGER NOT NULL,
                    `reminder_enabled` INTEGER NOT NULL,
                    `network` TEXT,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`account_id`),
                    FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_statements` (
                    `id` TEXT NOT NULL,
                    `card_account_id` TEXT NOT NULL,
                    `period_start_epoch_ms` INTEGER NOT NULL,
                    `period_end_epoch_ms` INTEGER NOT NULL,
                    `due_date_epoch_ms` INTEGER NOT NULL,
                    `statement_balance_paise` INTEGER NOT NULL,
                    `paid_amount_paise` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`card_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_card_statements_card_account_id` " +
                    "ON `card_statements` (`card_account_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_card_statements_due_date_epoch_ms` " +
                    "ON `card_statements` (`due_date_epoch_ms`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `budgets` (
                    `id` TEXT NOT NULL,
                    `category_id` TEXT,
                    `month_key` TEXT NOT NULL,
                    `limit_paise` INTEGER NOT NULL,
                    `alert_threshold_pct` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_budgets_category_id` " +
                    "ON `budgets` (`category_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_budgets_month_key` " +
                    "ON `budgets` (`month_key`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `recurring_rules` (
                    `id` TEXT NOT NULL,
                    `tx_type` TEXT NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `account_id` TEXT NOT NULL,
                    `category_id` TEXT,
                    `payment_method` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `notes` TEXT,
                    `frequency` TEXT NOT NULL,
                    `next_run_epoch_ms` INTEGER NOT NULL,
                    `is_active` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_account_id` " +
                    "ON `recurring_rules` (`account_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_category_id` " +
                    "ON `recurring_rules` (`category_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_rules_next_run_epoch_ms` " +
                    "ON `recurring_rules` (`next_run_epoch_ms`)"
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `split_groups` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `icon_key` TEXT NOT NULL,
                    `description` TEXT,
                    `is_archived` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    `last_activity_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_groups_is_archived` " +
                    "ON `split_groups` (`is_archived`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_groups_last_activity_epoch_ms` " +
                    "ON `split_groups` (`last_activity_epoch_ms`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `split_participants` (
                    `id` TEXT NOT NULL,
                    `group_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `phone` TEXT,
                    `email` TEXT,
                    `notes` TEXT,
                    `is_current_user` INTEGER NOT NULL,
                    `is_archived` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`group_id`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_participants_group_id` " +
                    "ON `split_participants` (`group_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_participants_is_current_user` " +
                    "ON `split_participants` (`is_current_user`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_participants_is_archived` " +
                    "ON `split_participants` (`is_archived`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `split_expenses` (
                    `id` TEXT NOT NULL,
                    `group_id` TEXT NOT NULL,
                    `idempotency_key` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `total_amount_paise` INTEGER NOT NULL,
                    `expense_date_epoch_ms` INTEGER NOT NULL,
                    `paid_by_participant_id` TEXT NOT NULL,
                    `paid_from_account_id` TEXT,
                    `ledger_transaction_id` TEXT,
                    `split_method` TEXT NOT NULL,
                    `notes` TEXT,
                    `is_deleted` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`group_id`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`paid_by_participant_id`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`paid_from_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`ledger_transaction_id`) REFERENCES `ledger_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_split_expenses_idempotency_key` " +
                    "ON `split_expenses` (`idempotency_key`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expenses_group_id` " +
                    "ON `split_expenses` (`group_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expenses_paid_by_participant_id` " +
                    "ON `split_expenses` (`paid_by_participant_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expenses_paid_from_account_id` " +
                    "ON `split_expenses` (`paid_from_account_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expenses_ledger_transaction_id` " +
                    "ON `split_expenses` (`ledger_transaction_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expenses_expense_date_epoch_ms` " +
                    "ON `split_expenses` (`expense_date_epoch_ms`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expenses_is_deleted` " +
                    "ON `split_expenses` (`is_deleted`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `split_expense_shares` (
                    `id` TEXT NOT NULL,
                    `expense_id` TEXT NOT NULL,
                    `participant_id` TEXT NOT NULL,
                    `share_amount_paise` INTEGER NOT NULL,
                    `percentage_bps` INTEGER,
                    `custom_units_scaled` INTEGER,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`expense_id`) REFERENCES `split_expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`participant_id`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_split_expense_shares_expense_id_participant_id` " +
                    "ON `split_expense_shares` (`expense_id`, `participant_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expense_shares_expense_id` " +
                    "ON `split_expense_shares` (`expense_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_expense_shares_participant_id` " +
                    "ON `split_expense_shares` (`participant_id`)"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `split_settlements` (
                    `id` TEXT NOT NULL,
                    `group_id` TEXT NOT NULL,
                    `idempotency_key` TEXT NOT NULL,
                    `from_participant_id` TEXT NOT NULL,
                    `to_participant_id` TEXT NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `settlement_date_epoch_ms` INTEGER NOT NULL,
                    `notes` TEXT,
                    `account_id` TEXT,
                    `ledger_transaction_id` TEXT,
                    `direction` TEXT NOT NULL,
                    `is_deleted` INTEGER NOT NULL,
                    `created_at_epoch_ms` INTEGER NOT NULL,
                    `updated_at_epoch_ms` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`group_id`) REFERENCES `split_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`from_participant_id`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`to_participant_id`) REFERENCES `split_participants`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`ledger_transaction_id`) REFERENCES `ledger_transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_split_settlements_idempotency_key` " +
                    "ON `split_settlements` (`idempotency_key`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_group_id` " +
                    "ON `split_settlements` (`group_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_from_participant_id` " +
                    "ON `split_settlements` (`from_participant_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_to_participant_id` " +
                    "ON `split_settlements` (`to_participant_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_account_id` " +
                    "ON `split_settlements` (`account_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_ledger_transaction_id` " +
                    "ON `split_settlements` (`ledger_transaction_id`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_settlement_date_epoch_ms` " +
                    "ON `split_settlements` (`settlement_date_epoch_ms`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_split_settlements_is_deleted` " +
                    "ON `split_settlements` (`is_deleted`)"
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
