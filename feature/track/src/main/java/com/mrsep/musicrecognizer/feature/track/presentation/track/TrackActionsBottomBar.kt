package com.mrsep.musicrecognizer.feature.track.presentation.track

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.ui.components.VinylRotating
import kotlinx.coroutines.launch
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackActionsBottomBar(
    modifier: Modifier = Modifier,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
    isFavorite: Boolean,
    isRetryAllowed: Boolean,
    isLyricsAvailable: Boolean,
    isLyricsLoading: Boolean,
    onFavoriteClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRetryRequested: () -> Unit,
) {
    BottomAppBar(
        scrollBehavior = scrollBehavior,
        modifier = modifier,
        floatingActionButton = {
            LyricsFloatingActionButtonWithTooltip(
                isLyricsAvailable = isLyricsAvailable,
                isLyricsLoading = isLyricsLoading,
                onAvailableLyricsClick = onLyricsClick,
            )
        },
        actions = {
            if (isRetryAllowed) {
                RetryRecognitionButtonWithTooltip(onRetryRequested = onRetryRequested)
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_travel_explore_24),
                    contentDescription = stringResource(StringsR.string.web_search)
                )
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) {
                            UiR.drawable.outline_favorite_fill1_24
                        } else {
                            UiR.drawable.outline_favorite_24
                        }
                    ),
                    contentDescription = if (isFavorite) {
                        stringResource(StringsR.string.unmark_track_as_favorite)
                    } else {
                        stringResource(StringsR.string.mark_track_as_favorite)
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsFloatingActionButtonWithTooltip(
    isLyricsAvailable: Boolean,
    isLyricsLoading: Boolean,
    onAvailableLyricsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState()
    TooltipBox(
        modifier = modifier,
        state = tooltipState,
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above
        ),
        enableUserInput = false,
        tooltip = {
            PlainTooltip(
                modifier = Modifier.padding(2.dp),
                maxWidth = 250.dp,
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shadowElevation = 1.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    when {
                        isLyricsLoading -> {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_search_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        !isLyricsAvailable -> {
                            Icon(
                                painter = painterResource(UiR.drawable.outline_close_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = when {
                            isLyricsLoading -> stringResource(StringsR.string.searching_for_lyrics)
                            !isLyricsAvailable -> stringResource(StringsR.string.no_lyrics_available)
                            else -> ""
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        val lyricsButtonAlpha by animateFloatAsState(
            when {
                isLyricsAvailable -> 1f
                isLyricsLoading -> 0.7f
                else -> 0.5f
            }
        )
        val lyricsButtonExtraElevation by animateDpAsState(if (isLyricsAvailable) 2.dp else 1.dp)
        Box {
            val lyricsStateDesc = when {
                isLyricsLoading -> stringResource(StringsR.string.searching_for_lyrics)
                !isLyricsAvailable -> stringResource(StringsR.string.no_lyrics_available)
                else -> null
            }
            FloatingActionButton(
                onClick = {
                    if (isLyricsAvailable) {
                        tooltipState.dismiss()
                        onAvailableLyricsClick()
                    } else {
                        scope.launch { tooltipState.show() }
                    }
                },
                modifier = Modifier.semantics {
                    lyricsStateDesc?.let { stateDescription = it }
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    .copy(alpha = lyricsButtonAlpha)
                    .compositeOver(BottomAppBarDefaults.containerColor),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    .copy(alpha = lyricsButtonAlpha),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = lyricsButtonExtraElevation,
                    pressedElevation = 1.dp + lyricsButtonExtraElevation,
                    focusedElevation = 1.dp + lyricsButtonExtraElevation,
                    hoveredElevation = 1.dp + lyricsButtonExtraElevation
                )
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_lyrics_24),
                    contentDescription = stringResource(StringsR.string.show_lyrics)
                )
            }
            AnimatedVisibility(
                visible = isLyricsLoading,
                enter = fadeIn(tween(300)) + scaleIn(tween(300)),
                exit = fadeOut(tween(300)) + scaleOut(tween(300)),
                modifier = Modifier
                    .padding(2.dp)
                    .align(Alignment.BottomEnd)
            ) {
                VinylRotating(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetryRecognitionButtonWithTooltip(
    modifier: Modifier = Modifier,
    onRetryRequested: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState()
    TooltipBox(
        state = tooltipState,
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above
        ),
        enableUserInput = false,
        tooltip = {
            PlainTooltip(
                modifier = Modifier.padding(2.dp),
                maxWidth = 250.dp,
                shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = stringResource(StringsR.string.button_retry_recognition_hint),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp),
                )
            }
        },
    ) {
        val retryActionLabel = stringResource(StringsR.string.button_retry_recognition)
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(CircleShape)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(label = retryActionLabel) {
                            onRetryRequested()
                            true
                        }
                    )
                }
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(StringsR.string.button_retry_recognition_hint),
                    onLongClickLabel = retryActionLabel,
                    onClick = {
                        scope.launch { tooltipState.show() }
                    },
                    onLongClick = {
                        tooltipState.dismiss()
                        onRetryRequested()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.outline_replay_24),
                contentDescription = stringResource(StringsR.string.button_retry_recognition)
            )
        }
    }
}
