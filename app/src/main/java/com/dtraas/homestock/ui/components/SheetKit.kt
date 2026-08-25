package com.dtraas.homestock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact

/**
 * Shared chrome for every redesigned pop-up (see the 2026-08 dialog-to-bottom-sheet review):
 * 28dp-top-only corners and a 36×4dp drag handle in [MaterialTheme.colorScheme.outlineVariant],
 * rather than Material's own 36dp-all-corners default shape/40%-alpha handle. Content isn't
 * pre-wrapped in a Column here — a search-heavy sheet ([androidx.compose.foundation.lazy.LazyColumn])
 * needs different scroll/padding handling than a short form, so each call site owns that; use
 * [sheetContentModifier] for the common "scrollable Column, 20dp sides, 22dp bottom" case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeStockBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { HomeStockDragHandle() },
        content = { content() },
    )
}

@Composable
private fun HomeStockDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 16.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
    )
}

/** The standard content padding for a sheet whose body is one scrollable `Column`. */
val sheetContentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 22.dp)

/** Sheet title (Baloo 2 Bold 22sp) with an optional one-line explainer underneath — every
 *  redesigned sheet opens with this instead of the centered `AlertDialog` title it replaces. */
@Composable
fun SheetTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Uppercase section label (Nunito ExtraBold 11sp, wide letter-spacing) grouping the rows below
 *  it — e.g. "WEERGAVE", "MET PREMIUM". */
@Composable
fun SheetEyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outline) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp),
        color = color,
        modifier = modifier,
    )
}

/** The one full-width filled action every sheet ends on — 56dp tall, 28dp radius, replacing the
 *  old two-`TextButton`-in-the-corner pattern. [loading] swaps the label for a spinner without
 *  changing the button's size, so the sheet doesn't jump while an action is in flight. */
@Composable
fun SheetPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * "The row is the target" (cross-cutting rule #3 of the dialog review): an icon tile, a
 * title/subtitle pair, and an optional trailing control, the whole row clickable at once rather
 * than a label with a separate small `IconButton` beside it. Used for both plain rows (2c, 3c…)
 * and the ranked, differently-tinted action rows of 2a — [containerColor]/[borderColor]/
 * [iconTileColor]/[titleColor] cover both.
 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color? = null,
    iconTileColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    iconTileSize: Dp = 44.dp,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 22.dp,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable (RowScope.() -> Unit)? = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = SoftCardShapeCompact,
        color = containerColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(iconTileSize)
                    .background(iconTileColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = titleColor,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = subtitleColor,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            trailing?.let { trailingContent ->
                Row(verticalAlignment = Alignment.CenterVertically, content = trailingContent)
            }
        }
    }
}

/** A single suggestion/filter pill — the shape every chip row in the reviewed sheets uses
 *  (999dp radius, filled when selected). Thin wrapper over [androidx.compose.material3.FilterChip]
 *  so every sheet's chips look identical rather than each hand-rolling `Surface` + `Row`. */
@Composable
fun SheetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = leadingIcon,
        shape = CircleShape,
        modifier = modifier,
    )
}
