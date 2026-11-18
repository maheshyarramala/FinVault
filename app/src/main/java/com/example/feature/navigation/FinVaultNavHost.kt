package com.example.feature.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.designsystem.component.FinVaultAdaptiveScaffold
import com.example.core.di.AppContainer
import com.example.feature.finance.FinanceScreen
import com.example.feature.finance.FinanceSectionTab
import com.example.feature.finance.FinanceViewModel
import com.example.feature.debt.DebtManagementScreen
import com.example.feature.debt.DebtManagementViewModel
import com.example.feature.finance.TransactionFormKind
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.reports.ReportsScreen
import com.example.feature.reports.ReportsViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.splitbill.SplitBillHubScreen
import com.example.feature.splitbill.SplitBillHubViewModel
import com.example.feature.splitbill.SplitGroupDetailScreen
import com.example.feature.splitbill.SplitGroupDetailViewModel
import com.example.feature.tools.ToolsScreen
import com.example.feature.tools.ToolsViewModel

@Composable
fun FinVaultNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var pendingFinanceFormKind by remember { mutableStateOf<TransactionFormKind?>(null) }
    var pendingFinanceTab by remember { mutableStateOf<FinanceSectionTab?>(null) }

    val selectedNavItem = BottomNavItem.entries.firstOrNull { item ->
        currentDestination?.hasRoute(item.route::class) == true
    } ?: if (
        currentDestination?.hasRoute(FinVaultRoute.DebtManagement::class) == true ||
        currentDestination?.hasRoute(FinVaultRoute.SplitBillHub::class) == true ||
        currentDestination?.hasRoute(FinVaultRoute.SplitBillGroupDetail::class) == true
    ) {
        BottomNavItem.TOOLS
    } else {
        BottomNavItem.HOME
    }

    fun navigateToTopLevel(route: FinVaultRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSplitBillHub() {
        navController.navigate(FinVaultRoute.SplitBillHub) {
            launchSingleTop = true
        }
    }

    FinVaultAdaptiveScaffold(
        items = BottomNavItem.entries,
        selectedRoute = selectedNavItem,
        onSelectItem = { item -> navigateToTopLevel(item.route) }
    ) { scaffoldModifier ->
        NavHost(
            navController = navController,
            startDestination = FinVaultRoute.Home,
            modifier = modifier.then(scaffoldModifier)
        ) {
            composable<FinVaultRoute.Home> {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        accountRepository = appContainer.accountRepository,
                        categoryRepository = appContainer.categoryRepository,
                        ledgerRepository = appContainer.ledgerRepository,
                        budgetRepository = appContainer.budgetRepository,
                        creditCardRepository = appContainer.creditCardRepository,
                        recurringRuleRepository = appContainer.recurringRuleRepository,
                        userPreferencesRepository = appContainer.userPreferencesRepository,
                        splitBillRepository = appContainer.splitBillRepository
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onQuickTransaction = { kind ->
                        pendingFinanceFormKind = kind
                        navigateToTopLevel(FinVaultRoute.Finance)
                    },
                    onOpenFinanceTab = { tab ->
                        pendingFinanceTab = tab
                        navigateToTopLevel(FinVaultRoute.Finance)
                    },
                    onOpenReports = {
                        navigateToTopLevel(FinVaultRoute.Reports)
                    },
                    onOpenSplitBill = {
                        openSplitBillHub()
                    },
                    onOpenDebtManagement = {
                        navController.navigate(FinVaultRoute.DebtManagement) { launchSingleTop = true }
                    }
                )
            }
            composable<FinVaultRoute.Finance> {
                val financeViewModel: FinanceViewModel = viewModel(
                    factory = FinanceViewModel.factory(
                        accountRepository = appContainer.accountRepository,
                        categoryRepository = appContainer.categoryRepository,
                        ledgerRepository = appContainer.ledgerRepository,
                        creditCardRepository = appContainer.creditCardRepository,
                        budgetRepository = appContainer.budgetRepository,
                        recurringRuleRepository = appContainer.recurringRuleRepository
                    )
                )
                LaunchedEffect(pendingFinanceFormKind, pendingFinanceTab) {
                    pendingFinanceTab?.let { tab ->
                        financeViewModel.selectTab(tab)
                        pendingFinanceTab = null
                    }
                    pendingFinanceFormKind?.let { kind ->
                        financeViewModel.openTransactionForm(kind)
                        pendingFinanceFormKind = null
                    }
                }
                FinanceScreen(viewModel = financeViewModel)
            }
            composable<FinVaultRoute.Tools> {
                val toolsViewModel: ToolsViewModel = viewModel()
                ToolsScreen(
                    viewModel = toolsViewModel,
                    onOpenSplitBill = { openSplitBillHub() },
                    onOpenDebtManagement = {
                        navController.navigate(FinVaultRoute.DebtManagement) { launchSingleTop = true }
                    }
                )
            }
            composable<FinVaultRoute.Reports> {
                val reportsViewModel: ReportsViewModel = viewModel(
                    factory = ReportsViewModel.factory(
                        accountRepository = appContainer.accountRepository,
                        ledgerRepository = appContainer.ledgerRepository,
                        splitBillRepository = appContainer.splitBillRepository,
                        debtRepository = appContainer.debtRepository
                    )
                )
                ReportsScreen(viewModel = reportsViewModel)
            }
            composable<FinVaultRoute.Settings> {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(
                        preferencesRepository = appContainer.userPreferencesRepository
                    )
                )
                SettingsScreen(viewModel = settingsViewModel)
            }
            composable<FinVaultRoute.DebtManagement> {
                val debtViewModel: DebtManagementViewModel = viewModel(
                    factory = DebtManagementViewModel.factory(
                        debtRepository = appContainer.debtRepository,
                        accountRepository = appContainer.accountRepository
                    )
                )
                DebtManagementScreen(
                    viewModel = debtViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable<FinVaultRoute.SplitBillHub> {
                val splitBillHubViewModel: SplitBillHubViewModel = viewModel(
                    factory = SplitBillHubViewModel.factory(
                        splitBillRepository = appContainer.splitBillRepository
                    )
                )
                SplitBillHubScreen(
                    viewModel = splitBillHubViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenGroup = { groupId ->
                        navController.navigate(FinVaultRoute.SplitBillGroupDetail(groupId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<FinVaultRoute.SplitBillGroupDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<FinVaultRoute.SplitBillGroupDetail>()
                val groupDetailViewModel: SplitGroupDetailViewModel = viewModel(
                    factory = SplitGroupDetailViewModel.factory(
                        groupId = route.groupId,
                        splitBillRepository = appContainer.splitBillRepository,
                        accountRepository = appContainer.accountRepository
                    )
                )
                SplitGroupDetailScreen(
                    viewModel = groupDetailViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
