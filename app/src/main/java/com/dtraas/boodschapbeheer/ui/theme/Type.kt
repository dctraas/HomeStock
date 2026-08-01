package com.dtraas.boodschapbeheer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.dtraas.boodschapbeheer.R

// Downloaded on-device via Play Services (see res/values/font_certs.xml) rather than
// bundled, so there's no binary font file to ship or keep in sync.
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

// Baloo 2: chunky and rounded, carries the "Keukenlinnen" identity in headings, titles
// and anything numeric (quantities, counts) — used with restraint, never for long text.
private val baloo2 = GoogleFont("Baloo 2")
private val DisplayFontFamily = FontFamily(
    Font(googleFont = baloo2, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = baloo2, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = baloo2, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = baloo2, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
)

// Nunito: soft-rounded but far more legible at small sizes — the body/label counterpart
// that keeps long text (descriptions, list rows) comfortable to read.
private val nunito = GoogleFont("Nunito")
private val BodyFontFamily = FontFamily(
    Font(googleFont = nunito, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = nunito, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = nunito, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = nunito, fontProvider = fontProvider, weight = FontWeight.Bold),
)

val BoodschapBeheerTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp),
)
