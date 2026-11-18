package com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen

import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.common.APP_DEEP_LINK_BASE
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.AppNavigationPadding
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.queueEntry(
    onNavigateToTrack: (trackId: String) -> Unit,
    barHeight: IntState,
    railWidth: IntState,
    showRail: State<Boolean>,
    hideSplashScreen: () -> Unit,
) {
    entry<QueueRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AppNavigationPadding(
            barHeight = barHeight,
            railWidth = railWidth,
            showRail = showRail,
        ) {
            QueueScreen(
                onNavigateToTrackScreen = { trackId ->
                    lifecycle.ifResumed { onNavigateToTrack(trackId) }
                },
            )
        }
    }
}

@Serializable
data object QueueRoute : NavKey {

    fun deepLinkUri(): String = "$APP_DEEP_LINK_BASE/queue"
}
