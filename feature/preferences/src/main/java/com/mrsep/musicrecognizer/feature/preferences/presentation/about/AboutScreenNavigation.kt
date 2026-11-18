package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.aboutEntry(
    onBackPressed: () -> Unit,
    onNavigateToSoftware: () -> Unit,
    onNavigateToAppLicense: () -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<AboutRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AboutScreen(
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
            onNavigateToSoftwareScreen = { lifecycle.ifResumed(onNavigateToSoftware) },
            onNavigateToAppLicenseScreen = { lifecycle.ifResumed(onNavigateToAppLicense) },
        )
    }
}

@Serializable
data object AboutRoute : NavKey
