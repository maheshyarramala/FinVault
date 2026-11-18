# FinVault — Persistent Development Status & Architecture Blueprint

**App Name:** FinVault  
**Package / Namespace:** `com.example` (`applicationId = "com.aistudio.finvault.xrktpm"`)  
**Target Market & Locale:** India (`en-IN`), INR (`₹`), 2 decimal places (`Long` integer paise storage)  
**Overall Progress:** Phase 5 of 10 Complete (50%)  
**Current Database Version:** `v4` (`FinVaultDatabase`, `exportSchema = true`, explicit `MIGRATION_1_2` + `MIGRATION_2_3` + `MIGRATION_3_4`)  

---

## 1. Completed Phases
- **Phase 1 — Project Architecture & Visual Foundation** (Completed & Verified)
  - Modular Clean Architecture + MVVM package layout (`core/` + `feature/`).
  - Manual constructor-injected `AppContainer` DI graph (`FinVaultApp`).
  - Fintech Design System (`Navy #0A192F`, `Electric Blue #1E6BFF`, `White #FFFFFF`, `AMOLED #000000`) with Light, Dark, AMOLED, and Dynamic Color theme modes persisted via DataStore.
  - Reusable Compose UI component library (`FinVaultHeroCard`, `FinVaultCard`, `FinVaultMetricTile`, `FinVaultEmptyStateCard`, `FinVaultSectionHeader`, `FinVaultStatusPill`, `FinVaultInfoRow`, `FinVaultAdaptiveScaffold`).
  - Bundled local Google Fonts (`Plus Jakarta Sans`, `Inter`, `JetBrains Mono`) via `font-util`.
  - Custom adaptive launcher icon (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `mipmap-*` PNGs).
  - Type-safe Navigation Compose (`@Serializable` destinations) with responsive `NavigationBar` (Compact) / `NavigationRail` (Medium/Expanded) across all 5 tabs: **Home**, **Finance** (Expenses & Accounts default), **Tools**, **Reports**, **Settings**.
  - Core `MoneyPaise` integer paise domain primitive with Indian numbering system formatter (`₹1,25,450.00`).
  - Room Database `v1` (`accounts`, `categories`) with idempotent default Indian category seeding.

- **Phase 2 — Central Financial Transaction Engine** (Completed & Verified)
  - Added Room `v2` double-entry entities (`ledger_transactions`, `ledger_entries`) with foreign keys, indexes, and explicit non-destructive `FinVaultMigrations.MIGRATION_1_2` (`1.json -> 2.json`).
  - Implemented `CentralLedgerEngine` (`Mutex` + `RoomDatabase.withTransaction`) supporting `OPENING_BALANCE`, `EXPENSE`, `CREDIT_CARD_EXPENSE`, `INCOME`, `TRANSFER`, `CREDIT_CARD_PAYMENT`, `SPLIT_EXPENSE`, `SPLIT_REIMBURSEMENT`, `DEBT_LENT`, `DEBT_BORROWED`, `DEBT_REPAYMENT`, `LOAN_EMI` (principal vs interest split), `LOAN_PREPAYMENT`, and `REVERSAL`.
  - Enforced unique `idempotency_key` deduplication, non-destructive deletion (`reverseOrDeleteTransaction`), atomic editing (`editTransaction` with reversal + replacement audit link `replaced_by_transaction_id`), per-account ledger balance reconciliation (`reconcileAccountBalance`), and global double-entry integrity verification (`verifyGlobalLedgerIntegrity`).

