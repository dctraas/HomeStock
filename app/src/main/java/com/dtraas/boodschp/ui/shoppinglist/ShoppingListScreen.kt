package com.dtraas.boodschp.ui.shoppinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.boodschp.BoodschpApplication
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.ui.components.CategoryDropdown
import com.dtraas.boodschp.ui.components.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen() {
    val application = LocalContext.current.applicationContext as BoodschpApplication
    val viewModel: ShoppingListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShoppingListViewModel(application.container.shoppingListRepository) }
        },
    )
    val shoppingItems by viewModel.shoppingList.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Boodschappenlijst") },
                actions = {
                    if (shoppingItems.any { it.isChecked }) {
                        IconButton(onClick = viewModel::clearChecked) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Wis afgevinkte items")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Item toevoegen")
            }
        },
    ) { padding ->
        if (shoppingItems.isEmpty()) {
            EmptyShoppingList(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(shoppingItems, key = { it.id }) { item ->
                    ShoppingListRow(
                        item = item,
                        onCheckedChange = { checked -> viewModel.setChecked(item.id, checked) },
                        onDelete = { viewModel.removeItem(item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }

        if (showAddDialog) {
            AddItemDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, category, quantity ->
                    viewModel.addItem(name, category, quantity)
                    showAddDialog = false
                },
            )
        }
    }
}

@Composable
private fun ShoppingListRow(
    item: ShoppingListItemEntity,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        },
        headlineContent = {
            Text(
                text = item.name,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = {
            Text("${Category.fromStorageKey(item.category).displayName} · ${item.quantity}x")
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Verwijderen")
            }
        },
    )
}

@Composable
private fun EmptyShoppingList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Je boodschappenlijst is leeg.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: Category, quantity: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OVERIG) }
    var quantity by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Item toevoegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam") },
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryDropdown(
                    selected = category,
                    onSelected = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aantal")
                    QuantityStepper(
                        quantity = quantity,
                        onDecrease = { quantity = (quantity - 1).coerceAtLeast(1) },
                        onIncrease = { quantity += 1 },
                        minQuantity = 1,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, category, quantity) },
                enabled = name.isNotBlank(),
            ) {
                Text("Toevoegen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuleren") }
        },
    )
}
