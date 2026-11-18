package com.mrsep.musicrecognizer.feature.track.presentation.track

import androidx.compose.runtime.State
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.common.APP_DEEP_LINK_BASE
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.trackEntry(
    isExpandedScreen: State<Boolean>,
    onBackPressed: () -> Unit,
    onTrackDeleted: () -> Unit,
    onRetryRequested: () -> Unit,
    onNavigateToLyrics: (trackId: String) -> Unit,
    hideSplashScreen: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<TrackRoute>(metadata = metadata) { route ->
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        TrackScreen(
            route = route,
            isExpandedScreen = isExpandedScreen.value,
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
            onNavigateToLyricsScreen = { trackId ->
                lifecycle.ifResumed { onNavigateToLyrics(trackId) }
            },
            onRetryRequested = { lifecycle.ifResumed(onRetryRequested) },
            onTrackDeleted = { lifecycle.ifResumed(onTrackDeleted) },
        )
    }
}

@Serializable
data class TrackRoute(
    val trackId: String,
    val allowRetry: Boolean = false,
) : NavKey {

    fun deepLinkUri(): String = "$APP_DEEP_LINK_BASE/track/$trackId?allowRetry=$allowRetry"

    companion object {
        const val DEEP_LINK_PATTERN: String = "$APP_DEEP_LINK_BASE/track/{trackId}?allowRetry={allowRetry}"
    }
}
