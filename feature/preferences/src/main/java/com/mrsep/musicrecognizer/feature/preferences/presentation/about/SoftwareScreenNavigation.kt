package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.softwareEntry(
    onBackPressed: () -> Unit,
    onNavigateToSoftwareDetails: (uniqueId: String) -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<SoftwareRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        SoftwareScreen(
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
            onNavigateToSoftwareDetailsScreen = { uniqueId ->
                lifecycle.ifResumed { onNavigateToSoftwareDetails(uniqueId) }
            },
        )
    }
}

@Serializable
data object SoftwareRoute : NavKey
