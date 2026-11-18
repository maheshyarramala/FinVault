package com.example.feature.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import kotlinx.serialization.Serializable

sealed interface FinVaultRoute {
    @Serializable
    data object Home : FinVaultRoute

    @Serializable
    data object Finance : FinVaultRoute

    @Serializable
    data object Tools : FinVaultRoute

    @Serializable
    data object Reports : FinVaultRoute

    @Serializable
    data object Settings : FinVaultRoute

    @Serializable
    data object SplitBillHub : FinVaultRoute

    @Serializable
    data class SplitBillGroupDetail(val groupId: String) : FinVaultRoute
}

enum class BottomNavItem(
    val route: FinVaultRoute,
    @param:StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        route = FinVaultRoute.Home,
        labelResId = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    FINANCE(
        route = FinVaultRoute.Finance,
        labelResId = R.string.nav_finance,
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_finance"
    ),
    TOOLS(
        route = FinVaultRoute.Tools,
        labelResId = R.string.nav_tools,
        selectedIcon = Icons.Filled.Calculate,
        unselectedIcon = Icons.Outlined.Calculate,
        testTag = "nav_tools"
    ),
    REPORTS(
        route = FinVaultRoute.Reports,
        labelResId = R.string.nav_reports,
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        testTag = "nav_reports"
    ),
    SETTINGS(
        route = FinVaultRoute.Settings,
        labelResId = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
}
