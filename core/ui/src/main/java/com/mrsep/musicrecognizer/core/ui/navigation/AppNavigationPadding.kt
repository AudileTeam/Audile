package com.mrsep.musicrecognizer.core.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun AppNavigationPadding(
    barHeight: IntState,
    railWidth: IntState,
    showRail: State<Boolean>,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val useRail = showRail.value
    val start = if (useRail) with(density) { railWidth.intValue.toDp() } else 0.dp
    val bottom = if (useRail) 0.dp else with(density) { barHeight.intValue.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = start, bottom = bottom)
            .then(
                if (useRail) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}
