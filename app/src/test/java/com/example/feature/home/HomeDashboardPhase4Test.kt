package com.example.feature.home

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.DateTimeUtils
import com.example.core.data.preferences.HomeDashboardWidget
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.BudgetRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.CreditCardRepository
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.RecurringRuleRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.RecurringFrequency
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.DebtLedgerKind
import com.example.core.domain.ledger.LedgerPostSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeDashboardPhase4Test {

    private lateinit var context: Context
    private lateinit var db: FinVaultDatabase
    private lateinit var engine: CentralLedgerEngine
    private lateinit var accountRepository: AccountRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var creditCardRepository: CreditCardRepository
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
        budgetRepository = BudgetRepository(db.budgetDao(), db.ledgerDao(), db.categoryDao())
        creditCardRepository = CreditCardRepository(db.creditCardDao(), engine)
        recurringRuleRepository = RecurringRuleRepository(db.recurringRuleDao(), db.accountDao(), engine)
        preferencesRepository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildViewModel(): HomeViewModel = HomeViewModel(
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        ledgerRepository = ledgerRepository,
        budgetRepository = budgetRepository,
        creditCardRepository = creditCardRepository,
        recurringRuleRepository = recurringRuleRepository,
        userPreferencesRepository = preferencesRepository
    )

    @Test
    fun `home dashboard balances, monthly cashflow, category chart, and budgets match central ledger`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var viewModel: HomeViewModel? = null
        try {
            withTimeout(10_000L) {
                categoryRepository.ensureDefaultCategoriesSeeded()
                val bank = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "HDFC Bank",
                        accountType = AccountType.BANK,
                        openingBalancePaise = 50_000_00L,
                        isDefault = true
                    )
                )
                ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "Cash Wallet",
                        accountType = AccountType.CASH,
                        openingBalancePaise = 5_000_00L
                    )
                )
                val savings = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "SBI Savings",
                        accountType = AccountType.SAVINGS,
                        openingBalancePaise = 25_000_00L
                    )
                )
                val card = creditCardRepository.createCreditCard(
                    name = "ICICI Amazon Pay",
                    last4 = "9012",
                    network = "Visa",
                    creditLimitPaise = 100_000_00L,
                    openingOutstandingPaise = 2_000_00L,
                    billingCycleDay = 1,
                    statementDay = 15,
                    dueDay = 5
                )

                val now = DateTimeUtils.nowEpochMs()
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Income(
                        destinationAccountId = bank.id,
                        amountPaise = 40_000_00L,
                        categoryId = "cat_inc_salary",
                        description = "Monthly Salary",
                        timestampEpochMs = now
                    )
                )
                val foodExpense = ledgerRepository.postTransaction(
                    LedgerPostSpec.Expense(
                        accountId = bank.id,
                        amountPaise = 1_500_00L,
                        categoryId = "cat_exp_food",
                        paymentMethod = PaymentMethod.BANK,
                        description = "Family Dinner",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.CreditCardExpense(
                        cardAccountId = card.accountId,
                        amountPaise = 3_000_00L,
                        categoryId = "cat_exp_bills",
                        description = "Electricity Bill",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Transfer(
                        fromAccountId = bank.id,
                        toAccountId = savings.id,
                        amountPaise = 2_000_00L,
                        description = "Monthly Savings Sweep",
                        timestampEpochMs = now
                    )
                )
                budgetRepository.upsertBudget(
                    monthKey = DateTimeUtils.monthKey(now),
                    categoryId = "cat_exp_food",
                    limitPaise = 5_000_00L,
                    alertThresholdPct = 80
                )

                val vm = buildViewModel().also { viewModel = it }
                val job = launch(Dispatchers.Default) { vm.uiState.collect { } }

                val ready = vm.uiState.first { state ->
                    state is HomeUiState.Ready &&
                        state.monthlyIncome.paise == 40_000_00L &&
                        state.monthlyExpenses.paise == 4_500_00L &&
                        state.budgetProgress.isNotEmpty()
                } as HomeUiState.Ready

                // Bank: 50k + 40k - 1.5k - 2k = 86.5k; Cash: 5k; Savings: 25k + 2k = 27k -> Total = 118.5k
                assertEquals(118_500_00L, ready.totalAccountBalance.paise)
                assertEquals(86_500_00L, ready.bankBalance.paise)
                assertEquals(5_000_00L, ready.cashBalance.paise)
                assertEquals(27_000_00L, ready.savingsBalance.paise)
                assertEquals(5_000_00L, ready.totalCreditCardOutstanding.paise)
                assertEquals(113_500_00L, ready.netWorth.paise)
                assertEquals(35_500_00L, ready.monthlyNetCashFlow.paise)
                assertEquals(5, ready.recentTransactions.size)
                assertEquals(6, ready.cashFlowTrend.size)
                assertEquals(40_000_00L, ready.cashFlowTrend.last().income.paise)
                assertEquals(4_500_00L, ready.cashFlowTrend.last().expense.paise)

                assertEquals(2, ready.categoryBreakdown.size)
                assertEquals("Bills & Recharge", ready.categoryBreakdown[0].name)
                assertEquals(3_000_00L, ready.categoryBreakdown[0].amount.paise)
                assertEquals("Food & Dining", ready.categoryBreakdown[1].name)
                assertEquals(1_500_00L, ready.categoryBreakdown[1].amount.paise)
                assertEquals(1_500_00L, ready.budgetProgress.first().spent.paise)
                assertEquals(30, ready.budgetProgress.first().usagePercentage)

                // Reversing the food expense updates Home balances, expenses, and category chart automatically
                ledgerRepository.reverseOrDeleteTransaction(foodExpense.transactionId)
                val afterReverse = vm.uiState.first { state ->
                    state is HomeUiState.Ready &&
                        state.monthlyExpenses.paise == 3_000_00L &&
                        state.totalAccountBalance.paise == 120_000_00L &&
                        state.categoryBreakdown.size == 1 &&
                        state.budgetProgress.firstOrNull()?.spent?.paise == 0L
                } as HomeUiState.Ready
                assertEquals(120_000_00L, afterReverse.totalAccountBalance.paise)
                assertEquals(1, afterReverse.categoryBreakdown.size)
                assertEquals(0L, afterReverse.budgetProgress.first().spent.paise)
                assertTrue(ledgerRepository.verifyGlobalIntegrity().isFullyHealthy)

                job.cancelAndJoin()
                vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                Unit
            }
        } finally {
            viewModel?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `home dashboard debts, split bills, loan EMIs, and reminders match central ledger`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var viewModel: HomeViewModel? = null
        try {
            withTimeout(10_000L) {
                categoryRepository.ensureDefaultCategoriesSeeded()
                val bank = ledgerRepository.createAccount(
                    CreateAccountSpec(
                        name = "Axis Bank",
                        accountType = AccountType.BANK,
                        openingBalancePaise = 100_000_00L,
                        isDefault = true
                    )
                )
                val now = DateTimeUtils.nowEpochMs()

                // Debt movements
                ledgerRepository.postTransaction(
                    LedgerPostSpec.DebtMovement(
                        accountId = bank.id,
                        amountPaise = 10_000_00L,
                        kind = DebtLedgerKind.LENT_OUT,
                        description = "Lent to Arjun",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.DebtMovement(
                        accountId = bank.id,
                        amountPaise = 3_500_00L,
                        kind = DebtLedgerKind.REPAYMENT_RECEIVED,
                        description = "Arjun partial payback",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.DebtMovement(
                        accountId = bank.id,
                        amountPaise = 4_000_00L,
                        kind = DebtLedgerKind.BORROWED_IN,
                        description = "Borrowed from Meera",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.DebtMovement(
                        accountId = bank.id,
                        amountPaise = 1_000_00L,
                        kind = DebtLedgerKind.REPAYMENT_PAID,
                        description = "Repaid Meera",
                        timestampEpochMs = now
                    )
                )

                // Split Bill movements
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Expense(
                        accountId = bank.id,
                        amountPaise = 2_400_00L,
                        categoryId = "cat_exp_food",
                        paymentMethod = PaymentMethod.BANK,
                        isSplitExpense = true,
                        description = "Goa Trip Dinner Split",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Income(
                        destinationAccountId = bank.id,
                        amountPaise = 1_600_00L,
                        categoryId = "cat_inc_other",
                        isSplitReimbursement = true,
                        description = "Trip reimbursement",
                        timestampEpochMs = now
                    )
                )

                // Loan EMI payment
                ledgerRepository.postTransaction(
                    LedgerPostSpec.LoanEmiPayment(
                        fromAccountId = bank.id,
                        principalPaise = 8_000_00L,
                        interestPaise = 2_000_00L,
                        interestCategoryId = "cat_exp_emi",
                        description = "Car Loan EMI #4",
                        timestampEpochMs = now
                    )
                )

                // Upcoming recurring EMI reminder
                val prevMonthStart = DateTimeUtils.monthRangeFromKey(
                    DateTimeUtils.shiftMonthKey(DateTimeUtils.monthKey(now), -1)
                ).first
                val rule = recurringRuleRepository.createRule(
                    txType = LedgerTransactionType.EXPENSE,
                    amountPaise = 12_000_00L,
                    accountId = bank.id,
                    categoryId = "cat_exp_emi",
                    paymentMethod = PaymentMethod.BANK,
                    description = "Home Loan EMI",
                    notes = "Auto reminder",
                    frequency = RecurringFrequency.MONTHLY,
                    firstRunEpochMs = prevMonthStart
                )

                val vm = buildViewModel().also { viewModel = it }
                val job = launch(Dispatchers.Default) { vm.uiState.collect { } }

                val ready = vm.uiState.first { state ->
                    state is HomeUiState.Ready &&
                        state.debtSummary.transactionCount == 4 &&
                        state.splitBillSummary.transactionCount == 2 &&
                        state.loanEmiSummary.paymentCount == 1 &&
                        state.upcomingReminders.isNotEmpty()
                } as HomeUiState.Ready

                assertEquals(6_500_00L, ready.debtSummary.owedToYou.paise)
                assertEquals(3_000_00L, ready.debtSummary.youOwe.paise)
                assertEquals(3_500_00L, ready.debtSummary.netDebtPosition.paise)

                assertEquals(2_400_00L, ready.splitBillSummary.totalSplitPaid.paise)
                assertEquals(1_600_00L, ready.splitBillSummary.totalReimbursed.paise)
                assertEquals(800_00L, ready.splitBillSummary.netOutflow.paise)

                assertEquals(8_000_00L, ready.loanEmiSummary.totalPrincipalPaid.paise)
                assertEquals(2_000_00L, ready.loanEmiSummary.totalInterestPaid.paise)
                assertEquals(10_000_00L, ready.loanEmiSummary.currentMonthPaid.paise)

                val reminder = ready.upcomingReminders.first() as UpcomingReminderItem.RecurringEmiOrBill
                assertTrue(reminder.isEmiOrLoan)
                assertEquals(rule.id, reminder.ruleId)

                // Post reminder directly from HomeViewModel
                val expensesBefore = ready.monthlyExpenses.paise
                vm.postRecurringReminder(rule.id)
                val afterPost = vm.uiState.first { state ->
                    state is HomeUiState.Ready &&
                        state.statusMessage == "Scheduled entry posted to ledger" &&
                        state.monthlyExpenses.paise == expensesBefore + 12_000_00L
                } as HomeUiState.Ready
                assertEquals(expensesBefore + 12_000_00L, afterPost.monthlyExpenses.paise)

                // Widget customization
                vm.toggleWidgetVisibility(HomeDashboardWidget.DEBTS_SUMMARY, false)
                val afterHide = vm.uiState.first { state ->
                    state is HomeUiState.Ready && !state.isWidgetVisible(HomeDashboardWidget.DEBTS_SUMMARY)
                } as HomeUiState.Ready
                assertFalse(afterHide.isWidgetVisible(HomeDashboardWidget.DEBTS_SUMMARY))

                vm.resetWidgetsToDefault()
                val afterReset = vm.uiState.first { state ->
                    state is HomeUiState.Ready && state.isWidgetVisible(HomeDashboardWidget.DEBTS_SUMMARY)
                } as HomeUiState.Ready
                assertTrue(afterReset.isWidgetVisible(HomeDashboardWidget.DEBTS_SUMMARY))

                job.cancelAndJoin()
                vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
                Unit
            }
        } finally {
            viewModel?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }
}
