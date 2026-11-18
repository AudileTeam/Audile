package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.common.APP_DEEP_LINK_BASE
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.lyricsEntry(
    onBackPressed: () -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<LyricsRoute> { route ->
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LyricsScreen(
            route = route,
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
        )
    }
}

@Serializable
data class LyricsRoute(val trackId: String) : NavKey {

    fun deepLinkUri(): String = "$APP_DEEP_LINK_BASE/lyrics/$trackId"

    companion object {
        const val DEEP_LINK_PATTERN: String = "$APP_DEEP_LINK_BASE/lyrics/{trackId}"
    }
}