- **Phase 3 — Accounts, Expenses, Income, Transfers, Credit Cards & Budgets** (Completed & Verified)
  - Upgraded Room database to `v3` with 4 new entities (`credit_cards`, `card_statements`, `budgets`, `recurring_rules`), 3 new DAOs (`CreditCardDao`, `BudgetDao`, `RecurringRuleDao`), and explicit non-destructive `MIGRATION_2_3` (`2.json -> 3.json`).
  - Built repositories (`CreditCardRepository`, `BudgetRepository`, `RecurringRuleRepository`) and extended `LedgerRepository` and `CategoryRepository`.
  - Implemented full Phase 3 Finance UI (`FinanceViewModel`, `FinanceScreen`, `FinanceSections`, `TransactionFormSheet`, `TransactionDetailSheet`, `FinanceDialogs`):
    - Defaults to **Expenses & Accounts** alongside **Expenses**, **Accounts**, **Cards**, and **Budgets** tabs.
    - **Expenses & Income:** Default expense payment method set to `PaymentMethod.BANK`; supports Bank, Cash, Savings, Credit Card, custom categories/subcategories, DatePicker, notes, and optional recurring schedules (`Daily`, `Weekly`, `Monthly`, `Yearly`) with idempotent posting.
    - **Transactions & Audit:** Full `TransactionDetailSheet` inspecting double-entry legs, atomic editing, and non-destructive reversal/deletion.
    - **Search & Multi-Filter:** Live search across description/notes plus type, date preset (`Today`, `This Month`, `Last 30d`), account, and category filters.
    - **Accounts & Transfers:** Bank/Cash/Savings/Custom account creation with opening balance, inter-account transfers, and clickable `AccountStatementSheet`.
    - **Credit Cards:** Card creation with limit, billing cycle/statement/due days, payment reminder toggle, available credit & utilization % indicator, card spend, bank-to-card payment (`CREDIT_CARD_PAYMENT`), and billing statement snapshots (`card_statements`).
    - **Budgets:** Overall monthly and category spending limits (with subcategory rollup), previous-month spend comparison, configurable alert threshold (70%/80%/90%), and over-budget indicators computed directly from `LedgerDao`.

- **Phase 4 — Complete Premium Home Dashboard** (Completed & Verified)
  - Extended `LedgerDao` and `LedgerRepository` with reactive central-ledger aggregate queries for 6-month Income vs Expense cash flow (`observeMonthlyCashFlowTrend`), Debt receivables/payables (`observeDebtLedgerSummary`), Split Bill paid/reimbursed/net (`observeSplitBillLedgerSummary`), and Loan EMI principal/interest/monthly totals (`observeLoanEmiLedgerSummary`).
  - Built `HomeDashboardCharts.kt` using native Compose `Canvas` (`IncomeExpenseComparisonChartCard` 6-month grouped bar chart and `CategoryDonutChartCard` monthly expense breakdown donut chart with percentages).
  - Built `HomeDashboardSections.kt` (`MonthlyFinancialSummaryCard`, `SavingsAndAccountBreakdownCard`, `EmiAndCardRemindersCard` with one-tap recurring posting and card bill shortcuts, `DebtsSummaryCard`, `SplitBillSummaryCard`, and `CustomizeHomeWidgetsDialog`).
  - Added customizable Home widget visibility persistence (`HomeDashboardWidget`) in `UserPreferencesRepository` (DataStore).
  - Connected `HomeViewModel`, `HomeScreen`, and `FinVaultNavHost` so every figure on Home reconciles directly to the central Room ledger.

- **Phase 5 — Complete Split Bill Management** (Completed & Verified)
  - Upgraded Room database to `v4` with 5 new entities (`split_groups`, `split_participants`, `split_expenses`, `split_expense_shares`, `split_settlements`), `SplitBillDao`, and explicit non-destructive `MIGRATION_3_4` (`3.json -> 4.json`).
  - Built deterministic integer-paise `SplitCalculator` supporting `EQUAL` (remainder-safe), `UNEQUAL_AMOUNT`, `PERCENTAGE` (basis points `100.00% = 10_000`, Largest Remainder Hamilton allocation in `BigInteger`), `CUSTOM_SHARE` (fractional weights), and greedy minimum-transfer debt simplification (`computeSimplifiedSettlements`). Intentionally omits tax, tip, and discount options per specification.
  - Built `SplitBillRepository` integrated directly with `CentralLedgerEngine.withAtomicLedgerLock` inside single SQLite `withTransaction` blocks so Paid-by-Me group expenses atomically debit the selected Bank/Cash account (`categoryId = null` with `isSplitExpense = true` to protect personal spending category charts), Paid-by-Other expenses create zero Finance ledger entries, and settlements handle Outgoing (`SPLIT_EXPENSE`), Incoming (`SPLIT_REIMBURSEMENT` posted to `LedgerClass.ASSET` so reimbursements reduce receivables rather than inflating ordinary income), and Internal (`fromParticipantId` ↔ `toParticipantId` with zero Finance impact).
  - Implemented `SplitBillHubViewModel`, `SplitGroupDetailViewModel`, `SplitBillHubScreen` (Summary tiles, search, filters, archive/restore), `SplitGroupDetailScreen` (5 tabs: Overview, Expenses, People, Balances, Settlements; zero Edit buttons on Person cards), `SplitBillForms` (Add/Edit Group Expense & Record/Edit Settlement), and `SplitBillDialogs` (Create/Edit Group, Add/Edit Person, `SplitPersonDetailSheet` with full history and Edit/Safe Delete/Archive, `SplitExpenseDetailSheet`).
  - Reconciled Home Dashboard `SplitBillSummaryCard` and `ReportsViewModel` (`splitGrossPaidPaise`, `splitReimbursedPaise`, `splitNetOutOfPocketPaise`, `splitOwedToYouPaise`, `splitYouOwePaise`, `splitGroupTotalExpensesPaise`) across `SplitBillRepository` and `CentralLedgerEngine`, and wired `SplitBillHub` & `SplitBillGroupDetail` navigation routes from both Home and Tools.
  - Verified with `FinVaultMigrationTest` (`3->4` & chained `1->4`), `SplitCalculatorTest`, `SplitBillIntegrationTest`, and `SplitBillUiTest`.

