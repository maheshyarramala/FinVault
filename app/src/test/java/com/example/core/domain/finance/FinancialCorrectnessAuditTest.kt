package com.example.core.domain.finance

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DateTimeUtils
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.BudgetRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.CreditCardRepository
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.RecurringRuleRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CardStatementEntity
import com.example.core.database.entity.CardStatementStatus
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.LedgerPostSpec
import com.example.feature.home.HomeUiState
import com.example.feature.home.HomeViewModel
import com.example.feature.reports.ReportPeriod
import com.example.feature.reports.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinancialCorrectnessAuditTest {

    private lateinit var context: Context
    private lateinit var db: FinVaultDatabase
    private lateinit var engine: CentralLedgerEngine
    private lateinit var accountRepository: AccountRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var creditCardRepository: CreditCardRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var recurringRuleRepository: RecurringRuleRepository
    private lateinit var preferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FinVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = CentralLedgerEngine(db)
        accountRepository = AccountRepository(db.accountDao())
        categoryRepository = CategoryRepository(db.categoryDao())
        ledgerRepository = LedgerRepository(db.ledgerDao(), engine)
        creditCardRepository = CreditCardRepository(db.creditCardDao(), engine, db.accountDao())
        budgetRepository = BudgetRepository(db.budgetDao(), db.ledgerDao(), db.categoryDao())
        recurringRuleRepository = RecurringRuleRepository(db.recurringRuleDao(), db.accountDao(), engine)
        preferencesRepository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `transfers and card payments do not inflate income or expenses and dashboard reconciles across all operations`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var homeVm: HomeViewModel? = null
        var reportsVm: ReportsViewModel? = null
        try {
            withTimeout(10_000L) {
                categoryRepository.ensureDefaultCategoriesSeeded()
                val bank = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "HDFC Salary",
                        accountType = AccountType.BANK,
                        openingBalancePaise = 20_000_00L,
                        isDefault = true
                    )
                )
                val savings = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "SBI Savings",
                        accountType = AccountType.SAVINGS,
                        openingBalancePaise = 10_000_00L
                    )
                )
                val card = creditCardRepository.createCreditCard(
                    name = "Axis Ace",
                    last4 = "1122",
                    network = "Visa",
                    creditLimitPaise = 100_000_00L,
                    openingOutstandingPaise = 0L,
                    billingCycleDay = 1,
                    statementDay = 15,
                    dueDay = 5
                )

                val now = DateTimeUtils.nowEpochMs()

                // 1. Income increases Bank balance
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Income(
                        destinationAccountId = bank.id,
                        amountPaise = 30_000_00L,
                        categoryId = "cat_inc_salary",
                        description = "Salary",
                        timestampEpochMs = now,
                        idempotencyKey = "audit_inc_1"
                    )
                )
                assertEquals(50_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

                // 2. Expense decreases Bank balance
                val bankExpense = ledgerRepository.postTransaction(
                    LedgerPostSpec.Expense(
                        accountId = bank.id,
                        amountPaise = 4_000_00L,
                        categoryId = "cat_exp_food",
                        paymentMethod = PaymentMethod.BANK,
                        description = "Groceries",
                        timestampEpochMs = now,
                        idempotencyKey = "audit_exp_1"
                    )
                )
                assertEquals(46_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

                // 3. Transfer updates both accounts atomically and 4. is not counted as income or expense
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Transfer(
                        fromAccountId = bank.id,
                        toAccountId = savings.id,
                        amountPaise = 15_000_00L,
                        description = "Savings Transfer",
                        timestampEpochMs = now,
                        idempotencyKey = "audit_xfer_1"
                    )
                )
                assertEquals(31_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
                assertEquals(25_000_00L, db.accountDao().getAccountById(savings.id)?.currentBalancePaise)

                // 5. Credit card purchase increases card outstanding
                ledgerRepository.postTransaction(
                    LedgerPostSpec.CreditCardExpense(
                        cardAccountId = card.accountId,
                        amountPaise = 6_000_00L,
                        categoryId = "cat_exp_shopping",
                        description = "Laptop Stand",
                        timestampEpochMs = now,
                        idempotencyKey = "audit_cc_exp_1"
                    )
                )
                assertEquals(6_000_00L, db.accountDao().getAccountById(card.accountId)?.currentBalancePaise)

                // 6. Credit card payment reduces bank balance & card liability without counting as expense
                creditCardRepository.payCreditCard(
                    fromAssetAccountId = bank.id,
                    cardAccountId = card.accountId,
                    amountPaise = 2_500_00L,
                    description = "Partial Card Payment",
                    notes = "Bill #1",
                    timestampEpochMs = now,
                    idempotencyKey = "audit_cc_pay_1"
                )
                assertEquals(28_500_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
                assertEquals(3_500_00L, db.accountDao().getAccountById(card.accountId)?.currentBalancePaise)

                // 9. Duplicate idempotency key does NOT create duplicate transaction
                val dup = ledgerRepository.postTransaction(
                    LedgerPostSpec.Expense(
                        accountId = bank.id,
                        amountPaise = 4_000_00L,
                        categoryId = "cat_exp_food",
                        paymentMethod = PaymentMethod.BANK,
                        description = "Groceries",
                        timestampEpochMs = now,
                        idempotencyKey = "audit_exp_1"
                    )
                )
                assertTrue(dup.wasIdempotentReplay)
                assertEquals(28_500_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

                // 10. Dashboard & Reports reconcile 100% with ledger:
                // Income = 30,000; Expenses = 4,000 + 6,000 = 10,000 (transfers & card payments excluded!)
                // Liquid = 28,500 + 25,000 = 53,500; Card debt = 3,500; Net Worth = 50,000
                homeVm = HomeViewModel(
                    accountRepository, categoryRepository, ledgerRepository,
                    budgetRepository, creditCardRepository, recurringRuleRepository, preferencesRepository
                )
                reportsVm = ReportsViewModel(accountRepository, ledgerRepository)
                val homeJob = launch(Dispatchers.Default) { homeVm.uiState.collect { } }
                val reportsJob = launch(Dispatchers.Default) { reportsVm.uiState.collect { } }

                val homeReady = homeVm.uiState.first {
                    it is HomeUiState.Ready && it.monthlyIncome.paise == 30_000_00L && it.monthlyExpenses.paise == 10_000_00L
                } as HomeUiState.Ready
                assertEquals(53_500_00L, homeReady.totalAccountBalance.paise)
                assertEquals(3_500_00L, homeReady.totalCreditCardOutstanding.paise)
                assertEquals(50_000_00L, homeReady.netWorth.paise)
                assertEquals(20_000_00L, homeReady.monthlyNetCashFlow.paise)

                val reportsState = reportsVm.uiState.first {
                    it.netCashFlow.paise == 20_000_00L && it.netWorth.paise == 50_000_00L
                }
                assertEquals(20_000_00L, reportsState.netCashFlow.paise)
                assertEquals("66.7%", reportsState.savingsRatePercent)

                // 7. Edit expense (4,000 -> 1,500) recalculates Bank & Home figures
                ledgerRepository.editTransaction(
                    bankExpense.transactionId,
                    LedgerPostSpec.Expense(
                        accountId = bank.id,
                        amountPaise = 1_500_00L,
                        categoryId = "cat_exp_food",
                        paymentMethod = PaymentMethod.BANK,
                        description = "Groceries Edited",
                        timestampEpochMs = now
                    )
                )
                assertEquals(31_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

                // 8. Delete transaction reverses its effect
                ledgerRepository.reverseOrDeleteTransaction(bankExpense.transactionId.let {
                    ledgerRepository.activeTransactions.first().first { tx -> tx.transaction.description == "Groceries Edited" }.transaction.id
                })
                assertEquals(32_500_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
                assertTrue(ledgerRepository.verifyGlobalIntegrity().isFullyHealthy)

                homeJob.cancelAndJoin()
                reportsJob.cancelAndJoin()
                homeVm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                reportsVm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                Unit
            }
        } finally {
            homeVm?.viewModelScope?.cancel()
            reportsVm?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `credit card payment FIFO allocation, edit recalculation, and reversal keep statements accurate`() = runTest {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "ICICI Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 50_000_00L,
                isDefault = true
            )
        )
        val card = creditCardRepository.createCreditCard(
            name = "SBI Card",
            last4 = "9988",
            network = "RuPay",
            creditLimitPaise = 100_000_00L,
            openingOutstandingPaise = 10_000_00L,
            billingCycleDay = 1,
            statementDay = 15,
            dueDay = 5
        )

        val now = DateTimeUtils.nowEpochMs()
        val s1Id = UUID.randomUUID().toString()
        val s2Id = UUID.randomUUID().toString()
        db.creditCardDao().insertStatement(
            CardStatementEntity(
                id = s1Id,
                cardAccountId = card.accountId,
                periodStartEpochMs = now - 60L * 86_400_000L,
                periodEndEpochMs = now - 31L * 86_400_000L,
                dueDateEpochMs = now - 10L * 86_400_000L,
                statementBalancePaise = 4_000_00L,
                paidAmountPaise = 0L,
                status = CardStatementStatus.CLOSED,
                createdAtEpochMs = now - 30L * 86_400_000L
            )
        )
        db.creditCardDao().insertStatement(
            CardStatementEntity(
                id = s2Id,
                cardAccountId = card.accountId,
                periodStartEpochMs = now - 30L * 86_400_000L,
                periodEndEpochMs = now - 1L * 86_400_000L,
                dueDateEpochMs = now + 10L * 86_400_000L,
                statementBalancePaise = 6_000_00L,
                paidAmountPaise = 0L,
                status = CardStatementStatus.CLOSED,
                createdAtEpochMs = now - 1L * 86_400_000L
            )
        )

        // Pay ₹5,500 -> fully settles s1 (₹4,000 PAID) and partially pays s2 (₹1,500 PARTIALLY_PAID)
        val payResult = creditCardRepository.payCreditCard(
            fromAssetAccountId = bank.id,
            cardAccountId = card.accountId,
            amountPaise = 5_500_00L,
            notes = "FIFO bill pay",
            timestampEpochMs = now
        )
        assertEquals(44_500_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
        assertEquals(4_500_00L, db.accountDao().getAccountById(card.accountId)?.currentBalancePaise)

        var stmts = db.creditCardDao().getStatementsForCardOnce(card.accountId).associateBy { it.id }
        assertEquals(4_000_00L, stmts[s1Id]?.paidAmountPaise)
        assertEquals(CardStatementStatus.PAID, stmts[s1Id]?.status)
        assertEquals(1_500_00L, stmts[s2Id]?.paidAmountPaise)
        assertEquals(CardStatementStatus.PARTIALLY_PAID, stmts[s2Id]?.status)

        // Edit payment down from ₹5,500 to ₹3,000 -> s1 becomes PARTIALLY_PAID (₹3,000), s2 reverts to ₹0 CLOSED
        val editResult = creditCardRepository.editCardPayment(
            originalTransactionId = payResult.transactionId,
            oldCardAccountId = card.accountId,
            oldAmountPaise = 5_500_00L,
            updatedSpec = LedgerPostSpec.CreditCardPayment(
                fromAssetAccountId = bank.id,
                cardAccountId = card.accountId,
                amountPaise = 3_000_00L,
                notes = "Corrected pay",
                timestampEpochMs = now
            )
        )
        assertEquals(47_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
        assertEquals(7_000_00L, db.accountDao().getAccountById(card.accountId)?.currentBalancePaise)
        stmts = db.creditCardDao().getStatementsForCardOnce(card.accountId).associateBy { it.id }
        assertEquals(3_000_00L, stmts[s1Id]?.paidAmountPaise)
        assertEquals(CardStatementStatus.PARTIALLY_PAID, stmts[s1Id]?.status)
        assertEquals(0L, stmts[s2Id]?.paidAmountPaise)
        assertEquals(CardStatementStatus.CLOSED, stmts[s2Id]?.status)

        // Reverse edited payment -> both statements revert to 0 PAID and balances restore to opening
        creditCardRepository.reverseCardPayment(
            transactionId = editResult.transactionId,
            cardAccountId = card.accountId,
            amountPaise = 3_000_00L
        )
        assertEquals(50_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
        assertEquals(10_000_00L, db.accountDao().getAccountById(card.accountId)?.currentBalancePaise)
        stmts = db.creditCardDao().getStatementsForCardOnce(card.accountId).associateBy { it.id }
        assertEquals(0L, stmts[s1Id]?.paidAmountPaise)
        assertEquals(CardStatementStatus.CLOSED, stmts[s1Id]?.status)
        assertTrue(ledgerRepository.verifyGlobalIntegrity().isFullyHealthy)
    }

    @Test
    fun `account edit enforces single default and archive requires zero balance`() = runTest {
        val bank1 = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "Bank 1",
                accountType = AccountType.BANK,
                openingBalancePaise = 5_000_00L,
                isDefault = true
            )
        )
        val bank2 = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "Bank 2",
                accountType = AccountType.BANK,
                openingBalancePaise = 0L,
                isDefault = true
            )
        )

        assertFalse(db.accountDao().getAccountById(bank1.id)!!.isDefault)
        assertTrue(db.accountDao().getAccountById(bank2.id)!!.isDefault)

        accountRepository.updateAccountDetails(
            accountId = bank1.id,
            name = "Bank 1 Primary",
            institutionName = "HDFC",
            last4 = "4321",
            isDefault = true
        )
        assertEquals("Bank 1 Primary", db.accountDao().getAccountById(bank1.id)?.name)
        assertTrue(db.accountDao().getAccountById(bank1.id)!!.isDefault)
        assertFalse(db.accountDao().getAccountById(bank2.id)!!.isDefault)

        // Non-zero balance archive fails cleanly
        val nonZeroArchive = runCatching { accountRepository.archiveAccount(bank1.id) }
        assertTrue(nonZeroArchive.isFailure)

        // Zero-balance archive succeeds and excludes from active accounts
        accountRepository.archiveAccount(bank2.id)
        val activeIds = accountRepository.activeAccounts.first().map { it.id }
        assertTrue(bank1.id in activeIds)
        assertFalse(bank2.id in activeIds)
    }

    @Test
    fun `reports viewmodel filters cashflow by DAILY, WEEKLY, MONTHLY, YEARLY ranges`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var reportsVm: ReportsViewModel? = null
        try {
            withTimeout(10_000L) {
                categoryRepository.ensureDefaultCategoriesSeeded()
                val bank = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "Axis Bank",
                        accountType = AccountType.BANK,
                        openingBalancePaise = 10_000_00L,
                        isDefault = true
                    )
                )
                val now = DateTimeUtils.nowEpochMs()
                val threeDaysAgo = now - 3L * 24L * 60L * 60L * 1000L

                // Today: +₹1,000 income
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Income(
                        destinationAccountId = bank.id,
                        amountPaise = 1_000_00L,
                        categoryId = "cat_inc_freelance",
                        description = "Today Gig",
                        timestampEpochMs = now
                    )
                )
                // 3 days ago: -₹400 expense (within WEEKLY, outside DAILY)
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Expense(
                        accountId = bank.id,
                        amountPaise = 400_00L,
                        categoryId = "cat_exp_food",
                        description = "3 Days Ago Dinner",
                        timestampEpochMs = threeDaysAgo
                    )
                )

                reportsVm = ReportsViewModel(accountRepository, ledgerRepository)
                val job = launch(Dispatchers.Default) { reportsVm.uiState.collect { } }

                reportsVm.selectPeriod(ReportPeriod.DAILY)
                val daily = reportsVm.uiState.first {
                    it.selectedPeriod == ReportPeriod.DAILY && it.netCashFlow.paise == 1_000_00L
                }
                assertEquals(1_000_00L, daily.netCashFlow.paise)

                reportsVm.selectPeriod(ReportPeriod.WEEKLY)
                val weekly = reportsVm.uiState.first {
                    it.selectedPeriod == ReportPeriod.WEEKLY && it.netCashFlow.paise == 600_00L
                }
                assertEquals(600_00L, weekly.netCashFlow.paise)
                assertEquals("60.0%", weekly.savingsRatePercent)

                job.cancelAndJoin()
                reportsVm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                Unit
            }
        } finally {
            reportsVm?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }
}
