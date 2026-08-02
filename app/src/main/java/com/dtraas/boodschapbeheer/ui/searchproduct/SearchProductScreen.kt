package com.dtraas.boodschapbeheer.ui.searchproduct

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.data.repository.ProductSearchResult
import com.dtraas.boodschapbeheer.ui.components.ProductImage
import com.dtraas.boodschapbeheer.ui.components.SearchField
import com.dtraas.boodschapbeheer.ui.components.icon
import com.dtraas.boodschapbeheer.ui.theme.SoftCardShapeCompact
import com.dtraas.boodschapbeheer.ui.theme.SoftImageShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchProductScreen(
    onBack: () -> Unit,
    onResultClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as BoodschapBeheerApplication
    val viewModel: SearchProductViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchProductViewModel(application.container.productRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.search_product_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.search_product_placeholder),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = viewModel::search,
                    enabled = uiState.query.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.search_product_action))
                }
            }

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                }
                uiState.hasError -> SearchProductMessage(
                    icon = Icons.Filled.WifiOff,
                    title = stringResource(R.string.search_product_error_title),
                    subtitle = stringResource(R.string.search_product_error_subtitle),
                )
                uiState.hasSearched && uiState.results.isEmpty() -> SearchProductMessage(
                    icon = Icons.Filled.SearchOff,
                    title = stringResource(R.string.search_product_empty_title),
                    subtitle = stringResource(R.string.search_product_empty_subtitle),
                )
                uiState.results.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.results, key = { it.barcode }) { result ->
                        SearchResultRow(result = result, onClick = { onResultClick(result.barcode) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchProductMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SearchResultRow(result: ProductSearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = SoftCardShapeCompact,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductImage(
                imageUrl = result.imageUrl,
                fallbackIcon = Category.OVERIG.icon,
                shape = SoftImageShape,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                result.brand?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}
