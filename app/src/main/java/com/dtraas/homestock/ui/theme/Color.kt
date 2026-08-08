package com.dtraas.homestock.ui.theme

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
// The page background is a warm, faintly sage-biased "linen" ivory — not pure white.
// Pure white/black reads as unconsidered (the generic Material default), whereas a
// neutral with a slight hue lean toward the palette reads as chosen; it's also simply
// truer to the "Keukenlinnen" name. Elevated surfaces (top app bars, cards) step away
// from that ground through the surfaceContainer* ramp just below — still a neutral, not
// sage-tinted the way primary/secondary/tertiary are, so a card or bar always reads as
// a distinct layer instead of blending into the page.
val LinenBackground = Color(0xFFFBF7EE)
val LinenSurfaceVariant = Color(0xFFCBD6C0)
val OnLinenSurfaceVariant = Color(0xFF4A5B4D)
val LinenOutline = Color(0xFF8B9686)
val LinenOutlineVariant = Color(0xFFCBD6C0)

// Ascending "distance" from the linen page background — used for e.g. the top app bar
// (surfaceContainer) and cards (surfaceContainerHigh), each meant to read as clearly
// separate from the page without shouting.
val LinenSurfaceContainerLowest = Color(0xFFFBF7EE)
val LinenSurfaceContainerLow = Color(0xFFF7F3E4)
val LinenSurfaceContainer = Color(0xFFF2ECDC)
val LinenSurfaceContainerHigh = Color(0xFFEAE3CC)
val LinenSurfaceContainerHighest = Color(0xFFE3D9BE)

// Top app bar container — its own dedicated tone rather than a reuse of
// surfaceContainer above: a soft, fresh sage tint (this app's primary hue, greatly
// lightened) so the title bar reads as a deliberate splash of color rather than
// another shade of the linen ramp. Cards stay neutral; this is the one elevated
// surface that gets to carry the palette. Also pushed to the system status bar itself
// (see MainActivity) so the color reaches the physical top edge of the screen, not
// just the app bar's own bounds below it.
val TopAppBarContainer = Color(0xFFDCE9D3)

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
// Warm near-black rather than pure #000000, mirroring the light theme's linen ivory —
// same reasoning: chosen, not inherited.
val LinenBackgroundDark = Color(0xFF131209)
val LinenSurfaceVariantDark = Color(0xFF2B3327)
val OnLinenSurfaceVariantDark = Color(0xFFC4CFBD)
val LinenOutlineDark = Color(0xFF8B9686)
val LinenOutlineVariantDark = Color(0xFF3A4232)

// Mirror of the light ramp above, but climbing away from warm near-black instead of
// linen ivory — still subtle (this stays a dark theme), just no longer indistinguishable
// from the page.
val LinenSurfaceContainerLowestDark = Color(0xFF131209)
val LinenSurfaceContainerLowDark = Color(0xFF171509)
val LinenSurfaceContainerDark = Color(0xFF1B1911)
val LinenSurfaceContainerHighDark = Color(0xFF1E1C13)
val LinenSurfaceContainerHighestDark = Color(0xFF262316)

// Dark counterpart of TopAppBarContainer above — same idea, a dark, desaturated sage
// rather than a shade of the neutral surfaceContainer ramp.
val TopAppBarContainerDark = Color(0xFF192416)

val LinenErrorDark = Color(0xFFF2A08F)
val OnLinenErrorDark = Color(0xFF4A130A)
val LinenErrorContainerDark = Color(0xFF6B2216)
val OnLinenErrorContainerDark = Color(0xFFF9D6CE)
