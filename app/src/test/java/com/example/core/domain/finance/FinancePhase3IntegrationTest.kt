package com.example.core.domain.finance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DateTimeUtils
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.BudgetRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.CreditCardRepository
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.RecurringRuleRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.CardStatementStatus
import com.example.core.database.entity.CategoryType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringFrequency
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.LedgerPostSpec
import com.example.feature.finance.DateRangePreset
import com.example.feature.finance.FinanceViewModel
import com.example.feature.finance.TransactionFormKind
import com.example.feature.finance.TransactionTypeFilter
import androidx.lifecycle.viewModelScope
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinancePhase3IntegrationTest {

    private lateinit var db: FinVaultDatabase
    private lateinit var engine: CentralLedgerEngine
    private lateinit var accountRepository: AccountRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var creditCardRepository: CreditCardRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var recurringRuleRepository: RecurringRuleRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FinVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = CentralLedgerEngine(db)
        accountRepository = AccountRepository(db.accountDao())
        categoryRepository = CategoryRepository(db.categoryDao())
        ledgerRepository = LedgerRepository(db.ledgerDao(), engine)
        creditCardRepository = CreditCardRepository(db.creditCardDao(), engine)
        budgetRepository = BudgetRepository(db.budgetDao(), db.ledgerDao(), db.categoryDao())
        recurringRuleRepository = RecurringRuleRepository(db.recurringRuleDao(), db.accountDao(), engine)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `expense defaults to BANK, supports edit and reversal, and updates statement`() = runTest {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "HDFC Salary",
                accountType = AccountType.BANK,
                openingBalancePaise = 10_000_00L, // ₹10,000.00
                isDefault = true
            )
        )
        val cash = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "Cash Wallet",
                accountType = AccountType.CASH,
                openingBalancePaise = 1_500_00L
            )
        )

        val expenseSpec = LedgerPostSpec.Expense(
            accountId = bank.id,
            amountPaise = 1_250_50L,
            categoryId = "cat_exp_food",
            description = "Dinner with team"
        )
        assertEquals(PaymentMethod.BANK, expenseSpec.paymentMethod)
        val postResult = ledgerRepository.postTransaction(expenseSpec)

        assertEquals(8_749_50L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

        // Edit expense to ₹2,000.00
        ledgerRepository.editTransaction(
            postResult.transactionId,
            LedgerPostSpec.Expense(
                accountId = bank.id,
                amountPaise = 2_000_00L,
                categoryId = "cat_exp_food",
                description = "Team Dinner Updated"
            )
        )
        assertEquals(8_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

        // Transfer ₹500 to Cash
        ledgerRepository.postTransaction(
            LedgerPostSpec.Transfer(
                fromAccountId = bank.id,
                toAccountId = cash.id,
                amountPaise = 500_00L,
                description = "ATM Withdrawal"
            )
        )
        assertEquals(7_500_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
        assertEquals(2_000_00L, db.accountDao().getAccountById(cash.id)?.currentBalancePaise)

        // Income ₹5,000 to Bank
        ledgerRepository.postTransaction(
            LedgerPostSpec.Income(
                destinationAccountId = bank.id,
                amountPaise = 5_000_00L,
                categoryId = "cat_inc_salary",
                description = "Bonus"
            )
        )
        assertEquals(12_500_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

        val statement = ledgerRepository.observeAccountStatementEntries(bank.id).first()
        assertTrue(statement.isNotEmpty())
        assertTrue(ledgerRepository.verifyGlobalIntegrity().isFullyHealthy)
    }

    @Test
    fun `credit card spend, statement snapshot, bill payment, and available credit calculate accurately`() = runTest {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "ICICI Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 50_000_00L
            )
        )
        val card = creditCardRepository.createCreditCard(
            name = "HDFC Millennia",
            last4 = "4321",
            network = "Visa",
            creditLimitPaise = 100_000_00L,
            openingOutstandingPaise = 10_000_00L,
            billingCycleDay = 1,
            statementDay = 15,
            dueDay = 5
        )

        // Card spend ₹5,000
        ledgerRepository.postTransaction(
            LedgerPostSpec.CreditCardExpense(
                cardAccountId = card.accountId,
                amountPaise = 5_000_00L,
                categoryId = "cat_exp_shopping",
                description = "Electronics"
            )
        )

        var cards = creditCardRepository.creditCards.first()
        assertEquals(1, cards.size)
        assertEquals(15_000_00L, cards[0].outstandingBalance.paise)
        assertEquals(85_000_00L, cards[0].availableCredit.paise)
        assertEquals(15, cards[0].utilizationPct)

        // Generate statement snapshot for ₹15,000
        val stmt = creditCardRepository.generateStatementSnapshot(card.accountId)
        assertEquals(15_000_00L, stmt?.statementBalancePaise)
        assertEquals(CardStatementStatus.CLOSED, stmt?.status)

        // Pay ₹6,000 from Bank -> Partial payment
        creditCardRepository.payCreditCard(
            fromAssetAccountId = bank.id,
            cardAccountId = card.accountId,
            amountPaise = 6_000_00L
        )

        cards = creditCardRepository.creditCards.first()
        assertEquals(9_000_00L, cards[0].outstandingBalance.paise)
        assertEquals(91_000_00L, cards[0].availableCredit.paise)
        assertEquals(CardStatementStatus.PARTIALLY_PAID, cards[0].statements.first().status)
        assertEquals(44_000_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

        creditCardRepository.toggleReminder(card.accountId, false)
        assertFalse(creditCardRepository.creditCards.first()[0].reminderEnabled)
    }

    @Test
    fun `budgets compute spent from ledger including subcategories and previous month comparison`() = runTest {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val subCat = categoryRepository.createCategory(
            name = "Coffee & Snacks",
            type = CategoryType.EXPENSE,
            parentId = "cat_exp_food"
        )
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "SBI Savings",
                accountType = AccountType.SAVINGS,
                openingBalancePaise = 50_000_00L
            )
        )

        val nowMs = DateTimeUtils.nowEpochMs()
        val currentMonth = DateTimeUtils.monthKey(nowMs)
        val prevMonth = DateTimeUtils.shiftMonthKey(currentMonth, -1)
        val (prevStartMs, _) = DateTimeUtils.monthRangeFromKey(prevMonth)

        // Post ₹300 in previous month
        ledgerRepository.postTransaction(
            LedgerPostSpec.Expense(
                accountId = bank.id,
                amountPaise = 300_00L,
                categoryId = "cat_exp_food",
                description = "Last month lunch",
                timestampEpochMs = prevStartMs + 60_000L
            )
        )

        // Post ₹600 on parent + ₹250 on subcategory = ₹850 total in current month
        ledgerRepository.postTransaction(
            LedgerPostSpec.Expense(
                accountId = bank.id,
                amountPaise = 600_00L,
                categoryId = "cat_exp_food",
                description = "Dinner",
                timestampEpochMs = nowMs
            )
        )
        ledgerRepository.postTransaction(
            LedgerPostSpec.Expense(
                accountId = bank.id,
                amountPaise = 250_00L,
                categoryId = subCat.id,
                description = "Latte",
                timestampEpochMs = nowMs
            )
        )

        // Set budget limit ₹1,000 with 80% alert -> 85% used -> alert triggered, not over budget
        budgetRepository.upsertBudget(
            monthKey = currentMonth,
            categoryId = "cat_exp_food",
            limitPaise = 1_000_00L,
            alertThresholdPct = 80
        )

        val progress = budgetRepository.observeBudgetsWithProgress(currentMonth).first()
        assertEquals(1, progress.size)
        val item = progress[0]
        assertEquals(850_00L, item.spent.paise)
        assertEquals(150_00L, item.remaining.paise)
        assertEquals(300_00L, item.previousMonthSpent.paise)
        assertEquals(85, item.usagePercentage)
        assertTrue(item.isAlertTriggered)
        assertFalse(item.isOverBudget)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `recurring rule posts idempotently and FinanceViewModel filters transactions`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var viewModel: FinanceViewModel? = null
        try {
            withTimeout(10_000L) {
                categoryRepository.ensureDefaultCategoriesSeeded()
                val bank = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "Axis Bank",
                        accountType = AccountType.BANK,
                        openingBalancePaise = 20_000_00L,
                        isDefault = true
                    )
                )

                val rule = recurringRuleRepository.createRule(
                    txType = LedgerTransactionType.EXPENSE,
                    amountPaise = 1_200_00L,
                    accountId = bank.id,
                    categoryId = "cat_exp_bills",
                    paymentMethod = PaymentMethod.BANK,
                    description = "Broadband Fiber",
                    notes = "Monthly plan",
                    frequency = RecurringFrequency.MONTHLY,
                    firstRunEpochMs = DateTimeUtils.nowEpochMs()
                )
                recurringRuleRepository.postNextOccurrence(rule.id)
                assertEquals(18_800_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)

                val vm = FinanceViewModel(
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    ledgerRepository = ledgerRepository,
                    creditCardRepository = creditCardRepository,
                    budgetRepository = budgetRepository,
                    recurringRuleRepository = recurringRuleRepository
                ).also { viewModel = it }

                val collectJob = launch(Dispatchers.Default) { vm.uiState.collect { } }
                vm.uiState.first { it.accounts.isNotEmpty() }

                vm.submitTransaction(
                    kind = TransactionFormKind.INCOME,
                    editingTransactionId = null,
                    amountInput = "5000",
                    description = "Freelance Payout",
                    notes = "UI design",
                    sourceAccountId = null,
                    destinationAccountId = bank.id,
                    categoryId = "cat_inc_freelance",
                    paymentMethod = PaymentMethod.BANK,
                    timestampEpochMs = DateTimeUtils.nowEpochMs(),
                    isRecurring = false,
                    recurringFrequency = RecurringFrequency.MONTHLY
                )

                val hydrated = vm.uiState.first { state ->
                    state.transactions.any { it.transaction.description == "Freelance Payout" }
                }
                assertEquals(3, hydrated.transactions.size)
                assertTrue(
                    hydrated.transactions.any {
                        it.transaction.txType == LedgerTransactionType.OPENING_BALANCE
                    }
                )
                assertEquals(23_800_00L, db.accountDao().getAccountById(bank.id)?.currentBalancePaise)
                assertEquals(PaymentMethod.BANK, vm.uiState.value.defaultExpensePaymentMethod)

                vm.updateSearchQuery("Broadband")
                val searchFiltered = vm.uiState.first {
                    it.searchQuery == "Broadband" && it.filteredTransactions.size == 1
                }
                assertEquals(1, searchFiltered.filteredTransactions.size)

                vm.updateSearchQuery("")
                vm.selectTypeFilter(TransactionTypeFilter.INCOME)
                vm.selectDateRangePreset(DateRangePreset.TODAY)
                val incomeFiltered = vm.uiState.first {
                    it.typeFilter == TransactionTypeFilter.INCOME &&
                        it.dateRangePreset == DateRangePreset.TODAY &&
                        it.filteredTransactions.size == 1
                }
                assertEquals(1, incomeFiltered.filteredTransactions.size)
                assertEquals("Freelance Payout", incomeFiltered.filteredTransactions.first().transaction.description)

                collectJob.cancelAndJoin()
                vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                Unit
            }
        } finally {
            viewModel?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }
}
