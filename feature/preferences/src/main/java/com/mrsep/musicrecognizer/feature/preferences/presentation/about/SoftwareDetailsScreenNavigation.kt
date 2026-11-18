package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.softwareDetailsEntry(
    onBackPressed: () -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<SoftwareDetailsRoute> { key ->
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        SoftwareDetailsScreen(
            uniqueId = key.uniqueId,
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
        )
    }
}

@Serializable
data class SoftwareDetailsRoute(val uniqueId: String) : NavKey
