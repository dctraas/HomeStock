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
// The page background itself is plain white (see the Dark variant below) — an explicit
// choice to keep the ground neutral, with the sage/coral/gold palette carried by
// primary/secondary/tertiary (icons, badges, buttons). Elevated surfaces (top app bars,
// cards) step away from that ground through the surfaceContainer* ramp just below: a
// warm, neutral grey — not sage-tinted — so a card or bar always reads as a distinct
// layer instead of blending into the page.
val LinenBackground = Color(0xFFFFFFFF)
val LinenSurfaceVariant = Color(0xFFCBD6C0)
val OnLinenSurfaceVariant = Color(0xFF4A5B4D)
val LinenOutline = Color(0xFF8B9686)
val LinenOutlineVariant = Color(0xFFCBD6C0)

// Ascending "distance" from the white page background — used for e.g. the top app bar
// (surfaceContainer) and cards (surfaceContainerHigh), each meant to read as clearly
// separate from the page without shouting.
val LinenSurfaceContainerLowest = Color(0xFFFFFFFF)
val LinenSurfaceContainerLow = Color(0xFFF9F8F5)
val LinenSurfaceContainer = Color(0xFFF3F1EC)
val LinenSurfaceContainerHigh = Color(0xFFECE9E1)
val LinenSurfaceContainerHighest = Color(0xFFE4E0D5)

// Top app bar container — its own dedicated tone rather than a reuse of
// surfaceContainer above: full-strength sage green (this app's actual primary color,
// not a pale tint of it) so the title bar reads as a deliberate, confident band of
// color instead of a faint wash. Cards stay neutral; this is the one elevated surface
// that gets to carry the palette. Also pushed to the system status bar itself (see
// MainActivity) so the color reaches the physical top edge of the screen, not just
// the app bar's own bounds below it. Being this saturated, it needs a light (not the
// usual ink-dark) title/icon color for contrast — see OnTopAppBarContainer.
val TopAppBarContainer = Color(0xFF3F6B4A)
val OnTopAppBarContainer = Color(0xFFFFFFFF)

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

// Mirror of the light ramp above, but climbing away from black instead of white — still
// subtle (this stays a dark theme), just no longer indistinguishable from the page.
val LinenSurfaceContainerLowestDark = Color(0xFF000000)
val LinenSurfaceContainerLowDark = Color(0xFF0B0C0A)
val LinenSurfaceContainerDark = Color(0xFF141510)
val LinenSurfaceContainerHighDark = Color(0xFF1C1D17)
val LinenSurfaceContainerHighestDark = Color(0xFF25261E)

// Dark counterpart of TopAppBarContainer above — a deep, saturated forest green
// (rather than the barely-there desaturated tone this used to be) so the bar stays
// just as bold a splash of color against the near-black page in dark theme.
val TopAppBarContainerDark = Color(0xFF25422E)
val OnTopAppBarContainerDark = Color(0xFFDCEFDD)

val LinenErrorDark = Color(0xFFF2A08F)
val OnLinenErrorDark = Color(0xFF4A130A)
val LinenErrorContainerDark = Color(0xFF6B2216)
val OnLinenErrorContainerDark = Color(0xFFF9D6CE)
