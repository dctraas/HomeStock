package com.dtraas.homestock.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A dashed rounded-rect outline — Compose has no built-in dashed border, so this draws one
 *  directly via a dash [PathEffect] on a round-rect stroke. Shared across every "empty slot,
 *  tap/drag something in here" affordance in the app (Maaltijden's [EmptySlotAddButton], Recepten's
 *  Mijn-recepten "Recept toevoegen" tile) — used to be private to MealPlanScreen.kt, moved here
 *  once a second screen needed the exact same dashed style. */
fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f),
    )
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(cornerRadius.toPx()))
}
