package com.example.core.domain.split

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
import com.example.core.data.repository.SplitBillRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.LedgerTransactionType
import com.example.core.database.entity.SettlementDirection
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.CreateAccountSpec
import com.example.core.domain.ledger.LedgerPostSpec
import com.example.feature.home.HomeUiState
import com.example.feature.home.HomeViewModel
import com.example.feature.reports.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitBillIntegrationTest {

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
    private lateinit var splitBillRepository: SplitBillRepository

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
        splitBillRepository = SplitBillRepository(db, db.splitBillDao(), db.accountDao(), engine)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `paid by me deducts bank via central ledger while paid by friend creates zero ledger impact`() = runBlocking {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "HDFC Bank",
                accountType = AccountType.BANK,
                openingBalancePaise = 20_000_00L,
                isDefault = true
            )
        )

        val group = splitBillRepository.createGroup(
            name = "Goa Trip",
            initialMemberNames = listOf("Aarav", "Priya")
        )
        val detail0 = splitBillRepository.observeGroupDetail(group.id).first()!!
        val me = detail0.overview.participants.first { it.isCurrentUser }
        val aarav = detail0.overview.participants.first { it.name == "Aarav" }
        val priya = detail0.overview.participants.first { it.name == "Priya" }
        val now = DateTimeUtils.nowEpochMs()

        // 1. Paid by Me: ₹3,000 hotel split equally -> Bank drops from ₹20,000 to ₹17,000
        val hotelExp = splitBillRepository.addExpense(
            groupId = group.id,
            description = "Beach Villa",
            totalAmountPaise = 3_000_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = me.id,
            paidFromAccountId = bank.id,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, aarav.id, priya.id)),
            idempotencyKey = "exp_villa_1"
        )
        assertNotNull(hotelExp.ledgerTransactionId)
        assertEquals(17_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // Idempotent replay produces NO duplicate expense or second deduction
        val replay = splitBillRepository.addExpense(
            groupId = group.id,
            description = "Beach Villa",
            totalAmountPaise = 3_000_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = me.id,
            paidFromAccountId = bank.id,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, aarav.id, priya.id)),
            idempotencyKey = "exp_villa_1"
        )
        assertEquals(hotelExp.id, replay.id)
        assertEquals(17_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // 2. Paid by Another Participant (Aarav): ₹600 scooter split equally -> ZERO Bank or Ledger change
        val scooterExp = splitBillRepository.addExpense(
            groupId = group.id,
            description = "Scooter Rental",
            totalAmountPaise = 600_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = aarav.id,
            paidFromAccountId = null,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, aarav.id, priya.id)),
            idempotencyKey = "exp_scooter_1"
        )
        assertNull(scooterExp.ledgerTransactionId)
        assertEquals(17_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // Ledger has only OPENING_BALANCE + 1 SPLIT_EXPENSE (plus ZERO transactions from Aarav's expense)
        val txs = db.ledgerDao().observeActiveTransactionsWithEntries().first()
        val splitTxs = txs.filter { it.transaction.txType == LedgerTransactionType.SPLIT_EXPENSE }
        assertEquals(1, splitTxs.size)
        assertEquals(3_000_00L, splitTxs.first().transaction.totalAmountPaise)

        // Me net = ₹3,000 paid - (₹1,000 + ₹200 shares) = +₹1,800 owed to Me
        // Aarav net = ₹600 paid - (₹1,000 + ₹200 shares) = -₹600
        // Priya net = ₹0 paid - (₹1,000 + ₹200 shares) = -₹1,200
        val detail1 = splitBillRepository.observeGroupDetail(group.id).first()!!
        assertEquals(3_600_00L, detail1.overview.totalExpenses.paise)
        assertEquals(1_800_00L, detail1.overview.youAreOwed.paise)
        assertEquals(0L, detail1.overview.youOwe.paise)
        assertEquals(1_800_00L, detail1.overview.unsettledAmount.paise)

        val integrity = engine.verifyGlobalLedgerIntegrity()
        assertTrue(integrity.isFullyHealthy)
    }

    @Test
    fun `atomic failure rolls back both bank balance and split expense record`() = runBlocking {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(
                name = "SBI Main",
                accountType = AccountType.BANK,
                openingBalancePaise = 10_000_00L,
                isDefault = true
            )
        )
        val group = splitBillRepository.createGroup("Office Lunch", initialMemberNames = listOf("Kiran"))
        val detail = splitBillRepository.observeGroupDetail(group.id).first()!!
        val me = detail.overview.participants.first { it.isCurrentUser }
        val kiran = detail.overview.participants.first { !it.isCurrentUser }

        val failure = runCatching {
            splitBillRepository.addExpense(
                groupId = group.id,
                description = "Team Pizza",
                totalAmountPaise = 1_200_00L,
                expenseDateEpochMs = DateTimeUtils.nowEpochMs(),
                paidByParticipantId = me.id,
                paidFromAccountId = bank.id,
                splitSpec = SplitInputSpec.Equal(listOf(me.id, kiran.id)),
                testBeforeLedgerCommitHook = { throw IllegalStateException("Simulated atomic abort") }
            )
        }
        assertTrue(failure.isFailure)
        // NEITHER the Bank deduction NOR the SplitExpense committed
        assertEquals(10_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        assertEquals(0, db.splitBillDao().getActiveExpensesForGroupOnce(group.id).size)
        assertEquals(0, db.ledgerDao().observeActiveTransactionsWithEntries().first().count {
            it.transaction.txType == LedgerTransactionType.SPLIT_EXPENSE
        })
    }

    @Test
    fun `edit expense switches payer between me and friend and delete reverses ledger atomically`() = runBlocking {
        categoryRepository.ensureDefaultCategoriesSeeded()
        val bank = ledgerRepository.createAccount(
            CreateAccountSpec(name = "ICICI Bank", accountType = AccountType.BANK, openingBalancePaise = 15_000_00L, isDefault = true)
        )
        val group = splitBillRepository.createGroup("Roommates", initialMemberNames = listOf("Rohan"))
        val detail0 = splitBillRepository.observeGroupDetail(group.id).first()!!
        val me = detail0.overview.participants.first { it.isCurrentUser }
        val rohan = detail0.overview.participants.first { !it.isCurrentUser }
        val now = DateTimeUtils.nowEpochMs()

        // Create Paid by Me ₹2,000 -> Bank ₹13,000
        val exp = splitBillRepository.addExpense(
            groupId = group.id,
            description = "Electricity",
            totalAmountPaise = 2_000_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = me.id,
            paidFromAccountId = bank.id,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, rohan.id))
        )
        assertEquals(13_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // Edit amount to ₹2,400 (still Me) -> Bank becomes ₹12,600
        splitBillRepository.editExpense(
            expenseId = exp.id,
            description = "Electricity + Water",
            totalAmountPaise = 2_400_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = me.id,
            paidFromAccountId = bank.id,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, rohan.id))
        )
        assertEquals(12_600_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // Switch payer to Rohan -> previous ₹2,400 ledger tx reverses, Bank restored to ₹15,000!
        val switchedToFriend = splitBillRepository.editExpense(
            expenseId = exp.id,
            description = "Electricity + Water",
            totalAmountPaise = 2_400_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = rohan.id,
            paidFromAccountId = null,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, rohan.id))
        )
        assertNull(switchedToFriend.ledgerTransactionId)
        assertEquals(15_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // Switch back to Me for ₹1,000 -> Bank becomes ₹14,000
        splitBillRepository.editExpense(
            expenseId = exp.id,
            description = "Electricity Final",
            totalAmountPaise = 1_000_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = me.id,
            paidFromAccountId = bank.id,
            splitSpec = SplitInputSpec.Equal(listOf(me.id, rohan.id))
        )
        assertEquals(14_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

        // Delete expense -> Bank restored to ₹15,000 and balances zeroed
        splitBillRepository.deleteExpense(exp.id)
        assertEquals(15_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
        val afterDelete = splitBillRepository.observeGroupDetail(group.id).first()!!
        assertEquals(0L, afterDelete.overview.totalExpenses.paise)
        assertEquals(0L, afterDelete.overview.unsettledAmount.paise)
        assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)
    }

    @Test
    fun `settlements support partial, full, outgoing, incoming, internal, edit, reverse, safe delete, and never inflate income`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var homeViewModel: HomeViewModel? = null
        var reportsViewModel: ReportsViewModel? = null
        try {
            withTimeout(10_000L) {
                categoryRepository.ensureDefaultCategoriesSeeded()
                val bank = ledgerRepository.createAccount(
                    CreateAccountSpec(name = "HDFC Primary", accountType = AccountType.BANK, openingBalancePaise = 50_000_00L, isDefault = true)
                )
                val cash = ledgerRepository.createAccount(
                    CreateAccountSpec(name = "Cash Wallet", accountType = AccountType.CASH, openingBalancePaise = 5_000_00L)
                )
                val now = DateTimeUtils.nowEpochMs()

                // Real Salary Income = ₹40,000 + Personal Coffee Expense = ₹200
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Income(
                        destinationAccountId = bank.id,
                        amountPaise = 40_000_00L,
                        categoryId = "cat_inc_salary",
                        description = "Salary",
                        timestampEpochMs = now
                    )
                )
                ledgerRepository.postTransaction(
                    LedgerPostSpec.Expense(
                        accountId = cash.id,
                        amountPaise = 200_00L,
                        categoryId = "cat_exp_food",
                        paymentMethod = com.example.core.database.entity.PaymentMethod.CASH,
                        description = "Personal Coffee",
                        timestampEpochMs = now
                    )
                )

                // Independent Group 1: Friends Dinner (Paid by Me ₹3,000, Aarav ₹1k, Priya ₹1k, Me ₹1k)
                val group1 = splitBillRepository.createGroup("Friends", initialMemberNames = listOf("Aarav", "Priya"))
                val unusedMember = splitBillRepository.addParticipant(group1.id, "Vikram")
                val g1 = splitBillRepository.observeGroupDetail(group1.id).first()!!
                val me1 = g1.overview.participants.first { it.isCurrentUser }
                val aarav = g1.overview.participants.first { it.name == "Aarav" }
                val priya = g1.overview.participants.first { it.name == "Priya" }

                splitBillRepository.addExpense(
                    groupId = group1.id,
                    description = "Dinner",
                    totalAmountPaise = 3_000_00L,
                    expenseDateEpochMs = now,
                    paidByParticipantId = me1.id,
                    paidFromAccountId = bank.id,
                    splitSpec = SplitInputSpec.Equal(listOf(me1.id, aarav.id, priya.id))
                )
                // Bank = 50,000 + 40,000 - 3,000 = ₹87,000

                // Safe removal test: Vikram has zero balance & zero references -> hard deleted
                assertTrue(splitBillRepository.removeOrArchiveParticipantSafely(unusedMember.id))
                // Aarav owes ₹1,000 -> removal blocked!
                assertTrue(runCatching { splitBillRepository.removeOrArchiveParticipantSafely(aarav.id) }.isFailure)

                // Group cannot be hard-deleted while unsettled > 0
                assertTrue(runCatching { splitBillRepository.deleteGroupSafely(group1.id) }.isFailure)

                // Partial incoming reimbursement from Aarav: ₹400 -> Cash increases to ₹5,200
                val partialReimb = splitBillRepository.recordSettlement(
                    groupId = group1.id,
                    fromParticipantId = aarav.id,
                    toParticipantId = me1.id,
                    amountPaise = 400_00L,
                    settlementDateEpochMs = now,
                    accountId = cash.id
                )
                assertEquals(SettlementDirection.INCOMING_REIMBURSEMENT, partialReimb.direction)
                assertEquals(5_200_00L, db.accountDao().getAccountById(cash.id)!!.currentBalancePaise)

                // Remaining ₹600 from Aarav -> Cash becomes ₹5,800
                splitBillRepository.recordSettlement(
                    groupId = group1.id,
                    fromParticipantId = aarav.id,
                    toParticipantId = me1.id,
                    amountPaise = 600_00L,
                    settlementDateEpochMs = now,
                    accountId = cash.id
                )
                assertEquals(5_800_00L, db.accountDao().getAccountById(cash.id)!!.currentBalancePaise)

                // Edit partialReimb: test edit then back to ₹400
                splitBillRepository.editSettlement(
                    settlementId = partialReimb.id,
                    fromParticipantId = aarav.id,
                    toParticipantId = me1.id,
                    amountPaise = 500_00L,
                    settlementDateEpochMs = now,
                    accountId = cash.id,
                    notes = "Updated"
                )
                assertEquals(5_900_00L, db.accountDao().getAccountById(cash.id)!!.currentBalancePaise)
                splitBillRepository.editSettlement(
                    settlementId = partialReimb.id,
                    fromParticipantId = aarav.id,
                    toParticipantId = me1.id,
                    amountPaise = 400_00L,
                    settlementDateEpochMs = now,
                    accountId = cash.id,
                    notes = "Restored"
                )
                assertEquals(5_800_00L, db.accountDao().getAccountById(cash.id)!!.currentBalancePaise)

                // Now Aarav is settled (₹0 balance) & referenced -> removeOrArchiveParticipantSafely archives Aarav!
                assertEquals(false, splitBillRepository.removeOrArchiveParticipantSafely(aarav.id))
                assertTrue(db.splitBillDao().getParticipantById(aarav.id)!!.isArchived)

                // Independent Group 2: Family (Me owes Kabir ₹500)
                val group2 = splitBillRepository.createGroup("Family", initialMemberNames = listOf("Kabir"))
                val g2 = splitBillRepository.observeGroupDetail(group2.id).first()!!
                val me2 = g2.overview.participants.first { it.isCurrentUser }
                val kabir = g2.overview.participants.first { it.name == "Kabir" }

                // Kabir paid ₹1,000 split 50/50 -> Me owes ₹500, no Bank impact
                splitBillRepository.addExpense(
                    groupId = group2.id,
                    description = "Groceries",
                    totalAmountPaise = 1_000_00L,
                    expenseDateEpochMs = now,
                    paidByParticipantId = kabir.id,
                    paidFromAccountId = null,
                    splitSpec = SplitInputSpec.Equal(listOf(me2.id, kabir.id))
                )
                assertEquals(87_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

                // Outgoing settlement Me -> Kabir ₹500 from Bank -> Bank becomes ₹86,500
                val outgoing = splitBillRepository.recordSettlement(
                    groupId = group2.id,
                    fromParticipantId = me2.id,
                    toParticipantId = kabir.id,
                    amountPaise = 500_00L,
                    settlementDateEpochMs = now,
                    accountId = bank.id
                )
                assertEquals(SettlementDirection.OUTGOING_PAYMENT, outgoing.direction)
                assertEquals(86_500_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)

                // Archive & Restore Group 2 works; now settled so Safe Delete succeeds
                splitBillRepository.setGroupArchived(group2.id, true)
                assertTrue(db.splitBillDao().getGroupById(group2.id)!!.isArchived)
                splitBillRepository.setGroupArchived(group2.id, false)

                // Test HomeViewModel & ReportsViewModel reconciliation BEFORE deleting group 2
                homeViewModel = HomeViewModel(
                    accountRepository = accountRepository,
                    categoryRepository = categoryRepository,
                    ledgerRepository = ledgerRepository,
                    budgetRepository = budgetRepository,
                    creditCardRepository = creditCardRepository,
                    recurringRuleRepository = recurringRuleRepository,
                    userPreferencesRepository = preferencesRepository,
                    splitBillRepository = splitBillRepository
                )
                reportsViewModel = ReportsViewModel(
                    accountRepository = accountRepository,
                    ledgerRepository = ledgerRepository,
                    splitBillRepository = splitBillRepository
                )
                val homeJob = launch { homeViewModel!!.uiState.collect {} }
                val reportsJob = launch { reportsViewModel!!.uiState.collect {} }

                val readyHome = homeViewModel!!.uiState.first { it is HomeUiState.Ready } as HomeUiState.Ready
                val readyReports = reportsViewModel!!.uiState.first { it.periodIncome.paise > 0L }

                // CRITICAL: Reimbursements ₹1,000 must NOT inflate Ordinary Income (₹40,000 Salary ONLY!)
                assertEquals(40_000_00L, readyHome.monthlyIncome.paise)
                assertEquals(40_000_00L, readyReports.periodIncome.paise)

                // Group balances in SplitBillPortfolioSummary:
                // Paid by User = ₹3,000, User Personal Share = ₹1,000 (Dinner) + ₹500 (Groceries) = ₹1,500
                // Reimbursements Received = ₹1,000, You Are Owed = ₹1,000 (Priya), You Owe = ₹0
                assertEquals(3_000_00L, readyReports.groupExpensesPaidByUser.paise)
                assertEquals(1_500_00L, readyReports.userGroupExpenseShare.paise)
                assertEquals(1_000_00L, readyReports.splitReimbursementsReceived.paise)
                assertEquals(1_000_00L, readyReports.outstandingSplitReceivables.paise)
                assertEquals(0L, readyReports.splitPayables.paise)
                // Actual Personal Spending = ₹200 direct personal coffee + ₹1,500 group shares = ₹1,700
                assertEquals(1_700_00L, readyReports.actualPersonalSpending.paise)

                assertEquals(1_000_00L, readyHome.splitBillPortfolio.youAreOwed.paise)
                assertEquals(0L, readyHome.splitBillPortfolio.youOwe.paise)

                // Delete outgoing settlement reverses Bank +₹500 -> ₹87,000, then re-settle & safe-delete group2
                splitBillRepository.deleteSettlement(outgoing.id)
                assertEquals(87_000_00L, db.accountDao().getAccountById(bank.id)!!.currentBalancePaise)
                splitBillRepository.recordSettlement(
                    groupId = group2.id,
                    fromParticipantId = me2.id,
                    toParticipantId = kabir.id,
                    amountPaise = 500_00L,
                    settlementDateEpochMs = now,
                    accountId = bank.id
                )
                splitBillRepository.deleteGroupSafely(group2.id)
                assertNull(db.splitBillDao().getGroupById(group2.id))

                assertTrue(engine.verifyGlobalLedgerIntegrity().isFullyHealthy)

                homeJob.cancelAndJoin()
                reportsJob.cancelAndJoin()
            }
        } finally {
            homeViewModel?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            reportsViewModel?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            Dispatchers.resetMain()
        }
        Unit
    }
}
