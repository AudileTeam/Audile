package com.mrsep.musicrecognizer.feature.library.presentation.library

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

fun EntryProviderScope<NavKey>.libraryEntry(
    onNavigateToTrack: (trackId: String) -> Unit,
    onNavigateToLibrarySearch: () -> Unit,
    barHeight: IntState,
    railWidth: IntState,
    showRail: State<Boolean>,
    hideSplashScreen: () -> Unit,
) {
    entry<LibraryRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AppNavigationPadding(
            barHeight = barHeight,
            railWidth = railWidth,
            showRail = showRail,
        ) {
            LibraryScreen(
                onNavigateToTrack = { trackId ->
                    lifecycle.ifResumed { onNavigateToTrack(trackId) }
                },
                onNavigateToLibrarySearch = { lifecycle.ifResumed(onNavigateToLibrarySearch) },
            )
        }
    }
}

@Serializable
data object LibraryRoute : NavKey {

    fun deepLinkUri(): String = "$APP_DEEP_LINK_BASE/library"
}
