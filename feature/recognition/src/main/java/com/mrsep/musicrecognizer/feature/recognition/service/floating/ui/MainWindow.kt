package com.mrsep.musicrecognizer.feature.recognition.service.floating.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.materialkolor.PaletteStyle
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.core.ui.components.VinylRotating
import com.mrsep.musicrecognizer.core.ui.theme.MusicRecognizerTheme
import com.mrsep.musicrecognizer.core.ui.theme.SwitchingMusicRecognizerTheme
import com.mrsep.musicrecognizer.core.ui.util.centeredIconPainter
import com.mrsep.musicrecognizer.core.ui.util.copyTextToClipboard
import com.mrsep.musicrecognizer.core.common.DeeplinkRouter
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RippleEffect
import com.mrsep.musicrecognizer.feature.recognition.service.RecognitionControlService
import com.mrsep.musicrecognizer.feature.recognition.service.floating.FloatingWindowSharedModel
import com.mrsep.musicrecognizer.feature.recognition.service.floating.FloatingWindowUiState
import com.mrsep.musicrecognizer.feature.recognition.service.floating.DismissWindowState
import com.mrsep.musicrecognizer.feature.recognition.service.floating.StartRecognitionAction
import com.mrsep.musicrecognizer.feature.recognition.service.floating.core.draggableFloatingWindow
import com.mrsep.musicrecognizer.feature.recognition.service.floating.ui.components.AnimatedLines
import kotlinx.coroutines.flow.filter
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

internal val MainWindowSize = 64.dp
private const val DraggedScale = 0.9f
private const val RecognizingScale = 0.8f

internal val ThemePaletteStyle = PaletteStyle.TonalSpot

private enum class LyricsState { Idle, Loading, Showing }

