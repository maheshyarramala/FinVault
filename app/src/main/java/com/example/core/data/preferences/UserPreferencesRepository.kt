package com.example.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.core.designsystem.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HomeDashboardWidget(val label: String) {
    MONTHLY_SUMMARY("Monthly Financial Summary"),
    INCOME_VS_EXPENSE_CHART("Income vs Expense Chart"),
    CATEGORY_CHART("Expense Breakdown Chart"),
    RECENT_TRANSACTIONS("Recent Transactions"),
    BUDGET_PROGRESS("Budget Progress"),
    EMI_AND_CARDS("EMI Reminders, Loans & Cards"),
    DEBTS_SUMMARY("Outstanding Debts"),
    SPLIT_BILL_SUMMARY("Split Bill Summary"),
    SAVINGS_OVERVIEW("Savings & Accounts Overview")
}

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "finvault_user_preferences"
)

data class UserPreferencesState(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val hiddenHomeWidgets: Set<HomeDashboardWidget> = emptySet()
)

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.userPreferencesDataStore

    val preferences: Flow<UserPreferencesState> = dataStore.data.map { prefs ->
        val rawTheme = prefs[KEY_THEME_MODE]
        val themeMode = rawTheme?.let {
            runCatching { AppThemeMode.valueOf(it) }.getOrNull()
        } ?: AppThemeMode.SYSTEM
        val dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: false
        val hiddenRaw = prefs[KEY_HIDDEN_HOME_WIDGETS].orEmpty()
        val hiddenWidgets = hiddenRaw.mapNotNull { name ->
            runCatching { HomeDashboardWidget.valueOf(name) }.getOrNull()
        }.toSet()
        UserPreferencesState(
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColor,
            hiddenHomeWidgets = hiddenWidgets
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setHomeWidgetVisible(widget: HomeDashboardWidget, visible: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_HOME_WIDGETS].orEmpty().toMutableSet()
            if (visible) {
                current.remove(widget.name)
            } else {
                current.add(widget.name)
            }
            prefs[KEY_HIDDEN_HOME_WIDGETS] = current
        }
    }

    suspend fun resetHomeWidgets() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_HIDDEN_HOME_WIDGETS)
        }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
        val KEY_HIDDEN_HOME_WIDGETS = stringSetPreferencesKey("hidden_home_widgets")
    }
}
