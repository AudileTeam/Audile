package com.mrsep.musicrecognizer.feature.developermode.presentation

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.developerEntry(
    onBackPressed: () -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<DeveloperRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DeveloperScreen(
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
        )
    }
}

@Serializable
data object DeveloperRoute : NavKey
