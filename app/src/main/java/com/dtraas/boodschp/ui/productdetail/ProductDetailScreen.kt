package com.dtraas.boodschp.ui.productdetail

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.boodschp.BoodschpApplication
import com.dtraas.boodschp.data.local.entity.ScanHistoryEntity
import com.dtraas.boodschp.data.model.Category
import com.dtraas.boodschp.ui.components.QuantityStepper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    barcode: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as BoodschpApplication
    val viewModel: ProductDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProductDetailViewModel(
                    barcode = barcode,
                    productRepository = application.container.productRepository,
                    inventoryRepository = application.container.inventoryRepository,
                    shoppingListRepository = application.container.shoppingListRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val stillInInventory = uiState.quantityInInventory != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.product?.name ?: "Product") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val product = uiState.product
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    product?.imageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = product.name,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                    product?.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        text = Category.fromStorageKey(product?.category).displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (stillInInventory) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Op voorraad", style = MaterialTheme.typography.titleMedium)
                            QuantityStepper(
                                quantity = uiState.quantityInInventory ?: 0,
                                onDecrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 1) - 1) },
                                onIncrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 0) + 1) },
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.removeFromInventory()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Text(" Verwijderen uit voorraad", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::addToShoppingList,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Toevoegen aan boodschappenlijst")
                    }

                    Text(
                        text = "Geschiedenis (${uiState.scanCount}x gescand)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                }
            }

            if (uiState.history.isEmpty()) {
                item {
                    Text(
                        text = "Nog geen scans geregistreerd.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(uiState.history, key = { it.id }) { entry ->
                    HistoryRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

private val historyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("nl-NL"))

@Composable
private fun HistoryRow(entry: ScanHistoryEntity) {
    val formatted = remember(entry.scannedAt) {
        historyFormatter.format(Instant.ofEpochMilli(entry.scannedAt).atZone(ZoneId.systemDefault()))
    }
    ListItem(
        headlineContent = { Text(formatted) },
        supportingContent = {
            val sign = if (entry.quantityDelta >= 0) "+" else ""
            Text("$sign${entry.quantityDelta} stuk(s) gescand")
        },
    )
}
