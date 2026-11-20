package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun AutoScrollToolbarHorizontal(
    modifier: Modifier = Modifier,
    isStarted: Boolean,
    onStartScrollClick: () -> Unit,
    onStopScrollClick: () -> Unit,
    scrollSpeed: Float,
    onScrollSpeedChange: (Float) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = isStarted,
            modifier = Modifier
                .shadow(
                    elevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                )
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.large
                )
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val valueRange = 0.5f..2f
                val sliderState = remember(valueRange) {
                    SliderState(value = scrollSpeed, steps = 0, trackRange = valueRange)
                }
                sliderState.value = scrollSpeed
                Slider(
                    state = sliderState,
                    onValueChange = onScrollSpeedChange,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        inactiveTrackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier
                        .height(56.dp)
                        .width(156.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FloatingActionButton(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onClick = if (isStarted) onStopScrollClick else onStartScrollClick,
            modifier = Modifier,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 2.dp,
                pressedElevation = 3.dp,
                focusedElevation = 3.dp,
                hoveredElevation = 3.dp
            ),
        ) {
            if (isStarted) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_stop_fill1_24),
                    contentDescription = stringResource(StringsR.string.lyrics_stop_autoscroll)
                )
            } else {
                Icon(
                    painter = painterResource(UiR.drawable.outline_play_arrow_fill1_24),
                    contentDescription = stringResource(StringsR.string.lyrics_start_autoscroll)
                )
            }
        }
    }
}

@Composable
internal fun AutoScrollToolbarVertical(
    modifier: Modifier = Modifier,
    isStarted: Boolean,
    onStartScrollClick: () -> Unit,
    onStopScrollClick: () -> Unit,
    scrollSpeed: Float,
    onScrollSpeedChange: (Float) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = isStarted,
            modifier = Modifier
                .shadow(
                    elevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                )
                .background(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.large
                ),
        ) {
            Box(
                modifier = Modifier.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val valueRange = 0.5f..2f
                val sliderState = remember(valueRange) {
                    SliderState(value = scrollSpeed, steps = 0, trackRange = valueRange)
                }
                sliderState.value = scrollSpeed
                Slider(
                    state = sliderState,
                    onValueChange = onScrollSpeedChange,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        inactiveTrackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier
                        .height(156.dp)
                        .width(56.dp)
                        .graphicsLayer {
                            rotationZ = 270f
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                Constraints(
                                    minWidth = constraints.minHeight,
                                    maxWidth = constraints.maxHeight,
                                    minHeight = constraints.minWidth,
                                    maxHeight = constraints.maxHeight,
                                )
                            )
                            layout(placeable.height, placeable.width) {
                                placeable.place(-placeable.width, 0)
                            }
                        }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        FloatingActionButton(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            onClick = if (isStarted) onStopScrollClick else onStartScrollClick,
            modifier = Modifier,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 2.dp,
                pressedElevation = 3.dp,
                focusedElevation = 3.dp,
                hoveredElevation = 3.dp
            ),
        ) {
            if (isStarted) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_stop_fill1_24),
                    contentDescription = stringResource(StringsR.string.lyrics_stop_autoscroll)
                )
            } else {
                Icon(
                    painter = painterResource(UiR.drawable.outline_play_arrow_fill1_24),
                    contentDescription = stringResource(StringsR.string.lyrics_start_autoscroll)
                )
            }
        }
    }
}
