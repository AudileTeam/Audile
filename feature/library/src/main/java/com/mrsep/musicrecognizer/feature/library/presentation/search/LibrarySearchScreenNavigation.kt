package com.mrsep.musicrecognizer.feature.library.presentation.search

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.librarySearchEntry(
    onBackPressed: () -> Unit,
    onNavigateToTrack: (trackId: String) -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<LibrarySearchRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LibrarySearchScreen(
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
            onNavigateToTrack = { trackId ->
                lifecycle.ifResumed { onNavigateToTrack(trackId) }
            },
        )
    }
}

@Serializable
data object LibrarySearchRoute : NavKey
