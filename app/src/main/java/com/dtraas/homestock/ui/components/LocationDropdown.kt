package com.dtraas.homestock.ui.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Location

/**
 * Fixed 3-option dropdown for a product's [Location] (see that enum's doc for why this replaced
 * a free-text field). [selected] is the raw stored value — null (no location set), one of
 * [Location.storageKey], or a legacy free-text string from before this change, shown as typed
 * via [Location.fromStorageKey] returning null and falling back to the raw value rather than
 * silently blanking it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDropdown(
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.product_detail_field_location),
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = selected
        ?.let { raw -> Location.fromStorageKey(raw)?.let { stringResource(it.labelRes) } ?: raw }
        ?: stringResource(R.string.inventory_no_location_label)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            value = displayValue,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inventory_no_location_label)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            Location.entries.forEach { location ->
                DropdownMenuItem(
                    text = { Text(stringResource(location.labelRes)) },
                    onClick = {
                        onSelected(location.storageKey)
                        expanded = false
                    },
                )
            }
        }
    }
}
