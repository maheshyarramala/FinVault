package com.example.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.FinVaultDatabase
import com.example.core.database.migration.FinVaultMigrations
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.DebtInterestType
import com.example.core.database.entity.DebtType
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebtRepositoryTest {
    private lateinit var database: FinVaultDatabase
    private lateinit var engine: CentralLedgerEngine
    private lateinit var repository: DebtRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = CentralLedgerEngine(database)
        repository = DebtRepository(database, engine)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `lend and receive repayment keep principal as receivable and interest as income`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "debt_repo_bank",
                name = "Main Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 100000L
            )
        )
        val debt = repository.createDebt(
            title = "Personal loan to Ravi",
            personName = "Ravi",
            type = DebtType.LENT,
            principalPaise = 30000L,
            accountId = bank.id,
            interestType = DebtInterestType.SIMPLE,
            annualInterestRateBps = 1200,
            startEpochMs = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L,
            idempotencyKey = "ravi-loan-001"
        )
        assertEquals(70000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        val repayment = repository.recordRepayment(
            debtId = debt.id,
            principalPaise = 10000L,
            interestPaise = 500L,
            paymentAccountId = bank.id,
            idempotencyKey = "ravi-repayment-001"
        )
        assertEquals(80500L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(20000L, repository.getDebt(debt.id)!!.outstandingPrincipalPaise)
        assertEquals(10500L, repayment.totalPaise)
        assertEquals(500L, database.ledgerDao().observeIncomeSumPaise(0L, Long.MAX_VALUE).first())
        assertEquals(0L, database.ledgerDao().observeExpenseSumPaise(0L, Long.MAX_VALUE).first())

        val entries = database.ledgerDao().getEntriesForTransaction(repayment.ledgerTransactionId)
        assertEquals(10000L, entries.single { it.ledgerClass.name == "ASSET" && it.entrySide.name == "CREDIT" }.amountPaise)
        assertEquals(500L, entries.single { it.ledgerClass.name == "INCOME" }.amountPaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `borrow and repay separate principal liability from interest expense`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "debt_borrow_bank",
                name = "Savings",
                accountType = AccountType.BANK,
                openingBalancePaise = 50000L
            )
        )
        val debt = repository.createDebt(
            title = "Borrowed from family",
            personName = "Family",
            type = DebtType.BORROWED,
            principalPaise = 20000L,
            accountId = bank.id,
            interestType = DebtInterestType.FIXED,
            fixedInterestPaise = 2400L,
            startEpochMs = System.currentTimeMillis() - 365L * 24L * 60L * 60L * 1000L,
            idempotencyKey = "family-borrow-001"
        )
        assertEquals(70000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        repository.recordRepayment(
            debtId = debt.id,
            principalPaise = 20000L,
            interestPaise = 2400L,
            paymentAccountId = bank.id,
            idempotencyKey = "family-pay-001"
        )
        assertEquals(47600L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(0L, repository.getDebt(debt.id)!!.outstandingPrincipalPaise)
        assertEquals("SETTLED", repository.getDebt(debt.id)!!.status.name)
        assertEquals(2400L, database.ledgerDao().observeExpenseSumPaise(0L, Long.MAX_VALUE).first())
        assertEquals(2400L, database.ledgerDao().observePersonalDirectExpenseSumPaise(0L, Long.MAX_VALUE).first())
        assertEquals(0L, database.ledgerDao().observeIncomeSumPaise(0L, Long.MAX_VALUE).first())
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `fixed interest is not charged again after a partial repayment`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "debt_fixed_partial_bank",
                name = "Fixed Interest Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 50000L
            )
        )
        val debt = repository.createDebt(
            title = "One-time fixed charge",
            personName = "Family",
            type = DebtType.BORROWED,
            principalPaise = 10000L,
            accountId = bank.id,
            interestType = DebtInterestType.FIXED,
            fixedInterestPaise = 2400L,
            idempotencyKey = "fixed-partial-create"
        )

        repository.recordRepayment(
            debtId = debt.id,
            principalPaise = 2000L,
            interestPaise = 1000L,
            paymentAccountId = bank.id,
            idempotencyKey = "fixed-partial-first-payment"
        )

        assertEquals(1400L, repository.accruedInterestPaise(debt.id))
        repository.recordRepayment(
            debtId = debt.id,
            principalPaise = 8000L,
            interestPaise = 1400L,
            paymentAccountId = bank.id,
            idempotencyKey = "fixed-partial-final-payment"
        )
        assertEquals("SETTLED", repository.getDebt(debt.id)!!.status.name)
        assertEquals(37600L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `archive reverses all debt movements but retains archived audit record`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "debt_archive_bank",
                name = "Archive Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 100000L
            )
        )
        val debt = repository.createDebt(
            title = "Archive test",
            personName = "Person",
            type = DebtType.LENT,
            principalPaise = 30000L,
            accountId = bank.id,
            idempotencyKey = "archive-debt-001"
        )
        repository.recordRepayment(
            debtId = debt.id,
            principalPaise = 10000L,
            interestPaise = 0L,
            paymentAccountId = bank.id,
            idempotencyKey = "archive-repayment-001"
        )
        assertEquals(80000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        repository.archiveDebt(debt.id)

        assertEquals(100000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals("ARCHIVED", repository.getDebt(debt.id)!!.status.name)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `editing latest repayment replaces ledger and restores correct outstanding balance`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_edit_bank", name = "Edit Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Edit test", personName = "Ravi", type = DebtType.LENT,
            principalPaise = 30000L, accountId = bank.id, idempotencyKey = "edit-debt-create"
        )
        val repayment = repository.recordRepayment(
            debtId = debt.id, principalPaise = 10000L, interestPaise = 0L,
            paymentAccountId = bank.id, idempotencyKey = "edit-repay"
        )
        val edited = repository.editLatestRepayment(
            repaymentId = repayment.id, principalPaise = 5000L, interestPaise = 0L, paymentAccountId = bank.id
        )
        assertEquals(5000L, edited.totalPaise)
        assertEquals(25000L, repository.getDebt(debt.id)!!.outstandingPrincipalPaise)
        assertEquals(75000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(database.ledgerDao().getTransactionById(repayment.ledgerTransactionId)!!.isVoided)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `deleting latest repayment reverses cash movement and restores principal`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_delete_bank", name = "Delete Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Delete test", personName = "Ravi", type = DebtType.LENT,
            principalPaise = 30000L, accountId = bank.id, idempotencyKey = "delete-debt-create"
        )
        val repayment = repository.recordRepayment(
            debtId = debt.id, principalPaise = 10000L, interestPaise = 0L,
            paymentAccountId = bank.id, idempotencyKey = "delete-repay"
        )
        repository.deleteLatestRepayment(repayment.id)
        assertEquals(30000L, repository.getDebt(debt.id)!!.outstandingPrincipalPaise)
        assertEquals(70000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(database.debtDao().getRepaymentById(repayment.id) != null)
        assertEquals(true, database.ledgerDao().getTransactionById(repayment.ledgerTransactionId)!!.isVoided)
        assertTrue(database.debtDao().getRepayments(debt.id).isEmpty())
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `editing earlier repayment is rejected and leaves ledger unchanged`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_order_bank", name = "Order Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Order test", personName = "Ravi", type = DebtType.LENT,
            principalPaise = 30000L, accountId = bank.id, idempotencyKey = "order-debt-create"
        )
        val first = repository.recordRepayment(
            debtId = debt.id, principalPaise = 5000L, interestPaise = 0L,
            paymentAccountId = bank.id, paidAtEpochMs = 1_800_000_000_000L, idempotencyKey = "order-repay-1"
        )
        repository.recordRepayment(
            debtId = debt.id, principalPaise = 5000L, interestPaise = 0L,
            paymentAccountId = bank.id, paidAtEpochMs = 1_800_000_100_000L, idempotencyKey = "order-repay-2"
        )
        val before = database.accountDao().getAccountById(bank.id)!!.currentBalancePaise
        val failure = runCatching {
            repository.editLatestRepayment(first.id, 4000L, 0L, bank.id)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(before, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `archive after principal and repayment edits reverses entire audit chain`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_archive_edit_bank", name = "Archive Edit Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Archive edited debt", personName = "Ravi", type = DebtType.LENT,
            principalPaise = 30000L, accountId = bank.id, idempotencyKey = "archive-edit-create"
        )
        val repayment = repository.recordRepayment(
            debtId = debt.id, principalPaise = 10000L, interestPaise = 0L,
            paymentAccountId = bank.id, idempotencyKey = "archive-edit-repay"
        )
        repository.updateDebtDetails(
            debtId = debt.id, title = "Edited debt", personName = "Ravi",
            dueEpochMs = null, notes = null, principalPaise = 40000L
        )
        repository.editLatestRepayment(
            repaymentId = repayment.id, principalPaise = 5000L, interestPaise = 0L, paymentAccountId = bank.id
        )
        assertEquals(65000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        repository.archiveDebt(debt.id)

        assertEquals(100000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals("ARCHIVED", repository.getDebt(debt.id)!!.status.name)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `editing debt principal replaces initial ledger movement and preserves repayments`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_principal_edit_bank", name = "Principal Edit Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Principal edit", personName = "Ravi", type = DebtType.LENT,
            principalPaise = 30000L, accountId = bank.id, idempotencyKey = "principal-edit-create"
        )
        repository.recordRepayment(
            debtId = debt.id, principalPaise = 5000L, interestPaise = 0L,
            paymentAccountId = bank.id, idempotencyKey = "principal-edit-repay"
        )
        repository.updateDebtDetails(
            debtId = debt.id,
            title = "Updated principal",
            personName = "Ravi",
            dueEpochMs = System.currentTimeMillis() + 86_400_000L,
            notes = "Updated terms",
            principalPaise = 40000L
        )
        val updated = repository.getDebt(debt.id)!!
        assertEquals(40000L, updated.principalPaise)
        assertEquals(35000L, updated.outstandingPrincipalPaise)
        assertEquals("Updated principal", updated.title)
        assertEquals(65000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(1, database.debtDao().getRepayments(debt.id).size)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `failed repayment edit rolls back without voiding original transaction`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_rollback_bank", name = "Rollback Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Rollback test", personName = "Ravi", type = DebtType.LENT,
            principalPaise = 30000L, accountId = bank.id, idempotencyKey = "rollback-debt-create"
        )
        val repayment = repository.recordRepayment(
            debtId = debt.id, principalPaise = 5000L, interestPaise = 0L,
            paymentAccountId = bank.id, idempotencyKey = "rollback-repay"
        )
        val balanceBefore = database.accountDao().getAccountById(bank.id)!!.currentBalancePaise
        val debtBefore = repository.getDebt(debt.id)!!
        val failure = runCatching {
            repository.editLatestRepayment(repayment.id, 4000L, 0L, "missing-account")
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(balanceBefore, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(debtBefore.outstandingPrincipalPaise, repository.getDebt(debt.id)!!.outstandingPrincipalPaise)
        assertEquals(false, database.ledgerDao().getTransactionById(repayment.ledgerTransactionId)!!.isVoided)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `debt and repayments survive database close and reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "debt_restart_regression.db"
        context.deleteDatabase(dbName)
        database.close()
        database = Room.databaseBuilder(context, FinVaultDatabase::class.java, dbName)
            .addMigrations(*FinVaultMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        engine = CentralLedgerEngine(database)
        repository = DebtRepository(database, engine)
        val bank = engine.createAccount(
            CreateAccountSpec(id = "debt_restart_bank", name = "Restart Bank", accountType = AccountType.BANK, openingBalancePaise = 100000L)
        )
        val debt = repository.createDebt(
            title = "Persistent debt", personName = "Anil", type = DebtType.LENT,
            principalPaise = 25000L, accountId = bank.id, idempotencyKey = "restart-debt-create"
        )
        val repayment = repository.recordRepayment(
            debtId = debt.id, principalPaise = 5000L, interestPaise = 0L,
            paymentAccountId = bank.id, idempotencyKey = "restart-repay"
        )
        database.close()

        database = Room.databaseBuilder(context, FinVaultDatabase::class.java, dbName)
            .addMigrations(*FinVaultMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        engine = CentralLedgerEngine(database)
        repository = DebtRepository(database, engine)
        assertEquals(20000L, repository.getDebt(debt.id)!!.outstandingPrincipalPaise)
        assertEquals(repayment.id, database.debtDao().getRepayments(debt.id).single().id)
        assertEquals(80000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
        database.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `debt creation with same idempotency key does not duplicate debt or ledger movement`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "debt_idem_bank",
                name = "Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 100000L
            )
        )
        val first = repository.createDebt(
            title = "Idempotent debt",
            personName = "Person",
            type = DebtType.LENT,
            principalPaise = 10000L,
            accountId = bank.id,
            idempotencyKey = "same-create-key"
        )
        val second = repository.createDebt(
            title = "Idempotent debt",
            personName = "Person",
            type = DebtType.LENT,
            principalPaise = 10000L,
            accountId = bank.id,
            idempotencyKey = "same-create-key"
        )
        assertEquals(first.id, second.id)
        assertEquals(90000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(1, database.debtDao().observeDebts().first().size)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }
}
