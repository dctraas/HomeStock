package com.dtraas.boodschapbeheer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.model.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreDropdown(
    selected: Store,
    onSelected: (Store) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.store_dropdown_label),
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            value = stringResource(selected.displayNameRes),
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Store.entries.sortedBy { it.sortOrder }.forEach { store ->
                DropdownMenuItem(
                    text = { Text(stringResource(store.displayNameRes)) },
                    leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                    onClick = {
                        onSelected(store)
                        expanded = false
                    },
                )
            }
        }
    }
}
