package com.dtraas.boodschp.ui.scanresult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.boodschp.BoodschpApplication
import com.dtraas.boodschp.ui.components.CategoryDropdown
import com.dtraas.boodschp.ui.components.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    barcode: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as BoodschpApplication
    val viewModel: ScanResultViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ScanResultViewModel(
                    barcode = barcode,
                    productRepository = application.container.productRepository,
                    inventoryRepository = application.container.inventoryRepository,
                )
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedToInventory) {
        if (uiState.savedToInventory) onSaved()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Product toevoegen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Terug")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Productgegevens ophalen…", modifier = Modifier.padding(top = 16.dp))
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!uiState.wasFoundOnline) {
                    Text(
                        text = "Dit product is niet gevonden in de database. Vul de gegevens handmatig in.",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                uiState.imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = uiState.name,
                        modifier = Modifier
                            .size(96.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }

                Text(text = "Barcode: $barcode", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Naam") },
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.brand?.let { brand ->
                    Text(text = "Merk: $brand", style = MaterialTheme.typography.bodyMedium)
                }

                uiState.unit?.let { unit ->
                    Text(text = "Inhoud: $unit", style = MaterialTheme.typography.bodyMedium)
                }

                CategoryDropdown(
                    selected = uiState.category,
                    onSelected = viewModel::onCategoryChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column {
                    Text("Aantal")
                    QuantityStepper(
                        quantity = uiState.quantity,
                        onDecrease = { viewModel.onQuantityChange(uiState.quantity - 1) },
                        onIncrease = { viewModel.onQuantityChange(uiState.quantity + 1) },
                        minQuantity = 1,
                    )
                }

                Button(
                    onClick = viewModel::onConfirm,
                    enabled = uiState.name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Toevoegen aan voorraad")
                }
            }
        }
    }
}
