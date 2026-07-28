package com.dtraas.boodschapbeheer.ui.theme

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
    primary = GreenPrimary,
    onPrimary = OnGreenPrimary,
    primaryContainer = GreenPrimaryContainer,
    onPrimaryContainer = OnGreenPrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = OnTerracottaSecondary,
    secondaryContainer = TerracottaSecondaryContainer,
    onSecondaryContainer = OnTerracottaSecondaryContainer,
    tertiary = BlueberryTertiary,
    onTertiary = OnBlueberryTertiary,
    tertiaryContainer = BlueberryTertiaryContainer,
    onTertiaryContainer = OnBlueberryTertiaryContainer,
    background = MarketBackground,
    surface = MarketBackground,
    surfaceVariant = MarketSurfaceVariant,
    onSurfaceVariant = OnMarketSurfaceVariant,
    outline = MarketOutline,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnGreenPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = OnGreenPrimaryContainerDark,
    secondary = TerracottaSecondaryDark,
    onSecondary = OnTerracottaSecondaryDark,
    secondaryContainer = TerracottaSecondaryContainerDark,
    onSecondaryContainer = OnTerracottaSecondaryContainerDark,
    tertiary = BlueberryTertiaryDark,
    onTertiary = OnBlueberryTertiaryDark,
    tertiaryContainer = BlueberryTertiaryContainerDark,
    onTertiaryContainer = OnBlueberryTertiaryContainerDark,
    background = MarketBackgroundDark,
    surface = MarketBackgroundDark,
    surfaceVariant = MarketSurfaceVariantDark,
    onSurfaceVariant = OnMarketSurfaceVariantDark,
    outline = MarketOutlineDark,
)

/**
 * [dynamicColor] defaults to false: this app has a deliberately designed
 * "fresh market" palette, and letting Android 12+ override it with
 * wallpaper-based colors would undermine that. Callers can still opt in.
 */
@Composable
fun BoodschapBeheerTheme(
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
        typography = BoodschapBeheerTypography,
        content = content,
    )
}