---

## 2. Pending Phases
- **Phase 6:** Basic, Scientific & General Financial/Unit/Currency Calculators + Restorable History (`Migration(4, 5)`)
- **Phase 7:** Debt & Loan/EMI Amortization Engine
- **Phase 8:** Investment Calculators & Reports/Analytics Charts + PDF/CSV Exports
- **Phase 9:** Security, AES-256-GCM Encrypted Backup/Restore, PIN/Biometric Lock & Notifications (WorkManager)
- **Phase 10:** Optional Cloud Sync (Firebase Auth + Firestore), Google AdMob & Google Play Billing

---

## 3. Current Architecture
```
UI Layer (Jetpack Compose + Material 3 + Adaptive Navigation)
   │  observes StateFlow via collectAsStateWithLifecycle()
   ▼
Presentation Layer (HomeViewModel, FinanceViewModel, SplitBillHubViewModel, SplitGroupDetailViewModel, ToolsViewModel, ReportsViewModel, SettingsViewModel)
   │  reads live Flow aggregates & delegates writes
   ▼
Domain & Ledger Engine Layer (MoneyPaise, DateTimeUtils, CentralLedgerEngine, SplitCalculator)
   │  Mutex + atomic @Transaction double-entry posting, reversals & edits
   ▼
Data Layer (AccountRepository, CategoryRepository, LedgerRepository, CreditCardRepository, BudgetRepository, RecurringRuleRepository, SplitBillRepository, UserPreferencesRepository)
   │
   ▼
Persistence Layer (FinVaultDatabase SQLite v4 via Room + Preferences DataStore)
```

---

## 4. Database Schema Status (`v4`)
- `accounts`: `id`, `name`, `account_type`, `opening_balance_paise`, `current_balance_paise`, `institution_name`, `account_last4`, `is_default`, `is_archived`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- `categories`: `id`, `name`, `type`, `parent_id` (FK→`categories`), `icon_key`, `color_hex`, `is_system`, `sort_order`
- `ledger_transactions`: `id`, `idempotency_key` (UNIQUE), `tx_type`, `tx_timestamp_epoch_ms`, `total_amount_paise`, `category_id` (FK→`categories`), `payment_method`, `description`, `notes`, `source_module`, `source_record_id`, `reverses_transaction_id` (FK→`ledger_transactions`), `replaced_by_transaction_id` (FK→`ledger_transactions`), `is_voided`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- `ledger_entries`: `id`, `transaction_id` (FK→`ledger_transactions` CASCADE), `account_id` (FK→`accounts` RESTRICT), `category_id` (FK→`categories` SET NULL), `ledger_class`, `entry_side` (`DEBIT`/`CREDIT`), `amount_paise`, `entry_order`, `memo`
- `credit_cards`: `account_id` (PK + FK→`accounts` CASCADE), `credit_limit_paise`, `billing_cycle_day`, `statement_day`, `due_day`, `reminder_enabled`, `network`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- `card_statements`: `id`, `card_account_id` (FK→`accounts` CASCADE), `period_start_epoch_ms`, `period_end_epoch_ms`, `due_date_epoch_ms`, `statement_balance_paise`, `paid_amount_paise`, `status`, `created_at_epoch_ms`
- `budgets`: `id`, `category_id` (FK→`categories` CASCADE), `month_key`, `limit_paise`, `alert_threshold_pct`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- `recurring_rules`: `id`, `tx_type`, `amount_paise`, `account_id` (FK→`accounts` CASCADE), `category_id` (FK→`categories` SET NULL), `payment_method`, `description`, `notes`, `frequency`, `next_run_epoch_ms`, `is_active`, `created_at_epoch_ms`
- `split_groups`: `id`, `name`, `icon_key`, `description`, `is_archived`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- `split_participants`: `id`, `group_id` (FK→`split_groups` CASCADE), `name`, `phone`, `email`, `notes`, `is_current_user`, `is_archived`, `created_at_epoch_ms`
- `split_expenses`: `id`, `group_id` (FK→`split_groups` CASCADE), `description`, `total_amount_paise`, `expense_date_epoch_ms`, `paid_by_participant_id` (FK→`split_participants` RESTRICT), `paid_from_account_id` (FK→`accounts` RESTRICT), `split_method`, `ledger_transaction_id` (FK→`ledger_transactions` SET NULL), `notes`, `is_deleted`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- `split_expense_shares`: `id`, `expense_id` (FK→`split_expenses` CASCADE), `participant_id` (FK→`split_participants` RESTRICT), `share_amount_paise`, `percentage_bps`, `custom_units_scaled`
- `split_settlements`: `id`, `group_id` (FK→`split_groups` CASCADE), `from_participant_id` (FK→`split_participants` RESTRICT), `to_participant_id` (FK→`split_participants` RESTRICT), `amount_paise`, `settlement_date_epoch_ms`, `direction`, `account_id` (FK→`accounts` RESTRICT), `ledger_transaction_id` (FK→`ledger_transactions` SET NULL), `notes`, `is_deleted`, `created_at_epoch_ms`, `updated_at_epoch_ms`

