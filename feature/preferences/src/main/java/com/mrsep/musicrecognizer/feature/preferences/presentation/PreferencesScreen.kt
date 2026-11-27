package com.mrsep.musicrecognizer.feature.preferences.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrsep.musicrecognizer.core.common.util.getDefaultVibrator
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.ui.components.LoadingStub
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceClickableItem
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceGroup
import com.mrsep.musicrecognizer.core.ui.components.preferences.PreferenceSwitchItem
import com.mrsep.musicrecognizer.core.ui.resources.titleId
import com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig.AcrCloudServiceDialog
import com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig.AuddServiceDialog
import com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig.ShazamServiceDialog
import com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig.getTitle
import com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig.rememberAcrCloudPreferencesState
import com.mrsep.musicrecognizer.feature.preferences.presentation.serviceconfig.rememberAuddPreferencesState
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun PreferencesScreen(
    viewModel: PreferencesViewModel = hiltViewModel(),
    showDeveloperOptions: Boolean,
    onNavigateToAboutScreen: () -> Unit,
    onNavigateToBackupRestoreScreen: () -> Unit,
    onNavigateToDeveloperScreen: () -> Unit,
) {
    val context = LocalContext.current
    val uiStateInFlow by viewModel.uiFlow.collectAsStateWithLifecycle()
    val topBarBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    when (val uiState = uiStateInFlow) {
        is PreferencesUiState.Loading -> Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .fillMaxSize()
        ) {
            PreferencesTopBar(scrollBehavior = topBarBehaviour)
            LoadingStub(modifier = Modifier.fillMaxSize())
        }

        is PreferencesUiState.Success -> {
            Column(
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.surface)
                    .fillMaxSize()
            ) {
                PreferencesTopBar(scrollBehavior = topBarBehaviour)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(topBarBehaviour.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                ) {
                    PreferenceGroup(title = stringResource(StringsR.string.pref_group_recognition)) {
                        val currentProvider = uiState.preferences.currentRecognitionProvider
                        var showServiceDialog by rememberSaveable { mutableStateOf(false) }
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_recognition_provider),
                            subtitle = uiState.preferences.currentRecognitionProvider.getTitle(),
                            onItemClick = { showServiceDialog = true }
                        )
                        if (showServiceDialog) {
                            var visibleProvider by rememberSaveable(currentProvider) {
                                mutableStateOf(currentProvider)
                            }
                            when (visibleProvider) {
                                RecognitionProvider.Audd -> {
                                    val state = rememberAuddPreferencesState(
                                        uiState.preferences.auddConfig
                                    )
                                    AuddServiceDialog(
                                        state = state,
                                        currentProvider = visibleProvider,
                                        onProviderChanged = { visibleProvider = it },
                                        onSaveClick = {
                                            viewModel.setAuddConfig(state.currentConfig)
                                            viewModel.setRecognitionProvider(visibleProvider)
                                            showServiceDialog = false
                                        },
                                        onDismissClick = { showServiceDialog = false }
                                    )
                                }

                                RecognitionProvider.AcrCloud -> {
                                    val state = rememberAcrCloudPreferencesState(
                                        uiState.preferences.acrCloudConfig
                                    )
                                    AcrCloudServiceDialog(
                                        state = state,
                                        currentProvider = visibleProvider,
                                        onProviderChanged = { visibleProvider = it },
                                        onSaveClick = {
                                            if (state.isConfigValid) {
                                                viewModel.setAcrCloudConfig(state.currentConfig)
                                                viewModel.setRecognitionProvider(visibleProvider)
                                                showServiceDialog = false
                                            } else {
                                                state.showErrors()
                                            }
                                        },
                                        onDismissClick = { showServiceDialog = false }
                                    )
                                }

                                RecognitionProvider.Shazam -> {
                                    ShazamServiceDialog(
                                        currentProvider = visibleProvider,
                                        onProviderChanged = { visibleProvider = it },
                                        onSaveClick = {
                                            viewModel.setRecognitionProvider(visibleProvider)
                                            showServiceDialog = false
                                        },
                                        onDismissClick = { showServiceDialog = false }
                                    )
                                }
                            }
                        }
                        var showPolicyDialog by rememberSaveable { mutableStateOf(false) }
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_fallback_policy),
                            subtitle = stringResource(StringsR.string.pref_subtitle_fallback_policy),
                            onItemClick = { showPolicyDialog = true }
                        )
                        if (showPolicyDialog) {
                            FallbackPolicyDialog(
                                fallbackPolicy = uiState.preferences.fallbackPolicy,
                                onFallbackPolicyChanged = viewModel::setFallbackPolicy,
                                autoDeleteSavedRecordingOnMatch = uiState.preferences.autoDeleteSavedRecordingOnMatch,
                                onAutoDeleteSavedRecordingOnMatchChanged = viewModel::setAutoDeleteSavedRecordingOnMatch,
                                onDismissClick = { showPolicyDialog = false }
                            )
                        }
                        var showAudioSourceDialog by rememberSaveable { mutableStateOf(false) }
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_audio_source),
                            subtitle = stringResource(StringsR.string.pref_subtitle_audio_source),
                            onItemClick = { showAudioSourceDialog = true }
                        )
                        if (showAudioSourceDialog) {
                            AudioSourceDialog(
                                defaultAudioCaptureMode = uiState.preferences.defaultAudioCaptureMode,
                                mainButtonLongPressAudioCaptureMode = uiState.preferences.mainButtonLongPressAudioCaptureMode,
                                useAltDeviceSoundSource = uiState.preferences.useAltDeviceSoundSource,
                                onChangeDefaultAudioCaptureMode = viewModel::setDefaultAudioCaptureMode,
                                onChangeMainButtonLongPressAudioCaptureMode = viewModel::setMainButtonLongPressAudioCaptureMode,
                                onChangeUseAltDeviceSoundSource = viewModel::setUseAltDeviceSoundSource,
                                onDismissClick = { showAudioSourceDialog = false }
                            )
                        }
                        PreferenceSwitchItem(
                            title = stringResource(StringsR.string.pref_title_prerecording_buffer),
                            subtitle = stringResource(StringsR.string.pref_subtitle_prerecording_buffer),
                            checked = uiState.preferences.usePrerecording,
                            onClick = { viewModel.setUsePrerecording(!uiState.preferences.usePrerecording) },
                        )
                        PreferenceSwitchItem(
                            title = stringResource(StringsR.string.pref_title_recognize_on_startup),
                            onClick = {
                                viewModel.setRecognizeOnStartup(!uiState.preferences.recognizeOnStartup)
                            },
                            checked = uiState.preferences.recognizeOnStartup
                        )
                    }
                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                    Spacer(Modifier.height(16.dp))
                    PreferenceGroup(title = stringResource(StringsR.string.pref_group_notifications)) {
                        SwitchWithRecognitionPermissionRequest(
                            title = stringResource(StringsR.string.pref_title_notification_service),
                            subtitle = stringResource(StringsR.string.pref_subtitle_notification_service),
                            setChecked = viewModel::setNotificationServiceEnabled,
                            checked = uiState.preferences.notificationServiceEnabled
                        )
                        var floatingButtonRequested by rememberSaveable { mutableStateOf(false) }
                        LifecycleStartEffect(
                            floatingButtonRequested,
                            uiState.preferences.floatingButtonEnabled,
                            uiState.preferences.notificationServiceEnabled
                        ) {
                            if (
                                floatingButtonRequested &&
                                !uiState.preferences.floatingButtonEnabled &&
                                uiState.preferences.notificationServiceEnabled &&
                                Settings.canDrawOverlays(context)
                            ) {
                                floatingButtonRequested = false
                                viewModel.setFloatingButtonEnabled(true)
                            }
                            onStopOrDispose {}
                        }
                        SwitchWithRecognitionPermissionRequest(
                            title = stringResource(StringsR.string.pref_title_floating_button),
                            subtitle = stringResource(StringsR.string.pref_subtitle_floating_button),
                            setChecked = { checked ->
                                if (checked) {
                                    viewModel.setNotificationServiceEnabled(true)
                                    if (!Settings.canDrawOverlays(context)) {
                                        floatingButtonRequested = true
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            "package:${context.packageName}".toUri(),
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        floatingButtonRequested = false
                                        viewModel.setFloatingButtonEnabled(true)
                                    }
                                } else {
                                    floatingButtonRequested = false
                                    viewModel.setFloatingButtonEnabled(false)
                                }
                            },
                            checked = uiState.preferences.floatingButtonEnabled
                        )
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_manage_notifications),
                            onItemClick = {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).run {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    context.startActivity(this)
                                }
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                    Spacer(Modifier.height(16.dp))
                    PreferenceGroup(title = stringResource(StringsR.string.pref_group_appearance)) {
                        var showThemeDialog by rememberSaveable { mutableStateOf(false) }
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_theme),
                            onItemClick = { showThemeDialog = true }
                        )
                        if (showThemeDialog) {
                            ThemeDialog(
                                onDismissClick = { showThemeDialog = false },
                                themeMode = uiState.preferences.themeMode,
                                useDynamicColors = uiState.preferences.dynamicColorsEnabled,
                                useArtworkBasedTheme = uiState.preferences.artworkBasedThemeEnabled,
                                usePureBlackForDarkTheme = uiState.preferences.usePureBlackForDarkTheme,
                                onThemeModeSelected = viewModel::setThemeMode,
                                onDynamicColorsEnabled = viewModel::setDynamicColorsEnabled,
                                onPureBlackEnabled = viewModel::setUsePureBlackForDarkTheme,
                                onArtworkBasedThemeEnabled = viewModel::setArtworkBasedThemeEnabled
                            )
                        }
                        var showServicesDialog by rememberSaveable { mutableStateOf(false) }
                        val requiredMusicServices = uiState.preferences.requiredMusicServices
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_music_services_links),
                            subtitle = requiredMusicServices.getEnumerationForSubtitle(limit = 3),
                            onItemClick = { showServicesDialog = true }
                        )
                        val container = LocalWindowInfo.current.containerDpSize
                        val dialogWidth = (container.width - 80.dp)
                            .coerceIn(320.dp, 560.dp)
                            .coerceAtMost(container.width)
                        val dialogHeight = 640.dp
                            .coerceAtMost(container.height)
                        if (showServicesDialog) {
                            RequiredServicesDialog(
                                modifier = Modifier
                                    .widthIn(max = dialogWidth)
                                    .heightIn(max = dialogHeight),
                                requiredServices = requiredMusicServices,
                                onRequiredServicesChanged = viewModel::setRequiredMusicServices,
                                onDismissClick = { showServicesDialog = false }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                    Spacer(Modifier.height(16.dp))
                    PreferenceGroup(title = stringResource(StringsR.string.pref_group_misc)) {
                        val vibratorAvailable = remember {
                            context.getDefaultVibrator().hasVibrator()
                        }
                        if (vibratorAvailable) {
                            var showHapticDialog by rememberSaveable { mutableStateOf(false) }
                            PreferenceClickableItem(
                                title = stringResource(StringsR.string.pref_title_vibration_feedback),
                                onItemClick = { showHapticDialog = true }
                            )
                            if (showHapticDialog) {
                                HapticFeedbackDialog(
                                    hapticFeedback = uiState.preferences.hapticFeedback,
                                    onHapticFeedbackChanged = viewModel::setHapticFeedback,
                                    onDismissClick = { showHapticDialog = false },
                                )
                            }
                        }
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_backup_and_restore),
                            subtitle = stringResource(StringsR.string.pref_subtitle_backup_and_restore),
                            onItemClick = onNavigateToBackupRestoreScreen
                        )
                        PreferenceClickableItem(
                            title = stringResource(StringsR.string.pref_title_about_app),
                            onItemClick = onNavigateToAboutScreen
                        )
                        if (showDeveloperOptions) {
                            PreferenceClickableItem(
                                title = "Developer options",
                                onItemClick = onNavigateToDeveloperScreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Stable
@Composable
private fun List<MusicService>.getEnumerationForSubtitle(limit: Int) = when (size) {
    0 -> stringResource(StringsR.string.pref_subtitle_no_selected_services)
    in 1..limit -> {
        map { service -> stringResource(service.titleId()) }.joinToString(", ")
    }

    else -> {
        val limited = take(limit)
            .map { service -> stringResource(service.titleId()) }.joinToString(", ")
        stringResource(StringsR.string.pref_subtitle_format_more_services, limited, size - limit)
    }
}