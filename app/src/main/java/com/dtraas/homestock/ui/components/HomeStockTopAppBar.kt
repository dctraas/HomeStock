package com.dtraas.homestock.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [CenterAlignedTopAppBar] with an explicit container color, one step up the
 * surfaceContainer ramp from the page background (see ui/theme/Color.kt). Left at its
 * Material3 default, a top app bar's container color matches
 * [MaterialTheme.colorScheme.surface], which equals this app's page background exactly
 * — so the bar silently blended into the content below it. Used for every top app bar
 * in the app so the title bar reads as its own band on every screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeStockTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
