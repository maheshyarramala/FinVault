package com.example.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.FinVaultDatabase
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

        val entries = database.ledgerDao().getEntriesForTransaction(repayment.ledgerTransactionId)
        assertEquals(20000L, entries.single { it.ledgerClass.name == "ASSET" && it.entrySide.name == "CREDIT" }.amountPaise)
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
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
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
