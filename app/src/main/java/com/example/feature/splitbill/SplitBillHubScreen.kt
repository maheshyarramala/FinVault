package com.example.feature.splitbill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.DateTimeUtils
import com.example.core.data.repository.SplitGroupOverview
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultEmptyStateCard
import com.example.core.designsystem.component.FinVaultMetricTile
import com.example.core.designsystem.component.FinVaultSectionHeader
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors

@Composable
fun SplitBillHubScreen(
    viewModel: SplitBillHubViewModel,
    onNavigateBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalFinVaultColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("split_hub_loading")
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("split_hub_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("split_hub_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            Column {
                                Text(
                                    text = "Split Bill",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Groups, fair shares & ledger settlements",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = viewModel::openCreateGroup,
                            modifier = Modifier.testTag("split_create_group_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("Group")
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FinVaultMetricTile(
                                label = "Total Group Expenses",
                                value = uiState.totalGroupExpenses.formatInr(),
                                icon = Icons.Filled.ReceiptLong,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("split_summary_total_expenses")
                            )
                            FinVaultMetricTile(
                                label = "Unsettled Amount",
                                value = uiState.unsettledAmount.formatInr(),
                                icon = Icons.Filled.Balance,
                                tint = semantic.warning,
                                containerColor = semantic.warningContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("split_summary_unsettled")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FinVaultMetricTile(
                                label = "You Owe",
                                value = uiState.youOwe.formatInr(),
                                icon = Icons.Filled.ArrowUpward,
                                tint = semantic.expense,
                                containerColor = semantic.expenseContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("split_summary_you_owe")
                            )
                            FinVaultMetricTile(
                                label = "You Are Owed",
                                value = uiState.youAreOwed.formatInr(),
                                icon = Icons.Filled.ArrowDownward,
                                tint = semantic.income,
                                containerColor = semantic.incomeContainer,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("split_summary_you_are_owed")
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        label = { Text("Search groups…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_group_search_input")
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SplitGroupFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.selectedFilter == filter,
                                onClick = { viewModel.selectFilter(filter) },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("split_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                item {
                    FinVaultSectionHeader(
                        title = if (uiState.selectedFilter == SplitGroupFilter.ARCHIVED) {
                            "Archived Groups"
                        } else {
                            "Your Groups"
                        },
                        trailingText = "${uiState.filteredGroups.size} shown"
                    )
                }

                if (uiState.filteredGroups.isEmpty()) {
                    item {
                        FinVaultEmptyStateCard(
                            icon = Icons.Filled.CallSplit,
                            title = if (uiState.selectedFilter == SplitGroupFilter.ARCHIVED) {
                                "No Archived Groups"
                            } else {
                                "No Split Groups Found"
                            },
                            body = if (uiState.selectedFilter == SplitGroupFilter.ARCHIVED) {
                                "Archived groups stay safely stored with their full expenses, shares, and settlement history."
                            } else {
                                "Create a group for Goa Trip, Roommates, Family, or Office lunches to track shared expenses fairly."
                            },
                            actionLabel = if (uiState.selectedFilter != SplitGroupFilter.ARCHIVED) {
                                "Create Group"
                            } else {
                                null
                            },
                            onAction = if (uiState.selectedFilter != SplitGroupFilter.ARCHIVED) {
                                viewModel::openCreateGroup
                            } else {
                                null
                            }
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredGroups,
                        key = { it.group.id }
                    ) { overview ->
                        SplitGroupRowCard(
                            overview = overview,
                            onClick = { onOpenGroup(overview.group.id) },
                            onToggleArchive = {
                                viewModel.setGroupArchived(overview.group.id, !overview.group.isArchived)
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::openCreateGroup,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("split_hub_fab_create")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create Split Group")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    when (val overlay = uiState.activeOverlay) {
        SplitHubOverlay.None -> Unit
        SplitHubOverlay.CreateGroup -> {
            CreateEditSplitGroupDialog(
                existingGroup = null,
                onDismiss = viewModel::dismissOverlay,
                onSave = { name, iconKey, desc, membersRaw ->
                    viewModel.createGroup(name, iconKey, desc, membersRaw)
                }
            )
        }
        is SplitHubOverlay.EditGroup -> {
            CreateEditSplitGroupDialog(
                existingGroup = overlay.overview.group,
                onDismiss = viewModel::dismissOverlay,
                onSave = { name, iconKey, desc, _ ->
                    viewModel.updateGroup(overlay.overview.group.id, name, iconKey, desc)
                }
            )
        }
    }
}

@Composable
private fun SplitGroupRowCard(
    overview: SplitGroupOverview,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit
) {
    val semantic = LocalFinVaultColors.current
    val net = overview.netBalance.paise
    val balanceLabel = when {
        net > 0L -> "Owed to you ${overview.youAreOwed.formatInr()}"
        net < 0L -> "You owe ${overview.youOwe.formatInr()}"
        else -> "Settled"
    }
    val balanceContainer = when {
        net > 0L -> semantic.incomeContainer
        net < 0L -> semantic.expenseContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val balanceContent = when {
        net > 0L -> semantic.income
        net < 0L -> semantic.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    FinVaultCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("split_group_card_${overview.group.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = resolveSplitGroupIcon(overview.group.iconKey),
                    contentDescription = overview.group.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overview.group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${overview.activeMemberCount} members • Last activity ${DateTimeUtils.formatShortDate(overview.lastActivityEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onToggleArchive,
                modifier = Modifier.testTag("split_group_archive_btn_${overview.group.id}")
            ) {
                Icon(
                    imageVector = if (overview.group.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    contentDescription = if (overview.group.isArchived) "Restore group" else "Archive group",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total Expenses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = overview.totalExpenses.formatInr(),
                    style = FinVaultNumericTypography.rowAmount
                )
            }
            FinVaultStatusPill(
                text = balanceLabel,
                containerColor = balanceContainer,
                contentColor = balanceContent
            )
        }
    }
}
