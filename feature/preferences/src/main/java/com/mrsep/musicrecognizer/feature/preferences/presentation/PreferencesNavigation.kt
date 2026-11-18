package com.mrsep.musicrecognizer.feature.preferences.presentation

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

fun EntryProviderScope<NavKey>.preferencesEntry(
    showDeveloperOptions: Boolean,
    onNavigateToAbout: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToDeveloper: () -> Unit,
    barHeight: IntState,
    railWidth: IntState,
    showRail: State<Boolean>,
    hideSplashScreen: () -> Unit,
) {
    entry<PreferencesRoute> {
        HideSplashWhenResumed(hideSplashScreen)
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        AppNavigationPadding(
            barHeight = barHeight,
            railWidth = railWidth,
            showRail = showRail,
        ) {
            PreferencesScreen(
                showDeveloperOptions = showDeveloperOptions,
                onNavigateToAboutScreen = { lifecycle.ifResumed(onNavigateToAbout) },
                onNavigateToBackupRestoreScreen = { lifecycle.ifResumed(onNavigateToBackupRestore) },
                onNavigateToDeveloperScreen = { lifecycle.ifResumed(onNavigateToDeveloper) },
            )
        }
    }
}

@Serializable
data object PreferencesRoute : NavKey {

    fun deepLinkUri(): String = "$APP_DEEP_LINK_BASE/preferences"
}
