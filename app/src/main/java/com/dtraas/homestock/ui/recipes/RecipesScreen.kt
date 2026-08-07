package com.dtraas.homestock.ui.recipes

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.dtraas.homestock.HomeStockApplication
import com.dtraas.homestock.R
import com.dtraas.homestock.data.model.Allergen
import com.dtraas.homestock.data.repository.RecipeRepository
import com.dtraas.homestock.data.repository.RecipeSuggestion
import com.dtraas.homestock.ui.theme.SoftCardShapeCompact
import com.dtraas.homestock.ui.theme.SoftImageShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    onBack: () -> Unit,
    onRecipeClick: (String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as HomeStockApplication
    val viewModel: RecipesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecipesViewModel(application.container.recipeRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].language

    // languageTag as the key rather than Unit: an in-app language switch (Instellingen >
    // Algemeen > Taal) recreates the whole activity, but keying here means this also behaves
    // correctly if that ever changes — refetches with the new cuisine/region boost instead of
    // silently keeping stale results for the old language.
    LaunchedEffect(languageTag) { viewModel.load(languageTag) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.recipes_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AllergenFilterRow(
                excludedAllergens = uiState.excludedAllergens,
                onToggle = { allergen -> viewModel.toggleAllergen(allergen, languageTag) },
            )
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                uiState.hasError -> RecipesMessage(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.WifiOff,
                    title = stringResource(R.string.recipes_error_title),
                    subtitle = stringResource(R.string.recipes_error_subtitle),
                    retryLabel = stringResource(R.string.scan_result_retry),
                    onRetry = { viewModel.load(languageTag) },
                )
                uiState.recipes.isEmpty() -> RecipesMessage(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Filled.RestaurantMenu,
                    title = stringResource(R.string.recipes_empty_title),
                    subtitle = stringResource(R.string.recipes_empty_subtitle),
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.recipes_beta_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.recipes, key = { it.meal.id }) { recipe ->
                            RecipeRow(recipe = recipe, onClick = { onRecipeClick(recipe.meal.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Toggleable chips for the curated allergen subset (see RecipeRepository.filterableAllergens) — a selected chip means that allergen is excluded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllergenFilterRow(excludedAllergens: Set<Allergen>, onToggle: (Allergen) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.recipes_allergen_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecipeRepository.filterableAllergens.forEach { allergen ->
                val selected = allergen in excludedAllergens
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(allergen) },
                    label = { Text(stringResource(allergen.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun RecipesMessage(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
        if (retryLabel != null && onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
                Text(retryLabel)
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: RecipeSuggestion, onClick: () -> Unit) {
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
            if (recipe.meal.thumbnailUrl != null) {
                AsyncImage(
                    model = recipe.meal.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(SoftImageShape),
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = recipe.meal.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    // Ranks this recipe within the list (see RecipeRepository.suggestRecipes) —
                    // makes "wat kan ik koken met wat ik in huis heb" visible, not just implicit
                    // in the order.
                    Text(
                        text = stringResource(R.string.recipes_match_count_format, recipe.matchCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (recipe.matchesArea) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = stringResource(R.string.recipes_area_match_cd),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 6.dp).size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
