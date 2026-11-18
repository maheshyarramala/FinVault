package com.example.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

@Immutable
data class FinVaultSemanticColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val cardBorder: Color,
    val isAmoled: Boolean
)

val LocalFinVaultColors = staticCompositionLocalOf {
    FinVaultSemanticColors(
        income = EmeraldPositive,
        incomeContainer = EmeraldContainerLight,
        expense = CoralNegative,
        expenseContainer = CoralContainerLight,
        warning = AmberWarning,
        warningContainer = AmberContainerLight,
        heroGradientStart = FinNavy900,
        heroGradientEnd = FinNavy700,
        cardBorder = SlateBorder,
        isAmoled = false
    )
}

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue500,
    onPrimary = PureWhite,
    primaryContainer = ElectricBlue100,
    onPrimaryContainer = FinNavy900,
    secondary = FinNavy800,
    onSecondary = PureWhite,
    secondaryContainer = SlateSurfaceVariant,
    onSecondaryContainer = FinNavy900,
    tertiary = EmeraldPositive,
    onTertiary = PureWhite,
    tertiaryContainer = EmeraldContainerLight,
    onTertiaryContainer = EmeraldContainerDark,
    background = SlateBackground,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = SlateBorder,
    outlineVariant = SlateBorder,
    error = CoralNegative,
    onError = PureWhite,
    errorContainer = CoralContainerLight,
    onErrorContainer = CoralContainerDark
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue400,
    onPrimary = FinNavy950,
    primaryContainer = FinNavy700,
    onPrimaryContainer = ElectricBlue100,
    secondary = ElectricBlue200,
    onSecondary = FinNavy950,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = EmeraldPositiveLight,
    onTertiary = FinNavy950,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = EmeraldContainerLight,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = CoralNegativeLight,
    onError = FinNavy950,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralContainerLight
)

private val AmoledColorScheme = darkColorScheme(
    primary = ElectricBlue400,
    onPrimary = AmoledBlack,
    primaryContainer = FinNavy800,
    onPrimaryContainer = ElectricBlue100,
    secondary = ElectricBlue200,
    onSecondary = AmoledBlack,
    secondaryContainer = AmoledSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = EmeraldPositiveLight,
    onTertiary = AmoledBlack,
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = EmeraldContainerLight,
    background = AmoledBlack,
    onBackground = DarkTextPrimary,
    surface = AmoledSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = AmoledSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = AmoledBorder,
    outlineVariant = AmoledBorder,
    error = CoralNegativeLight,
    onError = AmoledBlack,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralContainerLight
)

@Composable
fun FinVaultTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }
    val isAmoled = themeMode == AppThemeMode.AMOLED

    val colorScheme = when {
        dynamicColor && !isAmoled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isAmoled -> AmoledColorScheme
        useDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = when {
        isAmoled -> FinVaultSemanticColors(
            income = EmeraldPositiveLight,
            incomeContainer = EmeraldContainerDark,
            expense = CoralNegativeLight,
            expenseContainer = CoralContainerDark,
            warning = AmberWarningLight,
            warningContainer = AmberContainerDark,
            heroGradientStart = FinNavy950,
            heroGradientEnd = FinNavy800,
            cardBorder = AmoledBorder,
            isAmoled = true
        )
        useDark -> FinVaultSemanticColors(
            income = EmeraldPositiveLight,
            incomeContainer = EmeraldContainerDark,
            expense = CoralNegativeLight,
            expenseContainer = CoralContainerDark,
            warning = AmberWarningLight,
            warningContainer = AmberContainerDark,
            heroGradientStart = FinNavy900,
            heroGradientEnd = FinNavy700,
            cardBorder = DarkBorder,
            isAmoled = false
        )
        else -> FinVaultSemanticColors(
            income = EmeraldPositive,
            incomeContainer = EmeraldContainerLight,
            expense = CoralNegative,
            expenseContainer = CoralContainerLight,
            warning = AmberWarning,
            warningContainer = AmberContainerLight,
            heroGradientStart = FinNavy900,
            heroGradientEnd = FinNavy600,
            cardBorder = SlateBorder,
            isAmoled = false
        )
    }

    CompositionLocalProvider(LocalFinVaultColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinVaultTypography,
            shapes = FinVaultShapes,
            content = content
        )
    }
}
