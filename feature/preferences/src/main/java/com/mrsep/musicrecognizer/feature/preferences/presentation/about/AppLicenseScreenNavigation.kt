package com.mrsep.musicrecognizer.feature.preferences.presentation.about

import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.mrsep.musicrecognizer.core.ui.navigation.HideSplashWhenResumed
import com.mrsep.musicrecognizer.core.ui.navigation.ifResumed
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.appLicenseEntry(
    onBackPressed: () -> Unit,
    hideSplashScreen: () -> Unit,
) {
    entry<AppLicenseRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AppLicenseScreen(
            onBackPressed = { lifecycle.ifResumed(onBackPressed) },
        )
    }
}

@Serializable
data object AppLicenseRoute : NavKey
