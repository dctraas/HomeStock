package com.dtraas.homestock.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.MeasurementUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 0,
    dense: Boolean = false,
    // A full-width pill (surfaceContainerHigh background, fully rounded) with − pinned left,
    // quantity centered and + pinned right — for a row where the stepper is the only thing in
    // it (e.g. the Voorraad grid tile), rather than the default hug-content row meant to sit
    // beside other controls.
    pill: Boolean = false,
    // Lets callers show "500g"/"1L"/"6 stuks" instead of the bare number; see [formatQuantityWithUnit].
    displayText: String = quantity.toString(),
) {
    val buttonSize = if (dense) 32.dp else 48.dp
    val iconSize = if (dense) 16.dp else 24.dp
    val textStyle = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
    val textWidth = if (dense) 36.dp else 48.dp

    if (pill) {
        // A Box with the three parts pinned via .align(...) instead of a Row relying on
        // Arrangement.SpaceBetween — SpaceBetween only reads as centered when both side items
        // happen to be exactly the same width, which quietly broke ("niet mooi uitgelijnd")
        // whenever the − button's width shifted relative to the + button (e.g. a disabled-state
        // ripple/ripple-bounds difference at minQuantity). Anchoring each part to its own edge
        // of the pill guarantees the count sits dead-center and −/+ sit flush on the pill's own
        // edges, regardless of anything about the buttons' own layout.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 6.dp)
                .height(buttonSize),
        ) {
            RepeatingIconButton(
                onClick = onDecrease,
                enabled = quantity > minQuantity,
                modifier = Modifier.align(Alignment.CenterStart).size(buttonSize),
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.quantity_decrease_cd),
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = displayText,
                style = textStyle,
                modifier = Modifier.align(Alignment.Center),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
            RepeatingIconButton(
                onClick = onIncrease,
                enabled = true,
                modifier = Modifier.align(Alignment.CenterEnd).size(buttonSize),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.quantity_increase_cd),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatingIconButton(onClick = onDecrease, enabled = quantity > minQuantity, modifier = Modifier.size(buttonSize)) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.quantity_decrease_cd),
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                text = displayText,
                style = textStyle,
                modifier = Modifier.width(textWidth).padding(horizontal = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
            )
            RepeatingIconButton(onClick = onIncrease, enabled = true, modifier = Modifier.size(buttonSize)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.quantity_increase_cd),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/**
 * A small tappable icon button that also repeats [onClick] while held down, for quickly
 * running a quantity up or down instead of tapping one-by-one. Observes the button's own
 * press state via its [MutableInteractionSource] rather than adding a second, competing touch
 * handler — a plain tap releases well before the initial repeat delay elapses, so it
 * still results in exactly the one click the underlying `clickable` already fires on its own.
 *
 * [collectLatest] does the cancellation bookkeeping for us: a Release/Cancel interaction
 * (or another Press) arriving while the block below is still waiting out the initial
 * delay, or mid-repeat, cancels that in-flight block before starting the next one.
 */
@Composable
private fun RepeatingIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Press) {
                delay(450)
                while (true) {
                    currentOnClick()
                    delay(100)
                }
            }
        }
    }

    // A plain clickable Box instead of Material3's IconButton: IconButton enforces its own
    // built-in minimum touch-target size internally, which silently overrides a smaller
    // explicit .size() passed in from the caller — the dense stepper variant asks for a 32dp
    // button, but IconButton kept rendering (and reserving Row space for) its own larger
    // default regardless, throwing off centering between the "-", count and "+" — the
    // "niet gelijkmatig uitgelijnd" complaint. A custom Box gives this exact control over its
    // own footprint instead of fighting IconButton's internal sizing.
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentColor = if (enabled) {
            LocalContentColor.current
        } else {
            LocalContentColor.current.copy(alpha = 0.38f)
        }
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

/** Formats e.g. 500+GRAM as "500g", 1+LITER as "1L", 6+STUKS as "6 stuks". */
@Composable
fun formatQuantityWithUnit(quantity: Int, unit: MeasurementUnit): String {
    val label = stringResource(unit.shortLabelRes)
    return if (unit.spaceBeforeLabel) "$quantity $label" else "$quantity$label"
}
