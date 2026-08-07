package com.dtraas.homestock.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor

/**
 * [CenterAlignedTopAppBar] with an explicit, dedicated container color (see
 * [LocalTopAppBarContainerColor]) instead of the Material3 default, which matches
 * [androidx.compose.material3.MaterialTheme.colorScheme.surface] — this app's page
 * background exactly — so the bar silently blended into the content below it. Used for
 * every top app bar in the app so the title bar reads as its own band on every screen.
 * MainActivity pushes the same color onto the system status bar, so it also reaches
 * the physical top edge of the screen rather than stopping at this composable's bounds.
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
            containerColor = LocalTopAppBarContainerColor.current,
        ),
    )
}
