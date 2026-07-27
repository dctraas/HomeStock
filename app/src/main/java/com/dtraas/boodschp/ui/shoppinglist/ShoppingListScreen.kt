package com.dtraas.boodschp.ui.shoppinglist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.boodschp.BoodschpApplication
import com.dtraas.boodschp.data.local.entity.ShoppingListItemEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.data.model.Store
import com.dtraas.boodschp.ui.components.CategoryDropdown
import com.dtraas.boodschp.ui.components.QuantityStepper
import com.dtraas.boodschp.ui.components.SearchField
import com.dtraas.boodschp.ui.components.StoreDropdown
import com.dtraas.boodschp.ui.components.icon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen() {
    val application = LocalContext.current.applicationContext as BoodschpApplication
    val viewModel: ShoppingListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShoppingListViewModel(application.container.shoppingListRepository) }
        },
    )
    val groupedByStore by viewModel.groupedByStore.collectAsState()
    val searchQuery by viewModel.searchQueryState.collectAsState()
    val hasCheckedItems = groupedByStore.values.flatten().any { it.isChecked }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Boodschappenlijst") },
                actions = {
                    if (hasCheckedItems) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = "Zoek in boodschappenlijst…",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (groupedByStore.isEmpty()) {
                EmptyShoppingList(
                    isFiltered = searchQuery.isNotBlank(),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    groupedByStore.forEach { (store, itemsInStore) ->
                        stickyHeader {
                            StoreHeader(store, itemCount = itemsInStore.size)
                        }
                        items(itemsInStore, key = { it.id }) { item ->
                            ShoppingListRow(
                                item = item,
                                onCheckedChange = { checked -> viewModel.setChecked(item.id, checked) },
                                onClick = { editingItem = item },
                                onIncrease = { viewModel.setQuantity(item.id, item.quantity + 1) },
                                onDecrease = { viewModel.setQuantity(item.id, item.quantity - 1) },
                                onDelete = {
                                    viewModel.removeItem(item.id)
                                    coroutineScope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "${item.name} verwijderd",
                                            actionLabel = "Ongedaan maken",
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restoreItem(item)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            ItemFormDialog(
                title = "Item toevoegen",
                confirmLabel = "Toevoegen",
                onDismiss = { showAddDialog = false },
                onConfirm = { name, category, store, quantity ->
                    viewModel.addItem(name, category, store, quantity)
                    showAddDialog = false
                },
            )
        }

        editingItem?.let { item ->
            ItemFormDialog(
                title = "Item bewerken",
                confirmLabel = "Opslaan",
                initialName = item.name,
                initialCategory = Category.fromStorageKey(item.category),
                initialStore = Store.fromStorageKey(item.store),
                initialQuantity = item.quantity,
                imageUrl = item.imageUrl,
                onDismiss = { editingItem = null },
                onConfirm = { name, category, store, quantity ->
                    viewModel.updateItem(
                        item.copy(
                            name = name,
                            category = category.storageKey,
                            store = store.storageKey,
                            quantity = quantity,
                        )
                    )
                    editingItem = null
                },
            )
        }
    }
}

@Composable
private fun StoreHeader(store: Store, itemCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = store.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = itemCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShoppingListRow(
    item: ShoppingListItemEntity,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit,
) {
    val category = Category.fromStorageKey(item.category)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
            ShoppingItemAvatar(item, category)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    QuantityStepper(
                        quantity = item.quantity,
                        onDecrease = onDecrease,
                        onIncrease = onIncrease,
                        minQuantity = 1,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Verwijderen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemAvatar(item: ShoppingListItemEntity, category: Category) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        if (item.imageUrl != null) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyShoppingList(isFiltered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isFiltered) Icons.Filled.Search else Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = if (isFiltered) "Geen items gevonden." else "Je boodschappenlijst is leeg.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun ItemFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialCategory: Category = Category.OVERIG,
    initialStore: Store = Store.GEEN,
    initialQuantity: Int = 1,
    imageUrl: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: Category, store: Store, quantity: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var store by remember { mutableStateOf(initialStore) }
    var quantity by remember { mutableIntStateOf(initialQuantity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ItemFormAvatar(imageUrl = imageUrl, category = category)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                    StoreDropdown(
                        selected = store,
                        onSelected = { store = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Aantal", style = MaterialTheme.typography.bodyLarge)
                        QuantityStepper(
                            quantity = quantity,
                            onDecrease = { quantity = (quantity - 1).coerceAtLeast(1) },
                            onIncrease = { quantity += 1 },
                            minQuantity = 1,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, category, store, quantity) },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuleren") }
        },
    )
}

@Composable
private fun ItemFormAvatar(imageUrl: String?, category: Category) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(88.dp),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
