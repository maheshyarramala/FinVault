package com.example.feature.splitbill

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.core.data.repository.SplitGroupOverview
import com.example.core.database.dao.SplitExpenseWithShares
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity

enum class SplitGroupFilter(val label: String) {
    ACTIVE("Active"),
    YOU_OWE("You Owe"),
    OWED_TO_YOU("Owed to You"),
    SETTLED("Settled"),
    ARCHIVED("Archived")
}

enum class SplitGroupDetailTab(val label: String) {
    OVERVIEW("Overview"),
    EXPENSES("Expenses"),
    PEOPLE("People"),
    BALANCES("Balances"),
    SETTLEMENTS("Settlements")
}

data class SplitIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

val splitGroupIconOptions = listOf(
    SplitIconOption("group", "Friends", Icons.Filled.Groups),
    SplitIconOption("flight", "Trip", Icons.Filled.Flight),
    SplitIconOption("home", "Home", Icons.Filled.Home),
    SplitIconOption("work", "Office", Icons.Filled.Work),
    SplitIconOption("restaurant", "Dining", Icons.Filled.Restaurant),
    SplitIconOption("car", "Commute", Icons.Filled.DirectionsCar),
    SplitIconOption("shopping", "Shopping", Icons.Filled.ShoppingBag)
)

fun resolveSplitGroupIcon(iconKey: String): ImageVector =
    splitGroupIconOptions.firstOrNull { it.key == iconKey }?.icon ?: Icons.Filled.Groups

sealed interface SplitHubOverlay {
    data object None : SplitHubOverlay
    data object CreateGroup : SplitHubOverlay
    data class EditGroup(val overview: SplitGroupOverview) : SplitHubOverlay
}

sealed interface SplitGroupOverlay {
    data object None : SplitGroupOverlay
    data object EditGroup : SplitGroupOverlay
    data object AddParticipant : SplitGroupOverlay
    data class EditParticipant(val participant: SplitParticipantEntity) : SplitGroupOverlay
    data class PersonDetail(val participantId: String) : SplitGroupOverlay
    data class ExpenseForm(val editingExpense: SplitExpenseWithShares? = null) : SplitGroupOverlay
    data class ExpenseDetail(val expenseId: String) : SplitGroupOverlay
    data class SettlementForm(
        val editingSettlement: SplitSettlementEntity? = null,
        val prefillFromId: String? = null,
        val prefillToId: String? = null,
        val prefillAmountPaise: Long? = null
    ) : SplitGroupOverlay
}
