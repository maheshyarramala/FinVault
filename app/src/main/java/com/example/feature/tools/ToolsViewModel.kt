package com.example.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ToolCategory {
    ALL,
    CALCULATORS,
    FINANCE,
    CONVERTERS,
    INVESTMENTS
}

data class ToolCatalogItem(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val targetPhase: Int,
    val icon: ImageVector
)

data class ToolsUiState(
    val selectedCategory: ToolCategory = ToolCategory.ALL,
    val visibleTools: List<ToolCatalogItem> = allCatalogTools
)

private val allCatalogTools = listOf(
    ToolCatalogItem(
        id = "debt_management",
        title = "Debt Management",
        description = "Track money lent and borrowed, interest and repayments",
        category = ToolCategory.FINANCE,
        targetPhase = 6,
        icon = Icons.Filled.AccountBalance
    ),
    ToolCatalogItem(
        id = "split_bill",
        title = "Split Bill & Group Expenses",
        description = "Create groups, split unequally or by percentage, simplify debts & settle atomically",
        category = ToolCategory.FINANCE,
        targetPhase = 5,
        icon = Icons.Filled.CallSplit
    ),
    ToolCatalogItem(
        id = "basic_calc",
        title = "Basic Calculator",
        description = "Arithmetic, percentage, and restorable calculation history",
        category = ToolCategory.CALCULATORS,
        targetPhase = 4,
        icon = Icons.Filled.Calculate
    ),
    ToolCatalogItem(
        id = "scientific_calc",
        title = "Scientific Calculator",
        description = "Trigonometry, logarithms, powers, roots, constants & parentheses",
        category = ToolCategory.CALCULATORS,
        targetPhase = 4,
        icon = Icons.Filled.Science
    ),
    ToolCatalogItem(
        id = "gst_calc",
        title = "GST & Discount Calculator",
        description = "Inclusive/exclusive GST slabs (5%, 12%, 18%, 28%) and tip splits",
        category = ToolCategory.FINANCE,
        targetPhase = 4,
        icon = Icons.Filled.Receipt
    ),
    ToolCatalogItem(
        id = "emi_calc",
        title = "Loan EMI & Interest",
        description = "Simple, compound, inflation, and full amortization estimator",
        category = ToolCategory.FINANCE,
        targetPhase = 4,
        icon = Icons.Filled.AccountBalance
    ),
    ToolCatalogItem(
        id = "date_age_calc",
        title = "Age & Date Difference",
        description = "Exact years, months, days between dates and milestones",
        category = ToolCategory.CALCULATORS,
        targetPhase = 4,
        icon = Icons.Filled.DateRange
    ),
    ToolCatalogItem(
        id = "percentage_calc",
        title = "Percentage & Ratio",
        description = "Markup, margin, percentage change and ratio breakdowns",
        category = ToolCategory.FINANCE,
        targetPhase = 4,
        icon = Icons.Filled.Percent
    ),
    ToolCatalogItem(
        id = "currency_conv",
        title = "Currency Converter",
        description = "INR and global currencies with offline-cached rates",
        category = ToolCategory.CONVERTERS,
        targetPhase = 4,
        icon = Icons.Filled.CurrencyExchange
    ),
    ToolCatalogItem(
        id = "unit_conv",
        title = "Unit Converter",
        description = "Length, weight, area, volume, temperature, speed & digital storage",
        category = ToolCategory.CONVERTERS,
        targetPhase = 4,
        icon = Icons.Filled.Straighten
    ),
    ToolCatalogItem(
        id = "sip_suite",
        title = "SIP & Step-Up SIP",
        description = "Estimated mutual fund wealth growth and inflation-adjusted projections",
        category = ToolCategory.INVESTMENTS,
        targetPhase = 7,
        icon = Icons.Filled.TrendingUp
    ),
    ToolCatalogItem(
        id = "fd_rd_cagr",
        title = "FD, RD, Lump Sum & CAGR",
        description = "Scenario comparison with clear estimate vs actual return labels",
        category = ToolCategory.INVESTMENTS,
        targetPhase = 7,
        icon = Icons.Filled.ShowChart
    )
)

class ToolsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun selectCategory(category: ToolCategory) {
        _uiState.update {
            val filtered = if (category == ToolCategory.ALL) {
                allCatalogTools
            } else {
                allCatalogTools.filter { tool -> tool.category == category }
            }
            it.copy(selectedCategory = category, visibleTools = filtered)
        }
    }
}
