package com.dtraas.homestock.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dtraas.homestock.ui.theme.LocalTopAppBarContainerColor
import com.dtraas.homestock.ui.theme.LocalTopAppBarContentColor

/**
 * [CenterAlignedTopAppBar] with an explicit, dedicated container color (see
 * [LocalTopAppBarContainerColor]) instead of the Material3 default, which matches
 * [androidx.compose.material3.MaterialTheme.colorScheme.surface] — this app's page
 * background exactly — so the bar silently blended into the content below it. Used for
 * every top app bar in the app so the title bar reads as its own band on every screen.
 * MainActivity pushes the same color onto the system status bar, so it also reaches
 * the physical top edge of the screen rather than stopping at this composable's bounds.
 * The container is a full-strength, saturated sage rather than a pale tint, so title
 * and icons pair it with [LocalTopAppBarContentColor] (a light tone) instead of the
 * Material3 default ink-dark onSurface, which would barely read against it.
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
    val contentColor = LocalTopAppBarContentColor.current
    val containerColor = LocalTopAppBarContainerColor.current
    CenterAlignedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = containerColor,
            // Explicitly pinned to the same value as containerColor above — left at its
            // Material3 default, scrolledContainerColor is a pale surfaceContainer tone
            // completely unrelated to our bold custom containerColor. No screen currently
            // attaches a scrollBehavior, so nothing should ever blend toward it, but
            // CenterAlignedTopAppBar animates toward it via animateColorAsState on every
            // recomposition regardless (its target only pins to containerColor when the
            // fraction is exactly 0f) — on a screen swap, this bar is a brand-new instance
            // (each destination composes its own), so the very first frame(s) of that
            // animation could otherwise show a fraction >0f and briefly flash the pale
            // default before settling back to containerColor. Matching the two colors
            // makes that flash impossible regardless of the animation's transient state.
            scrolledContainerColor = containerColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor,
        ),
    )
}
