package com.example.core.domain.ledger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.LedgerRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.migration.FinVaultMigrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CentralLedgerEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: FinVaultDatabase
    private lateinit var engine: CentralLedgerEngine
    private lateinit var ledgerRepository: LedgerRepository

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, FinVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CategoryRepository(database.categoryDao()).ensureDefaultCategoriesSeeded()
        engine = CentralLedgerEngine(database)
        ledgerRepository = LedgerRepository(database.ledgerDao(), engine)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `create expense updates account balance and maintains double entry integrity`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "bank_hdfc",
                name = "HDFC Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 100000L, // ₹1,000.00
                isDefault = true
            )
        )

        val result = engine.post(
            LedgerPostSpec.Expense(
                accountId = bank.id,
                amountPaise = 25075L, // ₹250.75
                categoryId = "cat_exp_food",
                paymentMethod = PaymentMethod.BANK,
                description = "Lunch",
                idempotencyKey = "exp_lunch_1"
            )
        )

        assertFalse(result.wasIdempotentReplay)
        val updatedBank = database.accountDao().getAccountById(bank.id)!!
        assertEquals(74925L, updatedBank.currentBalancePaise)

        val integrity = engine.verifyGlobalLedgerIntegrity()
        assertTrue(integrity.isFullyHealthy)
        assertEquals(125075L, integrity.totalDebitsPaise)
        assertEquals(125075L, integrity.totalCreditsPaise)
    }

    @Test
    fun `duplicate idempotency key prevents duplicate transaction posting`() = runTest {
        val cash = engine.createAccount(
            CreateAccountSpec(
                id = "cash_wallet",
                name = "Cash Wallet",
                accountType = AccountType.CASH,
                openingBalancePaise = 50000L
            )
        )

        val first = engine.post(
            LedgerPostSpec.Expense(
                accountId = cash.id,
                amountPaise = 10000L,
                categoryId = "cat_exp_transport",
                paymentMethod = PaymentMethod.CASH,
                description = "Auto fare",
                idempotencyKey = "idem_auto_001"
            )
        )
        val duplicate = engine.post(
            LedgerPostSpec.Expense(
                accountId = cash.id,
                amountPaise = 10000L,
                categoryId = "cat_exp_transport",
                paymentMethod = PaymentMethod.CASH,
                description = "Auto fare",
                idempotencyKey = "idem_auto_001"
            )
        )

        assertFalse(first.wasIdempotentReplay)
        assertTrue(duplicate.wasIdempotentReplay)
        assertEquals(first.transactionId, duplicate.transactionId)

        val updatedCash = database.accountDao().getAccountById(cash.id)!!
        assertEquals(40000L, updatedCash.currentBalancePaise)
        assertEquals(1, ledgerRepository.activeTransactions.first().count { it.transaction.txType == LedgerTransactionType.EXPENSE })
    }

    @Test
    fun `edit expense reverses original and posts replacement across accounts accurately`() = runTest {
        val bankA = engine.createAccount(
            CreateAccountSpec(
                id = "bank_a",
                name = "Bank A",
                accountType = AccountType.BANK,
                openingBalancePaise = 100000L
            )
        )
        val bankB = engine.createAccount(
            CreateAccountSpec(
                id = "bank_b",
                name = "Bank B",
                accountType = AccountType.BANK,
                openingBalancePaise = 50000L
            )
        )

        val original = engine.post(
            LedgerPostSpec.Expense(
                accountId = bankA.id,
                amountPaise = 20000L,
                categoryId = "cat_exp_groceries",
                description = "Groceries",
                idempotencyKey = "exp_groc_1"
            )
        )
        assertEquals(80000L, database.accountDao().getAccountById(bankA.id)!!.currentBalancePaise)

        val edited = engine.editTransaction(
            originalTransactionId = original.transactionId,
            updatedSpec = LedgerPostSpec.Expense(
                accountId = bankB.id,
                amountPaise = 35000L,
                categoryId = "cat_exp_groceries",
                description = "Groceries Updated",
                idempotencyKey = "exp_groc_1_edit"
            )
        )

        assertNotEquals(original.transactionId, edited.transactionId)
        assertEquals(100000L, database.accountDao().getAccountById(bankA.id)!!.currentBalancePaise)
        assertEquals(15000L, database.accountDao().getAccountById(bankB.id)!!.currentBalancePaise)

        val oldTx = database.ledgerDao().getTransactionById(original.transactionId)!!
        assertTrue(oldTx.isVoided)
        assertEquals(edited.transactionId, oldTx.replacedByTransactionId)

        val activeExpenses = ledgerRepository.activeTransactions.first()
            .filter { it.transaction.txType == LedgerTransactionType.EXPENSE }
        assertEquals(1, activeExpenses.size)
        assertEquals(35000L, activeExpenses.first().transaction.totalAmountPaise)

        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `delete expense creates reversal entry and restores account balance`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "bank_main",
                name = "Main Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 80000L
            )
        )
        val expense = engine.post(
            LedgerPostSpec.Expense(
                accountId = bank.id,
                amountPaise = 30000L,
                categoryId = "cat_exp_shopping",
                description = "Shoes",
                idempotencyKey = "exp_shoes_1"
            )
        )
        assertEquals(50000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        val reversal = engine.reverseOrDeleteTransaction(
            transactionId = expense.transactionId,
            idempotencyKey = "rev_shoes_1",
            reason = "Returned item"
        )
        assertFalse(reversal.wasIdempotentReplay)
        assertEquals(80000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(database.ledgerDao().getTransactionById(expense.transactionId)!!.isVoided)

        val activeExpenses = ledgerRepository.activeTransactions.first()
            .filter { it.transaction.txType == LedgerTransactionType.EXPENSE }
        assertEquals(0, activeExpenses.size)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `transfer moves exact paise atomically between two accounts`() = runTest {
        val checking = engine.createAccount(
            CreateAccountSpec(
                id = "bank_checking",
                name = "Checking",
                accountType = AccountType.BANK,
                openingBalancePaise = 200000L
            )
        )
        val savings = engine.createAccount(
            CreateAccountSpec(
                id = "bank_savings",
                name = "Emergency Savings",
                accountType = AccountType.SAVINGS,
                openingBalancePaise = 50000L
            )
        )

        engine.post(
            LedgerPostSpec.Transfer(
                fromAccountId = checking.id,
                toAccountId = savings.id,
                amountPaise = 75000L,
                idempotencyKey = "xfer_1"
            )
        )

        assertEquals(125000L, database.accountDao().getAccountById(checking.id)!!.currentBalancePaise)
        assertEquals(125000L, database.accountDao().getAccountById(savings.id)!!.currentBalancePaise)
        assertEquals(250000L, database.accountDao().observeTotalLiquidBalancePaise().first())
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `credit card expense and card payment update outstanding liability correctly`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "bank_pay",
                name = "Salary Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 500000L
            )
        )
        val card = engine.createAccount(
            CreateAccountSpec(
                id = "card_regalia",
                name = "HDFC Regalia",
                accountType = AccountType.CREDIT_CARD,
                openingBalancePaise = 0L
            )
        )

        engine.post(
            LedgerPostSpec.CreditCardExpense(
                cardAccountId = card.id,
                amountPaise = 120000L,
                categoryId = "cat_exp_food",
                description = "Dinner",
                idempotencyKey = "cc_exp_1"
            )
        )
        assertEquals(120000L, database.accountDao().getAccountById(card.id)!!.currentBalancePaise)

        engine.post(
            LedgerPostSpec.CreditCardPayment(
                fromAssetAccountId = bank.id,
                cardAccountId = card.id,
                amountPaise = 80000L,
                idempotencyKey = "cc_pay_1"
            )
        )

        assertEquals(420000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(40000L, database.accountDao().getAccountById(card.id)!!.currentBalancePaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `loan emi splits principal reduction and interest expense accurately`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "bank_emi",
                name = "SBI Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 300000L
            )
        )

        val result = engine.post(
            LedgerPostSpec.LoanEmiPayment(
                fromAccountId = bank.id,
                principalPaise = 145000L,
                interestPaise = 55000L,
                interestCategoryId = "cat_exp_emi",
                description = "Home Loan EMI #1",
                idempotencyKey = "emi_1"
            )
        )

        assertEquals(100000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        val entries = database.ledgerDao().getEntriesForTransaction(result.transactionId)
        assertEquals(3, entries.size)
        assertEquals(145000L, entries[0].amountPaise)
        assertEquals(55000L, entries[1].amountPaise)
        assertEquals(200000L, entries[2].amountPaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `debt lent and repayment updates account balances and reconciles`() = runTest {
        val bank = engine.createAccount(
            CreateAccountSpec(
                id = "bank_debt",
                name = "ICICI Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 100000L
            )
        )

        engine.post(
            LedgerPostSpec.DebtMovement(
                accountId = bank.id,
                amountPaise = 40000L,
                kind = DebtLedgerKind.LENT_OUT,
                description = "Lent to Rahul",
                idempotencyKey = "debt_lent_1"
            )
        )
        assertEquals(60000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        engine.post(
            LedgerPostSpec.DebtMovement(
                accountId = bank.id,
                amountPaise = 25000L,
                kind = DebtLedgerKind.REPAYMENT_RECEIVED,
                description = "Partial repayment from Rahul",
                idempotencyKey = "debt_repay_1"
            )
        )
        assertEquals(85000L, database.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `persisted file database retains ledger transactions and balances across reopen`() = runTest {
        val dbFile = tempFolder.newFile("persisted_finvault.db")
        val db1 = Room.databaseBuilder(context, FinVaultDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*FinVaultMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val engine1 = CentralLedgerEngine(db1)
        CategoryRepository(db1.categoryDao()).ensureDefaultCategoriesSeeded()

        val acc = engine1.createAccount(
            CreateAccountSpec(
                id = "acc_persist",
                name = "Axis Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 150000L
            )
        )
        engine1.post(
            LedgerPostSpec.Income(
                destinationAccountId = acc.id,
                amountPaise = 50000L,
                categoryId = "cat_inc_salary",
                description = "Bonus",
                idempotencyKey = "inc_bonus_1"
            )
        )
        db1.close()

        val db2 = Room.databaseBuilder(context, FinVaultDatabase::class.java, dbFile.absolutePath)
            .addMigrations(*FinVaultMigrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val engine2 = CentralLedgerEngine(db2)

        val reloaded = db2.accountDao().getAccountById(acc.id)!!
        assertEquals(200000L, reloaded.currentBalancePaise)
        assertTrue(engine2.verifyGlobalLedgerIntegrity().isFullyHealthy)
        db2.close()
    }
}
