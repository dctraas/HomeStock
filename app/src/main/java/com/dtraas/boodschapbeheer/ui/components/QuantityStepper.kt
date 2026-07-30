package com.dtraas.boodschapbeheer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtraas.boodschapbeheer.R

@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    minQuantity: Int = 0,
    dense: Boolean = false,
) {
    val buttonSize = if (dense) 32.dp else 48.dp
    val iconSize = if (dense) 16.dp else 24.dp
    val textStyle = if (dense) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
    val textWidth = if (dense) 20.dp else 32.dp

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onDecrease, enabled = quantity > minQuantity, modifier = Modifier.size(buttonSize)) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.quantity_decrease_cd),
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = quantity.toString(),
            style = textStyle,
            modifier = Modifier.width(textWidth).padding(horizontal = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onIncrease, modifier = Modifier.size(buttonSize)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.quantity_increase_cd),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
