package com.dtraas.homestock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

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

// Not part of Material3's ColorScheme — the top app bar deliberately uses its own
// dedicated tone (see TopAppBarContainer/TopAppBarContainerDark in Color.kt) rather
// than one of the neutral surfaceContainer steps, so it reads as a splash of the
// app's own color instead of another shade of grey. Exposed via a CompositionLocal
// so both [HomeStockTopAppBar] and MainActivity (to also tint the system status bar
// the exact same color — see its usage there) can read the same resolved value.
val LocalTopAppBarContainerColor = compositionLocalOf { TopAppBarContainer }

// Bottom stop of the gradient headers built on top of LocalTopAppBarContainerColor (see
// TopAppBarContainerGradientEnd/-Dark in Color.kt) — resolved alongside it so every screen's
// header gradient starts at exactly the color MainActivity paints onto the system status bar,
// in both themes. Before this was split out, every header hardcoded the light-mode constants
// directly, which left dark theme's gradient visibly lighter than the status bar strip above
// it — two different greens that read as a seam instead of one continuous band.
val LocalTopAppBarContainerGradientEnd = compositionLocalOf { TopAppBarContainerGradientEnd }

// Title/icon color to pair with LocalTopAppBarContainerColor above. The bar is now a
// full-strength, saturated sage rather than a pale tint, so it needs a light content
// color for contrast rather than MaterialTheme's usual ink-dark onSurface.
val LocalTopAppBarContentColor = compositionLocalOf { OnTopAppBarContainer }

/** "Groot lettertype" scale factor — a modest, layout-safe bump (Android's own system-wide
 *  large-text setting uses a similar range) rather than something aggressive enough to start
 *  overflowing this app's many fixed-height cards/rows. */
private const val LARGE_TEXT_SCALE = 1.15f

/**
 * [dynamicColor] defaults to false: this app has a deliberately designed
 * "Keukenlinnen" palette, and letting Android 12+ override it with
 * wallpaper-based colors would undermine that. Callers can still opt in.
 *
 * [largeText]/[highContrast] are the two toegankelijkheid toggles in Instellingen (see
 * ThemePreferences) — both are additive tweaks on top of the normal light/dark scheme rather
 * than a wholly separate theme, so the app's own palette/typography identity stays recognizable
 * while still meaningfully improving legibility for low-vision users.
 */
@Composable
fun HomeStockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    largeText: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    // Boosts exactly the colors that carry text/boundary legibility rather than restyling the
    // whole palette — pure black-on-linen (light) / white-on-ink (dark) for body text, and the
    // same strong tone for outlines so card/field boundaries read clearly too. Primary/secondary/
    // tertiary and their containers are left alone: this is a contrast fix, not a re-theme.
    val colorScheme = if (highContrast) {
        val ink = if (darkTheme) Color.White else Color.Black
        baseColorScheme.copy(
            onSurface = ink,
            onBackground = ink,
            onSurfaceVariant = ink,
            outline = ink,
            outlineVariant = ink.copy(alpha = 0.6f),
        )
    } else {
        baseColorScheme
    }
    val typography = if (largeText) scaledTypography(HomeStockTypography, LARGE_TEXT_SCALE) else HomeStockTypography
    // Dynamic color has no equivalent of our custom top app bar tone, so it falls back to
    // the wallpaper-derived surfaceContainer instead of the fixed sage tint below.
    val topAppBarContainerColor = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> colorScheme.surfaceContainer
        darkTheme -> TopAppBarContainerDark
        else -> TopAppBarContainer
    }
    // Dynamic color has no gradient counterpart either — same flat surfaceContainer as
    // topAppBarContainerColor above, so a dynamic-color header is a flat fill rather than a
    // gradient rather than risk a wallpaper-derived tone that doesn't darken sensibly.
    val topAppBarContainerGradientEnd = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> colorScheme.surfaceContainer
        darkTheme -> TopAppBarContainerGradientEndDark
        else -> TopAppBarContainerGradientEnd
    }
    // Falls back to the theme's own onSurfaceVariant under dynamic color, since a
    // wallpaper-derived surfaceContainer can land anywhere on the light/dark spectrum
    // and the fixed light OnTopAppBarContainer would not reliably contrast against it.
    val topAppBarContentColor = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> colorScheme.onSurfaceVariant
        darkTheme -> OnTopAppBarContainerDark
        else -> OnTopAppBarContainer
    }

    CompositionLocalProvider(
        LocalTopAppBarContainerColor provides topAppBarContainerColor,
        LocalTopAppBarContainerGradientEnd provides topAppBarContainerGradientEnd,
        LocalTopAppBarContentColor provides topAppBarContentColor,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = HomeStockShapes,
            content = content,
        )
    }
}

private fun TextStyle.scaled(factor: Float): TextStyle =
    copy(fontSize = (fontSize.value * factor).sp, lineHeight = (lineHeight.value * factor).sp)

/** Scales every named style in [base] by [factor] — used for the "groot lettertype" toggle
 *  rather than relying on the system font-scale setting alone, so it works the same for every
 *  user regardless of their device-wide accessibility settings. */
private fun scaledTypography(base: Typography, factor: Float): Typography = base.copy(
    displayLarge = base.displayLarge.scaled(factor),
    displayMedium = base.displayMedium.scaled(factor),
    displaySmall = base.displaySmall.scaled(factor),
    headlineLarge = base.headlineLarge.scaled(factor),
    headlineMedium = base.headlineMedium.scaled(factor),
    headlineSmall = base.headlineSmall.scaled(factor),
    titleLarge = base.titleLarge.scaled(factor),
    titleMedium = base.titleMedium.scaled(factor),
    titleSmall = base.titleSmall.scaled(factor),
    bodyLarge = base.bodyLarge.scaled(factor),
    bodyMedium = base.bodyMedium.scaled(factor),
    bodySmall = base.bodySmall.scaled(factor),
    labelLarge = base.labelLarge.scaled(factor),
    labelMedium = base.labelMedium.scaled(factor),
    labelSmall = base.labelSmall.scaled(factor),
)
