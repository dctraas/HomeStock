package com.dtraas.homestock.ui.scanresult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.ui.components.HomeStockTopAppBar
import com.dtraas.homestock.ui.components.CategoryDropdown
import com.dtraas.homestock.ui.components.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    barcode: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    // Batch-scannen: when reached mid-scan from the barcode camera, the confirm button says so
    // and onSaved (wired by the caller) returns straight to the camera instead of Voorraad.
    fromScan: Boolean = false,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
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
            HomeStockTopAppBar(
                title = { Text(stringResource(R.string.scan_result_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                Text(stringResource(R.string.scan_result_loading), modifier = Modifier.padding(top = 16.dp))
            }

            uiState.networkError -> NetworkErrorView(
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = viewModel::retry,
                onContinueManually = viewModel::continueManually,
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!uiState.wasFoundOnline) {
                    Text(
                        text = stringResource(R.string.scan_result_not_found),
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

                Text(text = stringResource(R.string.scan_result_barcode_format, barcode), style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.common_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.brand?.let { brand ->
                    Text(text = stringResource(R.string.scan_result_brand_format, brand), style = MaterialTheme.typography.bodyMedium)
                }

                uiState.unit?.let { unit ->
                    Text(text = stringResource(R.string.scan_result_unit_format, unit), style = MaterialTheme.typography.bodyMedium)
                }

                CategoryDropdown(
                    selected = uiState.category,
                    onSelected = viewModel::onCategoryChange,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column {
                    Text(stringResource(R.string.common_quantity))
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
                    Text(
                        stringResource(
                            if (fromScan) R.string.scan_result_confirm_continue else R.string.scan_result_confirm
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkErrorView(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    onContinueManually: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.scan_result_network_error_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.scan_result_network_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Text(stringResource(R.string.scan_result_retry))
        }
        TextButton(onClick = onContinueManually) {
            Text(stringResource(R.string.scan_result_continue_manually))
        }
    }
}
