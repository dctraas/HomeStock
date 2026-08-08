package com.dtraas.homestock.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.dtraas.homestock.data.model.Category

/**
 * A distinct accent color per category — "aisle" coding, the way a real supermarket's
 * signage tells you which section you're in before you read a single label. Used to
 * tint a product's photo badge instead of every product sharing the same generic
 * container color, so a list or grid can be scanned by color, not just by name.
 */
val Category.tint: Color
    get() = when (this) {
        Category.ZUIVEL -> Color(0xFFC99A2E)
        Category.GROENTE_FRUIT -> Color(0xFF3F6B4A)
        Category.VLEES_VIS -> Color(0xFFC24B3F)
        Category.BROOD_BAKKERIJ -> Color(0xFFB9813F)
        Category.VOORRAADKAST -> Color(0xFFA97D2E)
        Category.DIEPVRIES -> Color(0xFF4C86A8)
        Category.DRANKEN -> Color(0xFF7C5C99)
        Category.SNOEP_SNACKS -> Color(0xFFC25C82)
        Category.HUISHOUDEN -> Color(0xFF5B7A8C)
        Category.VERZORGING -> Color(0xFF8C7AA8)
        Category.OVERIG -> Color(0xFF7A7566)
    }

/**
 * Soft, theme-aware background for a product image/badge: [tint] blended into the
 * current surfaceContainerHigh rather than used at full strength, so it reads as a
 * gentle wash of color — not a solid brand-colored block — and automatically adapts
 * between light and dark (surfaceContainerHigh already does).
 */
val Category.tintContainer: Color
    @Composable get() = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, tint, 0.32f)
