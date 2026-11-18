package com.example.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultSectionHeader
import com.example.core.designsystem.theme.AppThemeMode
import com.example.core.designsystem.theme.LocalFinVaultColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val semantic = LocalFinVaultColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            FinVaultSectionHeader(title = stringResource(R.string.settings_appearance_section))
        }

        item {
            FinVaultCard {
                Text(
                    text = stringResource(R.string.settings_theme_mode_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                val modes = listOf(
                    AppThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    AppThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    AppThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                    AppThemeMode.AMOLED to stringResource(R.string.settings_theme_amoled)
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_theme_selector")
                ) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = uiState.preferences.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            modifier = Modifier.testTag("theme_option_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = semantic.cardBorder)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_dynamic_color_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.preferences.dynamicColorEnabled,
                            onCheckedChange = viewModel::setDynamicColorEnabled,
                            enabled = uiState.preferences.themeMode != AppThemeMode.AMOLED,
                            modifier = Modifier.testTag("settings_dynamic_color_switch")
                        )
                    }
                }
            }
        }

        item {
            FinVaultSectionHeader(title = stringResource(R.string.settings_locale_section))
        }

        item {
            FinVaultCard {
                SettingsReadOnlyRow(
                    title = stringResource(R.string.settings_currency_title),
                    value = stringResource(R.string.settings_currency_value)
                )
                HorizontalDivider(
                    color = semantic.cardBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingsReadOnlyRow(
                    title = stringResource(R.string.settings_precision_title),
                    value = stringResource(R.string.settings_precision_value)
                )
                HorizontalDivider(
                    color = semantic.cardBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingsReadOnlyRow(
                    title = stringResource(R.string.settings_payment_default_title),
                    value = stringResource(R.string.settings_payment_default_value)
                )
            }
        }

        item {
            FinVaultSectionHeader(title = stringResource(R.string.settings_system_section))
        }

        item {
            FinVaultCard {
                SettingsReadOnlyRow(
                    title = stringResource(R.string.settings_offline_title),
                    value = stringResource(R.string.settings_offline_subtitle)
                )
                HorizontalDivider(
                    color = semantic.cardBorder,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                SettingsReadOnlyRow(
                    title = stringResource(R.string.settings_db_schema_title),
                    value = stringResource(R.string.db_version_label, uiState.databaseVersion)
                )
            }
        }
    }
}

@Composable
private fun SettingsReadOnlyRow(
    title: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
