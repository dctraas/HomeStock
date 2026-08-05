package com.dtraas.homestock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SageGreenPrimary,
    onPrimary = OnSageGreenPrimary,
    primaryContainer = SageGreenPrimaryContainer,
    onPrimaryContainer = OnSageGreenPrimaryContainer,
    secondary = CoralSecondary,
    onSecondary = OnCoralSecondary,
    secondaryContainer = CoralSecondaryContainer,
    onSecondaryContainer = OnCoralSecondaryContainer,
    tertiary = GoldTertiary,
    onTertiary = OnGoldTertiary,
    tertiaryContainer = GoldTertiaryContainer,
    onTertiaryContainer = OnGoldTertiaryContainer,
    background = LinenBackground,
    onBackground = LinenInk,
    surface = LinenBackground,
    onSurface = LinenInk,
    surfaceVariant = LinenSurfaceVariant,
    onSurfaceVariant = OnLinenSurfaceVariant,
    surfaceContainerLowest = LinenSurfaceContainerLowest,
    surfaceContainerLow = LinenSurfaceContainerLow,
    surfaceContainer = LinenSurfaceContainer,
    surfaceContainerHigh = LinenSurfaceContainerHigh,
    surfaceContainerHighest = LinenSurfaceContainerHighest,
    outline = LinenOutline,
    outlineVariant = LinenOutlineVariant,
    error = LinenError,
    onError = OnLinenError,
    errorContainer = LinenErrorContainer,
    onErrorContainer = OnLinenErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = SageGreenPrimaryDark,
    onPrimary = OnSageGreenPrimaryDark,
    primaryContainer = SageGreenPrimaryContainerDark,
    onPrimaryContainer = OnSageGreenPrimaryContainerDark,
    secondary = CoralSecondaryDark,
    onSecondary = OnCoralSecondaryDark,
    secondaryContainer = CoralSecondaryContainerDark,
    onSecondaryContainer = OnCoralSecondaryContainerDark,
    tertiary = GoldTertiaryDark,
    onTertiary = OnGoldTertiaryDark,
    tertiaryContainer = GoldTertiaryContainerDark,
    onTertiaryContainer = OnGoldTertiaryContainerDark,
    background = LinenBackgroundDark,
    onBackground = LinenInkDark,
    surface = LinenBackgroundDark,
    onSurface = LinenInkDark,
    surfaceVariant = LinenSurfaceVariantDark,
    onSurfaceVariant = OnLinenSurfaceVariantDark,
    surfaceContainerLowest = LinenSurfaceContainerLowestDark,
    surfaceContainerLow = LinenSurfaceContainerLowDark,
    surfaceContainer = LinenSurfaceContainerDark,
    surfaceContainerHigh = LinenSurfaceContainerHighDark,
    surfaceContainerHighest = LinenSurfaceContainerHighestDark,
    outline = LinenOutlineDark,
    outlineVariant = LinenOutlineVariantDark,
    error = LinenErrorDark,
    onError = OnLinenErrorDark,
    errorContainer = LinenErrorContainerDark,
    onErrorContainer = OnLinenErrorContainerDark,
)

/**
 * [dynamicColor] defaults to false: this app has a deliberately designed
 * "Keukenlinnen" palette, and letting Android 12+ override it with
 * wallpaper-based colors would undermine that. Callers can still opt in.
 */
@Composable
fun HomeStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HomeStockTypography,
        shapes = HomeStockShapes,
        content = content,
    )
}
