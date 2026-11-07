package com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.feature.recognition.presentation.model.EnqueuedRecognitionUi
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun RecognitionLazyGridItem(
    recognition: EnqueuedRecognitionUi,
    selected: Boolean,
    isPlaying: Boolean,
    onStartPlayRecord: () -> Unit,
    onStopPlayRecord: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCreationDate: Boolean,
    contentPadding: PaddingValues,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "containerColor"
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .drawBehind { drawRect(color = containerColor) }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(contentPadding)
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .shadow(
                    elevation = 1.dp,
                    shape = shape
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = shape
                )
                .clip(shape)
                .clickable {
                    if (isPlaying) onStopPlayRecord() else onStartPlayRecord()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) +
                            scaleIn(
                                initialScale = 0f,
                                animationSpec = tween(220)
                            )).togetherWith(
                        fadeOut(animationSpec = tween(220)) +
                                scaleOut(
                                    targetScale = 0f,
                                    animationSpec = tween(220)
                                )
                    )
                },
                contentAlignment = Alignment.Center,
                label = "PlayerIcon"
            ) { playing ->
                if (playing) {
                    Icon(
                        painter = painterResource(UiR.drawable.rounded_pause_48),
                        contentDescription = stringResource(StringsR.string.recording_stop_player),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(UiR.drawable.rounded_play_arrow_48),
                        contentDescription = stringResource(StringsR.string.recording_start_player),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text(
                text = recognition.getTitleMessage(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StatusChip(recognition = recognition)
            Spacer(Modifier.height(2.dp))
            if (showCreationDate) {
                Text(
                    text = recognition.creationDateShort,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
