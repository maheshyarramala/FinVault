package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.data.preferences.UserPreferencesState
import com.example.core.designsystem.theme.AppThemeMode
import com.example.core.designsystem.theme.FinVaultTheme
import com.example.feature.navigation.FinVaultNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as FinVaultApp).appContainer

        setContent {
            val prefs by appContainer.userPreferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferencesState())

            val systemDark = isSystemInDarkTheme()
            val useDarkIcons = when (prefs.themeMode) {
                AppThemeMode.SYSTEM -> !systemDark
                AppThemeMode.LIGHT -> true
                AppThemeMode.DARK, AppThemeMode.AMOLED -> false
            }

            DisposableEffect(useDarkIcons) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkIcons) {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (useDarkIcons) {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            FinVaultTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColorEnabled
            ) {
                FinVaultNavHost(appContainer = appContainer)
            }
        }
    }
}