@Composable
internal fun MainWindow(
    sharedModel: FloatingWindowSharedModel,
    deeplinkRouter: DeeplinkRouter,
    dismissWindowState: DismissWindowState,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
) {
    LaunchedEffect(Unit) {
        snapshotFlow { dismissWindowState.shouldRemoveTarget }
            .filter { it }
            .collect { sharedModel.setFloatingButtonEnabled(false) }
    }

    val context = LocalContext.current

    val uiState by sharedModel.uiState.collectAsStateWithLifecycle()
    val preferences by sharedModel.preferences.collectAsStateWithLifecycle()

    val globalScale by animateFloatAsState(
        targetValue = if (dismissWindowState.isTargetMagnetized) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val buttonScale = remember { Animatable(1f) }
    var isDragged by remember { mutableStateOf(false) }

    val status = uiState.recognitionStatus
    val isRecognizing = status is RecognitionStatus.Recognizing

    LaunchedEffect(isDragged, isRecognizing) {
        val targetScale = when {
            isRecognizing -> RecognizingScale
            isDragged -> DraggedScale
            else -> 1f
        }
        buttonScale.animateTo(
            targetValue = targetScale,
            animationSpec = SpringSpec(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        )
    }

    LaunchedEffect(dismissWindowState.isTargetMagnetized) {
        if (dismissWindowState.isTargetMagnetized) sharedModel.dismissRecognitionResult()
    }

    // Dismiss the result if the user turns off the screen
    if (status is RecognitionStatus.Done) {
        LifecycleStartEffect(Unit) {
            onStopOrDispose { sharedModel.dismissRecognitionResult() }
        }
    }

    val vibrateOnTap = preferences?.hapticFeedback?.vibrateOnTap == true
    if (preferences?.hapticFeedback?.vibrateOnResult == true) {
        val vibrated = rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(status) {
            if (status !is RecognitionStatus.Done) {
                vibrated.value = false
                return@LaunchedEffect
            }
            if (vibrated.value) return@LaunchedEffect
            when (status.result) {
                is RecognitionResult.Success,
                is RecognitionResult.ScheduledOffline -> sharedModel.vibrateSuccess()

                is RecognitionResult.NoMatches,
                RecognitionResult.NoSoundDetected,
                is RecognitionResult.Error -> sharedModel.vibrateFailure()
            }
            vibrated.value = true
        }
    }

    val successResult = (status as? RecognitionStatus.Done)?.result as? RecognitionResult.Success
    val currentTrack = successResult?.let { uiState.currentTrack ?: it.track }
    val seedColor = currentTrack?.properties?.themeSeedColor?.run(::Color)

    MusicRecognizerTheme {
        SwitchingMusicRecognizerTheme(
            seedColor = seedColor,
            style = ThemePaletteStyle,
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = globalScale
                        scaleY = globalScale
                    }
                    .size(MainWindowSize)
                    .draggableFloatingWindow(
                        dismissWindowState = dismissWindowState,
                        onDragStart = {
                            onDragStart()
                            isDragged = true
                        },
                        onDragEnd = {
                            onDragEnd()
                            isDragged = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isRecognizing,
                    enter = fadeIn(animationSpec = spring()) + scaleIn(spring()),
                    exit = fadeOut(animationSpec = spring()) + scaleOut(spring()),
                    modifier = Modifier.fillMaxSize()
                ) {
                    RippleEffect(
                        activated = false,
                        modifier = Modifier.fillMaxSize(),
                        startOffsetFraction = 0.8f, // Should be a bit less than buttonSize/rippleSize
                        baseColor = MaterialTheme.colorScheme.secondary,
                        activatedColor = MaterialTheme.colorScheme.secondary,
                        circlesCount = 3,
                        animationSpeed = 6_000,
                        fadeEasing = EaseIn,
                        scaleEasing = EaseIn
                    )
                }

                BaseContainer(
                    modifier = Modifier.graphicsLayer {
                        scaleX = buttonScale.value
                        scaleY = buttonScale.value
                    },
                    onClick = {
                        when (status) {
                            RecognitionStatus.Ready -> {
                                if (vibrateOnTap) sharedModel.vibrateOnTap()
                                sharedModel.startRecognition(StartRecognitionAction.Default)
                            }
                            is RecognitionStatus.Recognizing -> {
                                if (vibrateOnTap) sharedModel.vibrateOnTap()
                                RecognitionControlService.cancelRecognition(context)
                            }
                            is RecognitionStatus.Done -> when (val result = status.result) {
                                is RecognitionResult.Success -> {
                                    context.startActivity(deeplinkRouter.getDeepLinkIntentToTrack(result.track.id))
                                    sharedModel.dismissRecognitionResult()
                                }
                                is RecognitionResult.NoMatches,
                                RecognitionResult.NoSoundDetected -> {
                                    if (vibrateOnTap) sharedModel.vibrateOnTap()
                                    sharedModel.startRecognition(StartRecognitionAction.Retry)
                                }
                                is RecognitionResult.ScheduledOffline -> {
                                    context.startActivity(deeplinkRouter.getDeepLinkIntentToQueue())
                                    sharedModel.dismissRecognitionResult()
                                }
                                is RecognitionResult.Error -> sharedModel.dismissRecognitionResult()
                            }
                        }
                    },
                    onClickLabel = stringResource(
                        id = when (status) {
                            RecognitionStatus.Ready -> StringsR.string.action_recognize
                            is RecognitionStatus.Recognizing -> StringsR.string.action_cancel_recognition
                            is RecognitionStatus.Done -> when (status.result) {
                                is RecognitionResult.Success -> StringsR.string.show_track
                                is RecognitionResult.NoMatches,
                                RecognitionResult.NoSoundDetected -> StringsR.string.button_retry_recognition
                                is RecognitionResult.ScheduledOffline -> StringsR.string.show
                                is RecognitionResult.Error -> StringsR.string.reset
                            }
                        }
                    ),
                    onLongClick = when (status) {
                        RecognitionStatus.Ready -> {
                            {
                                if (vibrateOnTap) sharedModel.vibrateOnTap()
                                sharedModel.startRecognition(StartRecognitionAction.Alternative)
                            }
                        }
                        is RecognitionStatus.Done -> when (val result = status.result) {
                            is RecognitionResult.Success -> {
                                {
                                    with(result.track) {
                                        context.copyTextToClipboard("$title - $artist")
                                    }
                                }
                            }
                            else -> null
                        }
                        else -> null
                    },
                    onLongClickLabel = when (status) {
                        RecognitionStatus.Ready -> stringResource(StringsR.string.action_recognize)
                        is RecognitionStatus.Done -> when (status.result) {
                            is RecognitionResult.Success -> stringResource(StringsR.string.copy)
                            else -> null
                        }
                        else -> null
                    },
                ) {
                    AnimatedContent(
                        targetState = uiState,
                        transitionSpec = ContentTransitionSpec,
                        contentKey = { it.transitionKey },
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) { state ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (val status = state.recognitionStatus) {
                                RecognitionStatus.Ready,
                                is RecognitionStatus.Recognizing -> AnimatedLines(
                                    modifier = Modifier.size(36.dp),
                                    isActivated = isRecognizing,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )

                                is RecognitionStatus.Done -> when (val result = status.result) {
                                    is RecognitionResult.Success -> {
                                        val placeholder = centeredIconPainter(
                                            painter = painterResource(UiR.drawable.rounded_music_note_48),
                                            targetSize = 36.dp,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        val currentTrack = state.currentTrack ?: result.track
                                        AsyncImage(
                                            model = (currentTrack.artworkThumbUrl
                                                ?: currentTrack.artworkUrl),
                                            contentDescription = null,
                                            fallback = placeholder,
                                            error = placeholder,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }

                                    is RecognitionResult.NoMatches -> Icon(
                                        painter = painterResource(UiR.drawable.rounded_question_mark_48),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    is RecognitionResult.ScheduledOffline -> Icon(
                                        painter = painterResource(UiR.drawable.outline_schedule_send_24),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    RecognitionResult.NoSoundDetected,
                                    is RecognitionResult.Error -> Icon(
                                        painter = painterResource(UiR.drawable.rounded_priority_high_48),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = when {
                        sharedModel.isShowingLyrics.value -> LyricsState.Showing
                        uiState.isLyricsFetcherRunning -> LyricsState.Loading
                        else -> LyricsState.Idle
                    },
                    transitionSpec = lyricsIconTransform,
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(
                            alignment = if (sharedModel.isLeftAnchored.value) {
                                AbsoluteAlignment.BottomRight
                            } else {
                                AbsoluteAlignment.BottomLeft
                            }
                        )
                        .size(SmallIconContainerSize)
                ) { lyricsState ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (lyricsState) {
                            LyricsState.Idle -> Box(
                                modifier = Modifier
                                    .size(SmallIconContainerSize)
                                    .background(Color.Transparent)
                            )

                            LyricsState.Loading -> SmallIconContainer {
                                VinylRotating(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(SmallIconSize),
                                )
                            }

                            LyricsState.Showing -> SmallIconContainer {
                                Icon(
                                    painter = painterResource(UiR.drawable.rounded_lyrics_fill1_20),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(SmallIconSize)
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}

private val SmallIconContainerSize = 22.dp
private val SmallIconSize = 14.dp

@Composable
private fun SmallIconContainer(
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SmallIconContainerSize)
            .clip(CircleShape)
            .background(color = containerColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun BaseContainer(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClickLabel: String,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable (BoxScope.() -> Unit),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(color = containerColor, shape = CircleShape)
            .combinedClickable(
                onClick = onClick,
                onClickLabel = onClickLabel,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                role = Role.Button,
                hapticFeedbackEnabled = false,
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

private val FloatingWindowUiState.transitionKey get() = when (recognitionStatus) {
    RecognitionStatus.Ready,
    is RecognitionStatus.Recognizing -> "animated_lines"

    is RecognitionStatus.Done -> when (recognitionStatus.result) {
        is RecognitionResult.Success -> "success"
        is RecognitionResult.NoMatches -> "no_matches"
        RecognitionResult.NoSoundDetected -> "no_sound_detected"
        is RecognitionResult.ScheduledOffline -> "scheduled_offline"
        is RecognitionResult.Error -> "error"
    }
}

private fun FloatingWindowUiState.hasArtwork(): Boolean {
    val successResult = (recognitionStatus as? RecognitionStatus.Done)?.result as? RecognitionResult.Success
        ?: return false
    return successResult.track.artworkUrl != null || successResult.track.artworkThumbUrl != null
}

internal val ContentTransitionSpec: AnimatedContentTransitionScope<FloatingWindowUiState>.() -> ContentTransform = {
    val isEnteringArtwork = targetState.hasArtwork()
    val isExitingArtwork = initialState.hasArtwork()

    val enterTransition = if (isEnteringArtwork) {
        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessHigh))
    } else {
        fadeIn(animationSpec = spring()) + scaleIn(animationSpec = spring())
    }

    val exitTransition = if (isExitingArtwork) {
        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh))
    } else {
        fadeOut(animationSpec = spring()) + scaleOut(animationSpec = spring())
    }

    enterTransition togetherWith exitTransition
}

private val lyricsIconTransform: AnimatedContentTransitionScope<LyricsState>.() -> ContentTransform = {
    (fadeIn(
        animationSpec = tween(durationMillis = TRACK_CONTENT_ANIMATION_DURATION_MS, delayMillis = TRACK_CONTENT_ANIMATION_DURATION_MS)
    ) + scaleIn(
        initialScale = 0.7f,
        animationSpec = tween(durationMillis = TRACK_CONTENT_ANIMATION_DURATION_MS, delayMillis = TRACK_CONTENT_ANIMATION_DURATION_MS)
    )).togetherWith(
        fadeOut(
            animationSpec = tween(durationMillis = TRACK_CONTENT_ANIMATION_DURATION_MS)
        ) + scaleOut(
            targetScale = 0.7f,
            animationSpec = tween(durationMillis = TRACK_CONTENT_ANIMATION_DURATION_MS)
        )
    ).using(
        SizeTransform { _, _ -> snap(delayMillis = TRACK_CONTENT_ANIMATION_DURATION_MS) }
    )
}
