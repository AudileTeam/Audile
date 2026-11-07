package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import android.Manifest
import android.app.Activity
import android.app.Activity.MEDIA_PROJECTION_SERVICE
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.domain.preferences.UserPreferences
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.ui.components.RecognitionPermissionsBlockedDialog
import com.mrsep.musicrecognizer.core.ui.components.RecognitionPermissionsRationaleDialog
import com.mrsep.musicrecognizer.core.ui.findActivity
import com.mrsep.musicrecognizer.core.ui.shouldShowRationale
import com.mrsep.musicrecognizer.feature.recognition.BuildConfig
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.ApiUsageLimitedShield
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.AuthErrorShield
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.BadConnectionShield
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.FatalErrorShield
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.NoMatchesShield
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.NoSoundShield
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.shields.ScheduledOfflineShield
import com.mrsep.musicrecognizer.feature.recognition.service.AudioCaptureServiceMode
import com.mrsep.musicrecognizer.feature.recognition.service.AutoRecognitionLauncher
import com.mrsep.musicrecognizer.feature.recognition.service.createScreenCaptureIntentForDisplay
import com.mrsep.musicrecognizer.feature.recognition.service.toServiceMode
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

internal const val animationDurationButton = 200
internal const val animationDurationShield = 250

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun RecognitionScreen(
    viewModel: RecognitionViewModel = hiltViewModel(),
    pendingRecognitionRequest: RecognitionRequest?,
    onResetPendingRecognitionRequest: () -> Unit,
    onNavigateToTrackScreen: (trackId: String) -> Unit,
    onNavigateToQueueScreen: (recognitionId: Int?) -> Unit,
    onNavigateToPreferencesScreen: () -> Unit,
) {
    val context = LocalContext.current
    val recognizeStatus by viewModel.recognitionState.collectAsStateWithLifecycle()
    val soundLevelState = viewModel.soundLevelFlow.collectAsStateWithLifecycle(initialValue = 0f)
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val autoRecognitionState by viewModel.autoRecognitionState.collectAsStateWithLifecycle()
    val defaultCaptureMode = preferences?.defaultAudioCaptureMode
    val longClickCaptureMode = preferences?.mainButtonLongPressAudioCaptureMode
    val lastUsedAudioCaptureMode = preferences?.lastUsedAudioCaptureMode

    val mediaProjectionManager = remember {
        context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val requestedAudioCaptureMode = rememberSaveable {
        mutableStateOf(AudioCaptureMode.Microphone)
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            if (pendingRecognitionRequest != null) onResetPendingRecognitionRequest()
            return@rememberLauncherForActivityResult
        }
        result.data?.let { mediaProjectionData ->
            val captureMode = requestedAudioCaptureMode.value.toServiceMode(mediaProjectionData)
            viewModel.launchRecognition(captureMode)
        }
    }

    //region <permission handling block>
    var showPermissionsRationaleDialog by rememberSaveable { mutableStateOf(false) }
    var showPermissionsBlockedDialog by rememberSaveable { mutableStateOf(false) }
    val requiredPermissionsState = rememberMultiplePermissionsState(
        permissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        }
    ) { results ->
        if (results.all { (_, isGranted) -> isGranted }) {
            when (val requestedMode = requestedAudioCaptureMode.value) {
                AudioCaptureMode.Microphone -> {
                    viewModel.launchRecognition(requestedMode.toServiceMode(null))
                }
                AudioCaptureMode.Device,
                AudioCaptureMode.Auto -> {
                    if (preferences?.useAltDeviceSoundSource == true) {
                        viewModel.launchRecognition(requestedMode.toServiceMode(null))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val intent = mediaProjectionManager.createScreenCaptureIntentForDisplay()
                        mediaProjectionLauncher.launch(intent)
                    }
                }
            }
        } else {
            val activity = context.findActivity()
            showPermissionsBlockedDialog = results
                .any { (permission, isGranted) ->
                    !isGranted && !activity.shouldShowRationale(permission)
                }
            if (pendingRecognitionRequest != null) onResetPendingRecognitionRequest()
        }
    }
    if (showPermissionsRationaleDialog) {
        RecognitionPermissionsRationaleDialog(
            onConfirmClick = {
                showPermissionsRationaleDialog = false
                requiredPermissionsState.launchMultiplePermissionRequest()
            },
            onDismissClick = {
                showPermissionsRationaleDialog = false
                if (pendingRecognitionRequest != null) onResetPendingRecognitionRequest()
            }
        )
    }
    if (showPermissionsBlockedDialog) {
        RecognitionPermissionsBlockedDialog(
            onConfirmClick = { showPermissionsBlockedDialog = false },
            onDismissClick = { showPermissionsBlockedDialog = false }
        )
    }
    //endregion

    //region <auto recognition permission handling block>
    var showAutoRecognitionPermissionsRationaleDialog by rememberSaveable { mutableStateOf(false) }
    var showAutoRecognitionPermissionsBlockedDialog by rememberSaveable { mutableStateOf(false) }
    val autoRecognitionPermissionsState = rememberMultiplePermissionsState(
        permissions = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        }
    ) { results ->
        if (results.all { (_, isGranted) -> isGranted }) {
            AutoRecognitionLauncher.start(context)
        } else {
            val activity = context.findActivity()
            showAutoRecognitionPermissionsBlockedDialog = results
                .any { (permission, isGranted) ->
                    !isGranted && !activity.shouldShowRationale(permission)
                }
        }
    }
    if (showAutoRecognitionPermissionsRationaleDialog) {
        RecognitionPermissionsRationaleDialog(
            onConfirmClick = {
                showAutoRecognitionPermissionsRationaleDialog = false
                autoRecognitionPermissionsState.launchMultiplePermissionRequest()
            },
            onDismissClick = { showAutoRecognitionPermissionsRationaleDialog = false }
        )
    }
    if (showAutoRecognitionPermissionsBlockedDialog) {
        RecognitionPermissionsBlockedDialog(
            onConfirmClick = { showAutoRecognitionPermissionsBlockedDialog = false },
            onDismissClick = { showAutoRecognitionPermissionsBlockedDialog = false }
        )
    }
    //endregion

    fun toggleAutoRecognition() {
        if (autoRecognitionState.running) {
            AutoRecognitionLauncher.stop(context)
            return
        }
        if (autoRecognitionPermissionsState.allPermissionsGranted) {
            AutoRecognitionLauncher.start(context)
        } else if (autoRecognitionPermissionsState.shouldShowRationale) {
            showAutoRecognitionPermissionsRationaleDialog = true
        } else {
            autoRecognitionPermissionsState.launchMultiplePermissionRequest()
        }
    }

    fun checkPermissionsAndLaunchRecognition(captureMode: AudioCaptureMode) {
        requestedAudioCaptureMode.value = captureMode
        if (requiredPermissionsState.allPermissionsGranted) {
            when (captureMode) {
                AudioCaptureMode.Microphone -> {
                    viewModel.launchRecognition(AudioCaptureServiceMode.Microphone)
                }
                AudioCaptureMode.Device,
                AudioCaptureMode.Auto -> {
                    if (preferences?.useAltDeviceSoundSource == true) {
                        viewModel.launchRecognition(captureMode.toServiceMode(null))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val intent = mediaProjectionManager.createScreenCaptureIntentForDisplay()
                        mediaProjectionLauncher.launch(intent)
                    }
                }
            }
        } else if (requiredPermissionsState.shouldShowRationale) {
            showPermissionsRationaleDialog = true
        } else {
            requiredPermissionsState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(pendingRecognitionRequest, recognizeStatus, defaultCaptureMode, lastUsedAudioCaptureMode) {
        if (pendingRecognitionRequest == null || defaultCaptureMode == null || lastUsedAudioCaptureMode == null) return@LaunchedEffect
        when (recognizeStatus) {
            RecognitionStatus.Ready -> {
                val captureMode = when (pendingRecognitionRequest) {
                    RecognitionRequest.Startup -> defaultCaptureMode
                    RecognitionRequest.Retry -> lastUsedAudioCaptureMode
                }
                checkPermissionsAndLaunchRecognition(captureMode)
            }
            is RecognitionStatus.Recognizing,
            is RecognitionStatus.Done -> onResetPendingRecognitionRequest()
        }
    }

    Box(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        if (BuildConfig.DEBUG) {
            DebugBuildLabel(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
        }
        Box {
            AnimatedVisibility(
                visible = recognizeStatus.isNotDone(),
                enter = enterTransitionButton,
                exit = exitTransitionButton,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                fun onClick(captureMode: AudioCaptureMode) {
                    if (recognizeStatus.isDone()) return
                    if (preferences.vibrateOnTap()) viewModel.vibrateOnTap()
                    if (recognizeStatus is RecognitionStatus.Recognizing) {
                        viewModel.cancelRecognition()
                    } else {
                        checkPermissionsAndLaunchRecognition(captureMode)
                    }
                }
                RecognitionButtonWithTitle(
                    title = getButtonTitle(recognizeStatus, skipReady = pendingRecognitionRequest != null),
                    onButtonClick = { defaultCaptureMode?.run(::onClick) },
                    onButtonLongClick = { longClickCaptureMode?.run(::onClick) },
                    activated = recognizeStatus is RecognitionStatus.Recognizing,
                    soundLevelState = soundLevelState,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        BackHandler(
            enabled = recognizeStatus.isDone(),
            onBack = viewModel::resetRecognitionResult
        )
        AnimatedContent(
            targetState = recognizeStatus,
            contentAlignment = Alignment.Center,
            transitionSpec = transitionSpecShield,
            label = "shieldTransition"
        ) { thisStatus ->
            when (thisStatus) {
                is RecognitionStatus.Done -> when (val result = thisStatus.result) {
                    is RecognitionResult.Success -> {
                        LaunchedEffect(result) {
                            delay(animationDurationButton.milliseconds)
                            onNavigateToTrackScreen(result.track.id)
                        }
                        DisposableEffect(result) {
                            onDispose(viewModel::resetRecognitionResult)
                        }
                    }

                    is RecognitionResult.NoMatches -> NoMatchesShield(
                        recognitionTask = result.recognitionTask,
                        onDismissClick = viewModel::resetRecognitionResult,
                        onRetryClick = {
                            val captureMode = lastUsedAudioCaptureMode ?: requestedAudioCaptureMode.value
                            checkPermissionsAndLaunchRecognition(captureMode)
                        },
                        onNavigateToQueue = { recognitionId ->
                            viewModel.resetRecognitionResult()
                            onNavigateToQueueScreen(recognitionId)
                        }
                    )

                    RecognitionResult.NoSoundDetected -> NoSoundShield(
                        usedCaptureMode = lastUsedAudioCaptureMode ?: requestedAudioCaptureMode.value,
                        usedAltDeviceSoundSource = preferences?.useAltDeviceSoundSource ?: false,
                        onDismissClick = viewModel::resetRecognitionResult,
                        onRetryWithModeClick = ::checkPermissionsAndLaunchRecognition,
                    )

                    is RecognitionResult.ScheduledOffline -> ScheduledOfflineShield(
                        recognitionTask = result.recognitionTask,
                        onDismissClick = viewModel::resetRecognitionResult,
                        onNavigateToQueue = { recognitionId ->
                            viewModel.resetRecognitionResult()
                            onNavigateToQueueScreen(recognitionId)
                        }
                    )

                    is RecognitionResult.Error -> when (val remoteError = result.remoteError) {
                        RemoteRecognitionResult.Error.BadConnection -> BadConnectionShield(
                            recognitionTask = result.recognitionTask,
                            onDismissClick = viewModel::resetRecognitionResult,
                            onRetryClick = {
                                val captureMode = lastUsedAudioCaptureMode ?: requestedAudioCaptureMode.value
                                checkPermissionsAndLaunchRecognition(captureMode)
                            },
                            onNavigateToQueue = { recognitionId ->
                                viewModel.resetRecognitionResult()
                                onNavigateToQueueScreen(recognitionId)
                            }
                        )

                        is RemoteRecognitionResult.Error.BadRecording -> FatalErrorShield(
                            title = stringResource(StringsR.string.result_title_recording_error),
                            message = stringResource(StringsR.string.result_message_recording_error),
                            moreInfo = remoteError.getErrorInfo(),
                            recognitionTask = result.recognitionTask,
                            onDismissClick = viewModel::resetRecognitionResult,
                            onRetryClick = {
                                val captureMode = lastUsedAudioCaptureMode ?: requestedAudioCaptureMode.value
                                checkPermissionsAndLaunchRecognition(captureMode)
                            },
                            onNavigateToQueue = { recognitionId ->
                                viewModel.resetRecognitionResult()
                                onNavigateToQueueScreen(recognitionId)
                            }
                        )

                        is RemoteRecognitionResult.Error.HttpError -> FatalErrorShield(
                            title = stringResource(StringsR.string.result_title_bad_network_response),
                            message = stringResource(StringsR.string.result_message_bad_network_response),
                            moreInfo = remoteError.getErrorInfo(),
                            recognitionTask = result.recognitionTask,
                            onDismissClick = viewModel::resetRecognitionResult,
                            onRetryClick = {
                                val captureMode = lastUsedAudioCaptureMode ?: requestedAudioCaptureMode.value
                                checkPermissionsAndLaunchRecognition(captureMode)
                            },
                            onNavigateToQueue = { recognitionId ->
                                viewModel.resetRecognitionResult()
                                onNavigateToQueueScreen(recognitionId)
                            }
                        )

                        is RemoteRecognitionResult.Error.UnhandledError -> FatalErrorShield(
                            title = stringResource(StringsR.string.result_title_internal_error),
                            message = stringResource(StringsR.string.result_message_internal_error),
                            moreInfo = remoteError.getErrorInfo(),
                            recognitionTask = result.recognitionTask,
                            onDismissClick = viewModel::resetRecognitionResult,
                            onRetryClick = {
                                val captureMode = lastUsedAudioCaptureMode ?: requestedAudioCaptureMode.value
                                checkPermissionsAndLaunchRecognition(captureMode)
                            },
                            onNavigateToQueue = { recognitionId ->
                                viewModel.resetRecognitionResult()
                                onNavigateToQueueScreen(recognitionId)
                            }
                        )

                        is RemoteRecognitionResult.Error.AuthError -> AuthErrorShield(
                            recognitionTask = result.recognitionTask,
                            onDismissClick = viewModel::resetRecognitionResult,
                            onNavigateToQueue = { recognitionId ->
                                viewModel.resetRecognitionResult()
                                onNavigateToQueueScreen(recognitionId)
                            },
                            onNavigateToPreferences = {
                                viewModel.resetRecognitionResult()
                                onNavigateToPreferencesScreen()
                            }
                        )

                        is RemoteRecognitionResult.Error.ApiUsageLimited -> ApiUsageLimitedShield(
                            recognitionTask = result.recognitionTask,
                            onDismissClick = viewModel::resetRecognitionResult,
                            onNavigateToQueue = { recognitionId ->
                                viewModel.resetRecognitionResult()
                                onNavigateToQueueScreen(recognitionId)
                            },
                            onNavigateToPreferences = {
                                viewModel.resetRecognitionResult()
                                onNavigateToPreferencesScreen()
                            }
                        )
                    }
                }

                RecognitionStatus.Ready,
                is RecognitionStatus.Recognizing -> {}
            }
        }

        // optional vibration effect
        if (preferences.vibrateOnResult()) {
            val vibrated = rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(recognizeStatus) {
                val status = recognizeStatus
                if (status !is RecognitionStatus.Done) {
                    vibrated.value = false
                    return@LaunchedEffect
                }
                if (vibrated.value) return@LaunchedEffect
                when (status.result) {
                    is RecognitionResult.Success,
                    is RecognitionResult.ScheduledOffline -> viewModel.vibrateSuccess()

                    is RecognitionResult.NoMatches,
                    is RecognitionResult.NoSoundDetected,
                    is RecognitionResult.Error -> viewModel.vibrateFailure()
                }
                vibrated.value = true
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val running = autoRecognitionState.running
            val expandedOnStart = rememberAutoRecognitionExpandedOnStart(running)
            val offlinePopupWanted = isOffline && recognizeStatus.isNotDone()
            val fabExpanded = running && (expandedOnStart || !offlinePopupWanted)
            val showOfflinePopup = offlinePopupWanted && !fabExpanded
            AnimatedVisibility(
                visible = showOfflinePopup,
                enter = enterTransitionBottomPopup,
                exit = exitTransitionBottomPopup,
                modifier = Modifier.align(Alignment.Center),
            ) {
                OfflineModePopup()
            }
            AutoRecognitionToggle(
                running = running,
                expanded = fabExpanded,
                onClick = ::toggleAutoRecognition,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private val enterTransitionButton = fadeIn(
    tween(
        durationMillis = animationDurationButton,
        delayMillis = animationDurationShield
    )
)

private val exitTransitionButton = fadeOut(
    tween(
        durationMillis = animationDurationButton,
        delayMillis = 0
    )
)

private val enterTransitionBottomPopup = fadeIn(
    animationSpec = tween(
        durationMillis = animationDurationButton / 2,
        delayMillis = animationDurationButton
    )
) + scaleIn(
    animationSpec = tween(
        durationMillis = animationDurationButton / 2,
        delayMillis = animationDurationButton
    ),
    initialScale = 0.2f
)

private val exitTransitionBottomPopup = fadeOut(
    animationSpec = tween(durationMillis = animationDurationButton / 2)
) + scaleOut(
    animationSpec = tween(durationMillis = animationDurationButton / 2),
    targetScale = 0.2f
)

private val transitionSpecShield:
    AnimatedContentTransitionScope<RecognitionStatus>.() -> ContentTransform = {
        fadeIn(
            animationSpec = tween(
                durationMillis = animationDurationShield,
                delayMillis = animationDurationButton
            )
        ).togetherWith(
            fadeOut(
                animationSpec = tween(
                    durationMillis = animationDurationShield,
                    delayMillis = 0
                )
            )
        ).using(
            SizeTransform { _, _ ->
                snap(delayMillis = animationDurationShield)
            }
        )
    }

private fun RecognitionStatus.isDone() = this is RecognitionStatus.Done
private fun RecognitionStatus.isNotDone() = !isDone()

@Composable
private fun AutoRecognitionToggle(
    running: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(StringsR.string.quick_tile_auto_recognition)
    val clickLabel = stringResource(
        if (running) {
            StringsR.string.notification_auto_recognition_action_turn_off
        } else {
            StringsR.string.notification_auto_recognition_action_turn_on
        },
    )
    Box(
        modifier = modifier.clearAndSetSemantics {
            role = Role.Switch
            contentDescription = title
            toggleableState = if (running) ToggleableState.On else ToggleableState.Off
            onClick(label = clickLabel) {
                onClick()
                true
            }
        },
    ) {
        ExtendedFloatingActionButton(
            expanded = expanded,
            text = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            icon = {
                Icon(
                    painter = painterResource(UiR.drawable.rounded_autorenew_rotated_24),
                    contentDescription = null,
                )
            },
            onClick = onClick,
            modifier = Modifier.widthIn(max = AutoRecognitionFabMaxWidth),
            containerColor = if (running) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (running) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (running) 3.dp else 1.dp,
                pressedElevation = 3.dp,
                focusedElevation = 3.dp,
                hoveredElevation = 3.dp,
            ),
            shape = CircleShape,
        )
    }
}

@Composable
private fun rememberAutoRecognitionExpandedOnStart(running: Boolean): Boolean {
    var expandedOnStart by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (!running) {
            expandedOnStart = false
            return@LaunchedEffect
        }
        expandedOnStart = true
        delay(AutoRecognitionFabExpandedOnStart)
        expandedOnStart = false
    }
    return expandedOnStart
}

private val AutoRecognitionFabMaxWidth = 250.dp
private val AutoRecognitionFabExpandedOnStart = 3.seconds

@Composable
private fun getButtonTitle(recognitionStatus: RecognitionStatus, skipReady: Boolean): String {
    return when (recognitionStatus) {
        RecognitionStatus.Ready -> if (skipReady) {
            " "
        } else {
            stringResource(StringsR.string.main_screen_tap_to_recognize)
        }

        is RecognitionStatus.Recognizing -> if (recognitionStatus.extraTime) {
            stringResource(StringsR.string.main_screen_listening_extra_time)
        } else {
            stringResource(StringsR.string.main_screen_listening)
        }

        is RecognitionStatus.Done -> " "
    }
}

@Stable
private fun UserPreferences?.vibrateOnTap() = this?.hapticFeedback?.vibrateOnTap == true

@Stable
private fun UserPreferences?.vibrateOnResult() = this?.hapticFeedback?.vibrateOnResult == true

private fun RemoteRecognitionResult.Error.getErrorInfo() = when (this) {
    RemoteRecognitionResult.Error.BadConnection,
    is RemoteRecognitionResult.Error.AuthError,
    is RemoteRecognitionResult.Error.ApiUsageLimited -> ""

    is RemoteRecognitionResult.Error.HttpError -> "Code:\n$code\n\nMessage:\n$message"
    is RemoteRecognitionResult.Error.BadRecording ->
        "Message:\n$message\n\nCause:\n${cause?.stackTraceToString()}"

    is RemoteRecognitionResult.Error.UnhandledError ->
        "Message:\n$message\n\nCause:\n${cause?.stackTraceToString()}"
}
