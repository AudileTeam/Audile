package com.mrsep.musicrecognizer.feature.preferences.presentation

import android.content.Intent
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.mrsep.musicrecognizer.core.common.util.lifecycleIsResumed

object PreferencesScreen {

    private const val ROOT_DEEP_LINK = "app://mrsep.musicrecognizer.com"
    const val ROUTE = "preferences"

    fun NavGraphBuilder.preferencesScreen(
        showDeveloperOptions: Boolean,
        onNavigateToAboutScreen: (from: NavBackStackEntry) -> Unit,
        onNavigateToBackupRestoreScreen: (from: NavBackStackEntry) -> Unit,
        onNavigateToDeveloperScreen: (from: NavBackStackEntry) -> Unit
    ) {
        composable(
            route = ROUTE,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "$ROOT_DEEP_LINK/$ROUTE"
                    action = Intent.ACTION_VIEW
                }
            )
        ) { backStackEntry ->
            PreferencesScreen(
                showDeveloperOptions = showDeveloperOptions,
                onNavigateToAboutScreen = { onNavigateToAboutScreen(backStackEntry) },
                onNavigateToBackupRestoreScreen = { onNavigateToBackupRestoreScreen(backStackEntry) },
                onNavigateToDeveloperScreen = { onNavigateToDeveloperScreen(backStackEntry) }
            )
        }
    }

    fun NavController.navigateToPreferencesScreen(
        from: NavBackStackEntry,
        navOptions: NavOptions? = null
    ) {
        if (from.lifecycleIsResumed) {
            this.navigate(route = ROUTE, navOptions = navOptions)
        }
    }

    fun createDeepLink(): String {
        return "$ROOT_DEEP_LINK/$ROUTE"
    }
}
