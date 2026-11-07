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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionWorkStatus
import com.mrsep.musicrecognizer.feature.recognition.presentation.model.EnqueuedRecognitionUi
import com.mrsep.musicrecognizer.feature.recognition.presentation.model.RemoteRecognitionResultUi
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Composable
internal fun RecognitionLazyColumnItem(
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(color = containerColor) }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(contentPadding)
    ) {
        Box(
            modifier = Modifier
                .heightIn(max = 100.dp)
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
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .heightIn(min = 100.dp)
                .padding(vertical = 2.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = recognition.getTitleMessage(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.95f)
            )
            StatusChip(recognition = recognition)
            if (showCreationDate) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = recognition.creationDateShort,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .alpha(0.9f)
                        .align(Alignment.End)
                )
            }
        }
    }
}

@Composable
internal fun EnqueuedRecognitionUi.getTitleMessage(): String {
    return title.ifBlank { stringResource(StringsR.string.untitled_recognition) }
}

@Composable
internal fun EnqueuedRecognitionUi.getStatusMessage(concise: Boolean): String {
    val statusDescription = when (status) {
        RecognitionWorkStatus.IDLE -> when (val current = result) {
            null -> stringResource(StringsR.string.recognition_status_idle)
            else -> current.statusTitle()
        }

        RecognitionWorkStatus.QUEUED -> when (result) {
            is RemoteRecognitionResultUi.Error -> stringResource(StringsR.string.recognition_status_retry)
            else -> stringResource(StringsR.string.recognition_status_enqueued)
        }

        RecognitionWorkStatus.RUNNING -> stringResource(StringsR.string.recognition_status_running)
    }
    return if (concise) {
        statusDescription
    } else {
        stringResource(
            StringsR.string.format_recognition_status,
            statusDescription
        )
    }
}

@Composable
private fun RemoteRecognitionResultUi.statusTitle() = when (this) {
    RemoteRecognitionResultUi.Error.BadConnection -> stringResource(StringsR.string.result_title_bad_connection)
    is RemoteRecognitionResultUi.Error.BadRecording -> stringResource(StringsR.string.result_title_recording_error)
    is RemoteRecognitionResultUi.Error.HttpError -> stringResource(StringsR.string.result_title_bad_network_response)
    is RemoteRecognitionResultUi.Error.UnhandledError -> stringResource(StringsR.string.result_title_internal_error)
    is RemoteRecognitionResultUi.Error.AuthError -> stringResource(StringsR.string.result_title_auth_error)
    is RemoteRecognitionResultUi.Error.ApiUsageLimited -> stringResource(StringsR.string.result_title_service_usage_limited)
    RemoteRecognitionResultUi.NoMatches -> stringResource(StringsR.string.result_title_no_matches)
    RemoteRecognitionResultUi.NoSoundDetected -> stringResource(StringsR.string.result_title_no_sound_detected)
    is RemoteRecognitionResultUi.Success -> stringResource(StringsR.string.recognition_status_track_found)
}

@Composable
internal fun StatusChip(
    modifier: Modifier = Modifier,
    recognition: EnqueuedRecognitionUi,
) {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface
    val chipContainerColor = when (recognition.status) {
        RecognitionWorkStatus.RUNNING ->
            scheme.tertiaryContainer.copy(alpha = 0.3f).compositeOver(surface)
        RecognitionWorkStatus.IDLE if recognition.result is RemoteRecognitionResultUi.Success ->
            scheme.primaryContainer.copy(alpha = 0.4f).compositeOver(surface)
        else ->
            scheme.secondaryContainer.copy(alpha = 0.4f).compositeOver(surface)
    }
    Text(
        text = recognition.getStatusMessage(true),
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurface.copy(0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 220.dp)
            .padding(vertical = 2.dp)
            .background(
                color = chipContainerColor,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
