package com.dtraas.boodschapbeheer.ui.productdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.boodschapbeheer.BoodschapBeheerApplication
import com.dtraas.boodschapbeheer.R
import com.dtraas.boodschapbeheer.data.local.entity.NutritionInfo
import com.dtraas.boodschapbeheer.data.local.entity.ProductEntity
import com.dtraas.boodschapbeheer.data.model.Category
import com.dtraas.boodschapbeheer.ui.components.QuantityStepper
import com.dtraas.boodschapbeheer.ui.components.icon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    barcode: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as BoodschapBeheerApplication
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
                title = { Text(uiState.product?.name ?: stringResource(R.string.product_detail_default_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
        val category = Category.fromStorageKey(product?.category)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProductHero(product = product, category = category)

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryChip(category)
                product?.nutriScoreGrade?.let { NutriScoreBadge(grade = it) }
            }

            if (stillInInventory) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.product_detail_in_stock), style = MaterialTheme.typography.titleMedium)
                            QuantityStepper(
                                quantity = uiState.quantityInInventory ?: 0,
                                onDecrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 1) - 1) },
                                onIncrease = { viewModel.setQuantity((uiState.quantityInInventory ?: 0) + 1) },
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        ExpirationRow(
                            expirationDate = uiState.expirationDate,
                            onDateChange = viewModel::setExpirationDate,
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        MinQuantityRow(
                            minQuantity = uiState.minQuantity,
                            onChange = viewModel::setMinQuantity,
                        )

                        OutlinedButton(
                            onClick = {
                                viewModel.removeFromInventory()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.product_detail_remove))
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::addToShoppingList,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.product_detail_add_to_shopping_list))
            }

            product?.nutrition?.let { nutrition ->
                NutritionCard(nutrition, modifier = Modifier.padding(top = 20.dp))
            }

            product?.ingredients?.let { ingredients ->
                IngredientsCard(ingredients, modifier = Modifier.padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun ProductHero(product: ProductEntity?, category: Category) {
    val imageUrl = product?.imageUrl
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(140.dp),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = product?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }

    val subtitle = listOfNotNull(product?.brand, product?.unit).joinToString(" · ")
    if (subtitle.isNotEmpty()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun CategoryChip(category: Category) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(category.displayNameRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

private fun nutriScoreColor(grade: String): Color = when (grade.uppercase(Locale.ROOT)) {
    "A" -> Color(0xFF038141)
    "B" -> Color(0xFF85BB2F)
    "C" -> Color(0xFFFECB02)
    "D" -> Color(0xFFEE8100)
    "E" -> Color(0xFFE63E11)
    else -> Color.Gray
}

@Composable
private fun NutriScoreBadge(grade: String) {
    val contentDescription = stringResource(R.string.product_detail_nutriscore_format, grade.uppercase(Locale.ROOT))
    Surface(
        shape = CircleShape,
        color = nutriScoreColor(grade),
        modifier = Modifier
            .size(32.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = grade.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun IngredientsCard(ingredients: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.product_detail_ingredients_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = ingredients,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NutritionCard(nutrition: NutritionInfo, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.product_detail_nutrition_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))

            nutrition.energyKcal100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_energy), formatKcal(it))
            }
            nutrition.fat100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_fat), formatGrams(it))
            }
            nutrition.saturatedFat100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_saturated_fat), formatGrams(it), indented = true)
            }
            nutrition.carbohydrates100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_carbohydrates), formatGrams(it))
            }
            nutrition.sugars100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_sugars), formatGrams(it), indented = true)
            }
            nutrition.fiber100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_fiber), formatGrams(it))
            }
            nutrition.proteins100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_proteins), formatGrams(it))
            }
            nutrition.salt100g?.let {
                NutritionRow(stringResource(R.string.product_detail_nutrition_salt), formatGrams(it))
            }
        }
    }
}

@Composable
private fun NutritionRow(label: String, value: String, indented: Boolean = false) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = if (indented) 12.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (indented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = if (indented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = if (indented) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = if (indented) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatKcal(value: Double): String = String.format(Locale.getDefault(), "%.0f kcal", value)
private fun formatGrams(value: Double): String = String.format(Locale.getDefault(), "%.1f g", value)

private fun formatExpirationDate(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(date)
}

private fun daysUntilExpiration(millis: Long): Long {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    return ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpirationRow(expirationDate: Long?, onDateChange: (Long?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val isNearExpiry = expirationDate != null && daysUntilExpiration(expirationDate) <= 3

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.product_detail_expiration_label), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showPicker = true }) {
                Text(
                    text = expirationDate?.let { formatExpirationDate(it) }
                        ?: stringResource(R.string.product_detail_expiration_set),
                    color = if (isNearExpiry) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
            if (expirationDate != null) {
                IconButton(onClick = { onDateChange(null) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.product_detail_expiration_clear_cd),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = expirationDate)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onDateChange(state.selectedDateMillis)
                    showPicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun MinQuantityRow(minQuantity: Int?, onChange: (Int?) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.product_detail_min_quantity_label), style = MaterialTheme.typography.bodyLarge)
            QuantityStepper(
                quantity = minQuantity ?: 0,
                onDecrease = {
                    val next = (minQuantity ?: 0) - 1
                    onChange(if (next <= 0) null else next)
                },
                onIncrease = { onChange((minQuantity ?: 0) + 1) },
                minQuantity = 0,
                dense = true,
            )
        }
        Text(
            text = stringResource(R.string.product_detail_min_quantity_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
