package com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen

import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.AppNavigationPadding
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.recognitionEntry(
    pendingRecognitionRequest: State<RecognitionRequest?>,
    onResetPendingRecognitionRequest: () -> Unit,
    onNavigateToTrack: (trackId: String) -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    barHeight: IntState,
    railWidth: IntState,
    showRail: State<Boolean>,
    hideSplashScreen: () -> Unit,
) {
    entry<RecognitionRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AppNavigationPadding(
            barHeight = barHeight,
            railWidth = railWidth,
            showRail = showRail,
        ) {
            RecognitionScreen(
                pendingRecognitionRequest = pendingRecognitionRequest.value,
                onResetPendingRecognitionRequest = onResetPendingRecognitionRequest,
                onNavigateToTrackScreen = { trackId ->
                    lifecycle.ifResumed { onNavigateToTrack(trackId) }
                },
                // Queue deep link and in-app navigation ignore the recognition id for now.
                onNavigateToQueueScreen = { _ ->
                    lifecycle.ifResumed(onNavigateToQueue)
                },
                onNavigateToPreferencesScreen = {
                    lifecycle.ifResumed(onNavigateToPreferences)
                },
            )
        }
    }
}

@Serializable
data object RecognitionRoute : NavKey
