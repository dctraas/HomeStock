package com.dtraas.boodschapbeheer.ui.theme

import androidx.compose.ui.graphics.Color

// "Keukenlinnen" (kitchen linen) palette: a muted sage ground with cards that sit a
// shade lighter, like ceramic tiles or folded linen — warm coral for actions/accents,
// forest green for primary state, muted gold as a third accent for pantry-staple
// category coding. Every surfaceContainer* tone below is set explicitly: left at
// Material's defaults, cards silently fall back to stock grey regardless of the rest
// of the palette, which is a large part of why the app read as generic before.

val SageGreenPrimary = Color(0xFF3F6B4A)
val OnSageGreenPrimary = Color(0xFFFFFFFF)
val SageGreenPrimaryContainer = Color(0xFFC6D9C3)
val OnSageGreenPrimaryContainer = Color(0xFF17301D)

val CoralSecondary = Color(0xFFD9694E)
val OnCoralSecondary = Color(0xFFFFFFFF)
val CoralSecondaryContainer = Color(0xFFF6D9CD)
val OnCoralSecondaryContainer = Color(0xFF5C2415)

val GoldTertiary = Color(0xFFA97D2E)
val OnGoldTertiary = Color(0xFFFFFFFF)
val GoldTertiaryContainer = Color(0xFFEFE0BE)
val OnGoldTertiaryContainer = Color(0xFF3D2C08)

val LinenInk = Color(0xFF2E3B31)
// Page and card backgrounds are plain white/black (see the Dark variants below) rather
// than tinted — an explicit choice to keep every surface neutral, with the sage/coral/
// gold palette carried entirely by primary/secondary/tertiary (icons, badges, buttons).
val LinenBackground = Color(0xFFFFFFFF)
val LinenSurfaceVariant = Color(0xFFCBD6C0)
val OnLinenSurfaceVariant = Color(0xFF4A5B4D)
val LinenOutline = Color(0xFF8B9686)
val LinenOutlineVariant = Color(0xFFCBD6C0)

val LinenSurfaceContainerLowest = Color(0xFFFFFFFF)
val LinenSurfaceContainerLow = Color(0xFFFFFFFF)
val LinenSurfaceContainer = Color(0xFFFFFFFF)
val LinenSurfaceContainerHigh = Color(0xFFFFFFFF)
val LinenSurfaceContainerHighest = Color(0xFFFFFFFF)

val LinenError = Color(0xFFB3392A)
val OnLinenError = Color(0xFFFFFFFF)
val LinenErrorContainer = Color(0xFFF6D3CC)
val OnLinenErrorContainer = Color(0xFF410E06)

val SageGreenPrimaryDark = Color(0xFF9BC79E)
val OnSageGreenPrimaryDark = Color(0xFF12321B)
val SageGreenPrimaryContainerDark = Color(0xFF2C4A34)
val OnSageGreenPrimaryContainerDark = Color(0xFFBEE0BF)

val CoralSecondaryDark = Color(0xFFF0A88F)
val OnCoralSecondaryDark = Color(0xFF4A200F)
val CoralSecondaryContainerDark = Color(0xFF6B3521)
val OnCoralSecondaryContainerDark = Color(0xFFFBD9CB)

val GoldTertiaryDark = Color(0xFFDCB966)
val OnGoldTertiaryDark = Color(0xFF3D2C08)
val GoldTertiaryContainerDark = Color(0xFF5A431A)
val OnGoldTertiaryContainerDark = Color(0xFFF3E2B8)

val LinenInkDark = Color(0xFFE7ECE0)
val LinenBackgroundDark = Color(0xFF000000)
val LinenSurfaceVariantDark = Color(0xFF2B3327)
val OnLinenSurfaceVariantDark = Color(0xFFC4CFBD)
val LinenOutlineDark = Color(0xFF8B9686)
val LinenOutlineVariantDark = Color(0xFF3A4232)

val LinenSurfaceContainerLowestDark = Color(0xFF000000)
val LinenSurfaceContainerLowDark = Color(0xFF000000)
val LinenSurfaceContainerDark = Color(0xFF000000)
val LinenSurfaceContainerHighDark = Color(0xFF000000)
val LinenSurfaceContainerHighestDark = Color(0xFF000000)

val LinenErrorDark = Color(0xFFF2A08F)
val OnLinenErrorDark = Color(0xFF4A130A)
val LinenErrorContainerDark = Color(0xFF6B2216)
val OnLinenErrorContainerDark = Color(0xFFF9D6CE)
