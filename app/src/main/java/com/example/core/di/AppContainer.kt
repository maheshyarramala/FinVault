package com.example.core.di

import android.content.Context
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.repository.AccountRepository
import com.example.core.data.repository.BudgetRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.data.repository.CreditCardRepository
import com.example.core.data.repository.LedgerRepository
import com.example.core.data.repository.RecurringRuleRepository
import com.example.core.data.repository.SplitBillRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.domain.ledger.CentralLedgerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Constructor-injected application dependency container.
 * Exposes CentralLedgerEngine and Finance Repositories as the single source of truth.
 */
class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: FinVaultDatabase = FinVaultDatabase.create(context)

    val userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)
    val accountRepository: AccountRepository = AccountRepository(database.accountDao())
    val categoryRepository: CategoryRepository = CategoryRepository(database.categoryDao())
    val centralLedgerEngine: CentralLedgerEngine = CentralLedgerEngine(database)
    val ledgerRepository: LedgerRepository = LedgerRepository(
        ledgerDao = database.ledgerDao(),
        engine = centralLedgerEngine
    )
    val creditCardRepository: CreditCardRepository = CreditCardRepository(
        creditCardDao = database.creditCardDao(),
        ledgerEngine = centralLedgerEngine,
        accountDao = database.accountDao()
    )
    val budgetRepository: BudgetRepository = BudgetRepository(
        budgetDao = database.budgetDao(),
        ledgerDao = database.ledgerDao(),
        categoryDao = database.categoryDao()
    )
    val recurringRuleRepository: RecurringRuleRepository = RecurringRuleRepository(
        recurringRuleDao = database.recurringRuleDao(),
        accountDao = database.accountDao(),
        ledgerEngine = centralLedgerEngine
    )
    val splitBillRepository: SplitBillRepository = SplitBillRepository(
        database = database,
        splitBillDao = database.splitBillDao(),
        accountDao = database.accountDao(),
        ledgerEngine = centralLedgerEngine
    )

    init {
        applicationScope.launch {
            categoryRepository.ensureDefaultCategoriesSeeded()
        }
    }
}
