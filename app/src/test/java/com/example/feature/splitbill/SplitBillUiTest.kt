package com.example.feature.splitbill

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitBillUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `split bill end-to-end flow creates group, adds expense without tax tip discount, inspects person detail without card edit button, and settles`() {
        composeRule.waitForIdle()

        // 1. Create a Bank account first in Finance
        composeRule.onNodeWithTag("nav_finance").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_btn_add_account").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_name_input").performTextInput("HDFC Split Bank")
        composeRule.onNodeWithTag("account_opening_input").performTextInput("10000")
        composeRule.onNodeWithTag("account_create_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("account_name_input").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("HDFC Split Bank").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Navigate Tools -> Split Bill Hub -> Create "Goa Trip" with Aarav & Priya
        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("tools_screen_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tools_screen_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("tool_card_split_bill"))
        composeRule.onNodeWithTag("tool_card_split_bill").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_create_group_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("split_create_group_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_group_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("split_group_name_input").performTextInput("Goa Trip")
        composeRule.onNodeWithTag("split_group_members_input").performTextInput("Aarav, Priya")
        composeRule.onNodeWithTag("split_group_save_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_group_name_input").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("split_hub_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Goa Trip"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Goa Trip").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Open Group Detail -> Add Expense ₹3,000 Paid by Me
        composeRule.onNodeWithText("Goa Trip").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_group_add_expense_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("split_group_add_expense_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_exp_desc_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify NO Tax, Tip, or Discount fields exist in Split Bill expense form
        assertEquals(0, composeRule.onAllNodesWithText("Tax", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Tip", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Discount", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("split_exp_desc_input").performTextInput("Beach Villa")
        composeRule.onNodeWithTag("split_exp_amount_input").performTextInput("3000")
        composeRule.onNodeWithTag("split_exp_submit_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_exp_desc_input").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("split_overview_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Beach Villa"))
        composeRule.onNodeWithText("Beach Villa").assertIsDisplayed()

        // 4. Switch to People tab -> verify NO Edit icon buttons on cards -> tap Aarav card -> Person Detail opens
        composeRule.onNodeWithTag("split_tab_people").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_people_list").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("Aarav").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Edit", ignoreCase = true).fetchSemanticsNodes().size)

        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("saved", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("split_people_list")
            .performScrollToNode(hasText("Aarav"))
        composeRule.onNodeWithText("Aarav").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("person_detail_close_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Total Share").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Amount Owed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("person_detail_edit_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("person_detail_close_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("person_detail_close_btn").fetchSemanticsNodes().isEmpty()
        }

        // 5. Switch to Settlements tab -> Record Settlement (default prefilled Aarav -> Me)
        composeRule.onNodeWithTag("split_tab_settlements").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_record_settlement_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("split_record_settlement_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_settle_amount_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("split_settle_amount_input").performTextInput("1000")
        composeRule.onNodeWithTag("split_settle_submit_btn").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("split_settle_amount_input").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Aarav paid Me").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
