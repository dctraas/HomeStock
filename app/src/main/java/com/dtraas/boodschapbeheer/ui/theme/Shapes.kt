package com.dtraas.boodschapbeheer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Cards, sheets, dialogs — generously rounded, like a ceramic tile rather than a stock Material card. */
val SoftCardShape = RoundedCornerShape(22.dp)

/** Denser cards — grid tiles, activity/notification rows — same language, a touch tighter. */
val SoftCardShapeCompact = RoundedCornerShape(18.dp)

/** Product photos and other rectangular images inside cards. */
val SoftImageShape = RoundedCornerShape(18.dp)

/**
 * The "droplet" icon-badge shape used behind icons throughout the app (settings rows,
 * category badges, nav selection) — three full-round corners and one pinched corner,
 * like a drop of water, standing in for the generic filled circle Material defaults to.
 */
val SoftBadgeShape = RoundedCornerShape(
    topStartPercent = 50,
    topEndPercent = 50,
    bottomEndPercent = 50,
    bottomStartPercent = 20,
)

val BoodschapBeheerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = SoftCardShape,
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