---

## 5. Important Implementation Decisions
1. **Integer Paise Arithmetic (`Long`):** All monetary amounts are stored as integer paise (`1 INR = 100 paise`) wrapped by `MoneyPaise` utilities, preventing IEEE-754 floating-point drift.
2. **Double-Entry Sign Convention:** Asset accounts (`BANK`, `CASH`, `SAVINGS`, `CUSTOM`) carry normal DEBIT balances (`+DEBIT - CREDIT`). Credit Card accounts (`CREDIT_CARD`) carry normal CREDIT liability balances (`+CREDIT - DEBIT`, representing outstanding balance owed). Net Worth = `Sum(Assets) - Sum(Liabilities)`.
3. **Immutable Audit Trail on Edit & Delete:** Deleting voids the original transaction and posts an inverse `REVERSAL` transaction; editing inserts the replacement transaction first (satisfying FK constraints), then voids and reverses the original transaction with `replaced_by_transaction_id` linkage inside one atomic SQLite transaction.
4. **Ledger-Derived Budgets & Cards:** Budget progress (`BudgetRepository`) and credit card outstanding/available credit (`CreditCardRepository`) calculate directly from `LedgerDao` and `AccountDao`, never using standalone mutable balance caches.
5. **Non-Destructive Room Migrations Only:** `exportSchema = true` with `1.json`, `2.json`, `3.json`, and `4.json` exported and validated via `MigrationTestHelper`.
6. **Default Payment Method:** New expenses default to `PaymentMethod.BANK` and select the user's default Bank account when present.
7. **Split Bill Accounting & Ledger Isolation:** `SPLIT_REIMBURSEMENT` posts against `LedgerClass.ASSET` (crediting the split receivable rather than `LedgerClass.INCOME`) so receiving settlement repayments increases the destination Bank/Cash balance without inflating ordinary monthly Income or Reports totals. Split advances pass `categoryId = null` to keep personal spending category charts clean.

---

## 6. Phase 1–4 Quality Upgrade Audit Fixes
- **Single Default Account & Safe Archival:** `CentralLedgerEngine.createAccount` and `AccountDao.updateAccountMetadata` enforce a single default account; `AccountRepository.archiveAccount` blocks archiving accounts with non-zero balances.
- **Credit Card Statement & Payment Lifecycle:** Added FIFO multi-statement payment allocation, LIFO reversal on delete (`reverseCardPayment`), and statement recalculation on payment edit (`editCardPayment`), plus card metadata editing without mutating ledger-derived outstanding.
- **Submission Idempotency & System Entry Guards:** Added UI-layer double-submit lock (`isSubmitting`), form-scoped idempotency keys, stacked Category creation inside `TransactionFormSheet`, and read-only protection in `TransactionDetailSheet` for `OPENING_BALANCE`, `REVERSAL`, and specialized ledger types.
- **Live Reports Period Filtering & UI Consistency:** Connected `ReportsViewModel` (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`) to ledger inflow/outflow queries, fixed single-category 360° donut rendering, and added Edit/Archive/Clear-Filters flows across Finance sheets and cards.

---

## 7. Known Defects & Environment Limitations
- **No Emulator / ADB in Cloud Build Environment:** Visual layout rendering and physical device instrumented tests cannot be executed directly by the build agent; all database, migration, and Compose UI behaviors are verified via compilation and Robolectric JVM tests.
