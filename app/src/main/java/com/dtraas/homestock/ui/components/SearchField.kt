package com.dtraas.homestock.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.theme.SoftCardShape

/** [dense] shrinks the field a bit (smaller text/icons — [OutlinedTextField] otherwise has no
 *  direct "smaller" knob, it just sizes itself to whatever content/icons it's given) — opt-in
 *  per call site rather than the default, since every other screen's search field is sized fine
 *  as-is. [onSearch], when given, switches the keyboard's Enter key to a search action that
 *  calls it — lets a call site skip a separate "confirm search" icon of its own (e.g. an
 *  expand-on-tap search bar with nothing else next to the field to press). Left null (the
 *  default) keeps every existing call site's plain Enter-key behavior unchanged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_placeholder_default),
    dense: Boolean = false,
    onSearch: (() -> Unit)? = null,
    // Left at the Material3 default everywhere except a colored header (e.g. Voorraad's/
    // Recepten's green gradient header), where a call site overrides it to a white pill with
    // dark content instead of the default outline styling, which would be unreadable there.
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val textStyle: TextStyle? = if (dense) MaterialTheme.typography.bodyMedium else null
    val iconSize = if (dense) Modifier.size(20.dp) else Modifier
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        // The placeholder is its own composable slot, not covered by singleLine below —
        // without an explicit line cap a long placeholder wraps and grows the field to two
        // visual lines the moment it's shown, instead of just ellipsizing.
        placeholder = {
            Text(
                placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = textStyle ?: LocalTextStyle.current,
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = iconSize) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear_cd), modifier = iconSize)
                }
            }
        },
        singleLine = true,
        shape = SoftCardShape,
        colors = colors,
        textStyle = textStyle ?: LocalTextStyle.current,
        keyboardOptions = if (onSearch != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = if (onSearch != null) KeyboardActions(onSearch = { onSearch() }) else KeyboardActions.Default,
    )
}
