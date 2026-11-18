package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.repository.CategoryRepository
import com.example.core.database.FinVaultDatabase
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.designsystem.theme.AppThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var testDb: FinVaultDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testDb = Room.inMemoryDatabaseBuilder(context, FinVaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    @Test
    fun `app name matches FinVault`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FinVault", appName)
    }

    @Test
    fun `database seeds default categories idempotently and aggregates account balances`() = runTest {
        val categoryRepo = CategoryRepository(testDb.categoryDao())
        categoryRepo.ensureDefaultCategoriesSeeded()
        categoryRepo.ensureDefaultCategoriesSeeded()

        val categories = testDb.categoryDao().observeAllCategories().first()
        assertEquals(13, categories.size)

        val now = System.currentTimeMillis()
        testDb.accountDao().insertAccount(
            AccountEntity(
                id = "acc_bank_1",
                name = "HDFC Savings",
                accountType = AccountType.BANK,
                openingBalancePaise = 2500000L,
                currentBalancePaise = 2500000L,
                isDefault = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        val total = testDb.accountDao().observeTotalLiquidBalancePaise().first()
        assertEquals(2500000L, total)
    }

    @Test
    fun `user preferences persist AMOLED theme selection`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsRepo = UserPreferencesRepository(context)
        prefsRepo.setThemeMode(AppThemeMode.AMOLED)
        prefsRepo.setDynamicColorEnabled(true)

        val current = prefsRepo.preferences.first()
        assertEquals(AppThemeMode.AMOLED, current.themeMode)
        assertEquals(true, current.dynamicColorEnabled)
    }

    @Test
    fun `bottom navigation switches across all five tabs and interactive filters work`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_available_balance_amount").assertIsDisplayed()

        // Finance opens Expenses & Accounts by default
        composeRule.onNodeWithTag("nav_finance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_segment_switcher").assertIsDisplayed()
        composeRule.onNodeWithTag("finance_tab_expenses_and_accounts").assertIsSelected()
        composeRule.onNodeWithTag("finance_tab_accounts").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_tab_accounts").assertIsSelected()

        // Tools filters
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tools_screen_list").assertIsDisplayed()
        composeRule.onNodeWithTag("tools_filter_investments").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tools_filter_investments").assertIsSelected()

        // Reports periods
        composeRule.onNodeWithTag("nav_reports").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reports_period_switcher").assertIsDisplayed()
        composeRule.onNodeWithTag("reports_period_yearly").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithTag("reports_period_yearly").assertIsSelected()
                true
            } catch (_: AssertionError) {
                false
            }
        }

        // Settings theme switching (DataStore writes asynchronously on Dispatchers.IO)
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_theme_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_option_dark").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            try {
                composeRule.onNodeWithTag("theme_option_dark").assertIsSelected()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    @Test
    fun `finance screen switches across all 5 tabs and creates account plus bank-default expense`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_finance").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("finance_tab_cards").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_tab_cards").assertIsSelected()

        composeRule.onNodeWithTag("finance_tab_budgets").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_tab_budgets").assertIsSelected()

        composeRule.onNodeWithTag("finance_tab_expenses_and_accounts").performClick()
        composeRule.waitForIdle()

        // Create a Bank account
        composeRule.onNodeWithTag("finance_btn_add_account").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_name_input").performTextInput("Kotak Bank")
        composeRule.onNodeWithTag("account_opening_input").performTextInput("5000")
        composeRule.onNodeWithTag("account_create_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("account_name_input").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Kotak Bank").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Expense form and verify Bank payment method is selected by default
        composeRule.onNodeWithTag("finance_btn_add_expense").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_method_bank").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tx_method_bank").assertIsSelected()
        composeRule.onNodeWithTag("tx_amount_input").performTextInput("450")
        composeRule.onNodeWithTag("tx_description_input").performTextInput("Metro Pass")
        composeRule.onNodeWithTag("tx_submit_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Metro Pass").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `home dashboard shows customize widgets dialog and toggles widget visibility`() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_customize_widgets_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_customize_widgets_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_customize_widgets_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("widget_toggle_debts_summary").performClick()
        composeRule.onNodeWithTag("customize_widgets_done").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_customize_widgets_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("customize_widgets_reset").performClick()
        composeRule.onNodeWithTag("customize_widgets_done").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `transaction detail allows edit and delete and category dialog preserves form input`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_finance").performClick()
        composeRule.waitForIdle()

        // Create account first
        composeRule.onNodeWithTag("finance_btn_add_account").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("account_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("account_name_input").performTextInput("SBI Checking")
        composeRule.onNodeWithTag("account_opening_input").performTextInput("10000")
        composeRule.onNodeWithTag("account_create_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("account_name_input").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("SBI Checking").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Open expense form, type amount, open + New Category, add category, confirm amount stays intact
        composeRule.onNodeWithTag("finance_btn_add_expense").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_amount_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tx_amount_input").performTextInput("650")
        composeRule.onNodeWithTag("tx_description_input").performTextInput("Cab Ride")
        composeRule.onNodeWithText("+ New Category").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("category_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("category_name_input").performTextInput("Commute")
        composeRule.onNodeWithTag("category_save_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("category_name_input").fetchSemanticsNodes().isEmpty()
        }
        // Form is still open with submit button active!
        composeRule.onNodeWithTag("tx_submit_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_amount_input").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("finance_tab_expenses").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_screen_list").performScrollToNode(hasText("Cab Ride"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Cab Ride").fetchSemanticsNodes().isNotEmpty()
        }

        // Open transaction detail -> Edit -> Update description -> Verify
        composeRule.onNodeWithText("Cab Ride").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_detail_edit_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tx_detail_edit_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_description_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tx_description_input").performTextReplacement("Cab Ride Airport")
        composeRule.onNodeWithTag("tx_submit_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_amount_input").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("finance_screen_list").performScrollToNode(hasText("Cab Ride Airport"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Cab Ride Airport").fetchSemanticsNodes().isNotEmpty()
        }

        // Open detail again -> Delete -> Reverse & Delete -> Verify removed
        composeRule.onNodeWithText("Cab Ride Airport").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_detail_delete_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tx_detail_delete_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_confirm_delete_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tx_confirm_delete_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tx_confirm_delete_button").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Cab Ride Airport").fetchSemanticsNodes().isEmpty()
        }
    }
}
