package com.mrsep.musicrecognizer.presentation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.core.util.Consumer
import androidx.lifecycle.withResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.navigation.navigation
import com.mrsep.musicrecognizer.BuildConfig
import com.mrsep.musicrecognizer.feature.backup.presentation.BackupRestoreScreenNavigation.backupRestoreScreen
import com.mrsep.musicrecognizer.feature.backup.presentation.BackupRestoreScreenNavigation.navigateToBackupRestoreScreen
import com.mrsep.musicrecognizer.feature.developermode.presentation.DeveloperScreenNavigation.developerScreen
import com.mrsep.musicrecognizer.feature.developermode.presentation.DeveloperScreenNavigation.navigateToDeveloperScreen
import com.mrsep.musicrecognizer.feature.library.presentation.library.LibraryScreen
import com.mrsep.musicrecognizer.feature.library.presentation.library.LibraryScreen.libraryScreen
import com.mrsep.musicrecognizer.feature.library.presentation.search.LibrarySearchScreen.librarySearchScreen
import com.mrsep.musicrecognizer.feature.library.presentation.search.LibrarySearchScreen.navigateToLibrarySearchScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesScreen.navigateToPreferencesScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesScreen.preferencesScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.AboutScreenNavigation.aboutScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.AboutScreenNavigation.navigateToAboutScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.AppLicenseScreen.appLicenseScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.AppLicenseScreen.navigateToAppLicenseScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.SoftwareDetailsScreen.softwareDetailsScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.SoftwareDetailsScreen.navigateToSoftwareDetailsScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.SoftwareScreenNavigation.softwareScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.SoftwareScreenNavigation.navigateToSoftwareScreen
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.RecognitionQueueScreen
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.RecognitionQueueScreen.navigateToQueueScreen
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.RecognitionQueueScreen.queueScreen
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionRequest
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionScreen
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionScreen.recognitionScreen
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.LyricsScreen.lyricsScreen
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.LyricsScreen.navigateToLyricsScreen
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackScreen
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackScreen.navigateToTrackScreen
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackScreen.trackScreen

private const val SCREEN_TRANSITION_DURATION = 250

@Composable
internal fun AppNavigation(
    unviewedTracksCount: State<Int>,
    pendingRecognitionRequest: RecognitionRequest?,
    setPendingRecognitionRequest: (RecognitionRequest?) -> Unit,
    shouldShowNavRail: Boolean,
    isExpandedScreen: Boolean,
    hideSplashScreen: () -> Unit
) {
    val outerNavController = rememberNavController()
    val innerNavController = rememberNavController()
    val optionalOuterEntry by outerNavController.currentBackStackEntryAsState()
    val optionalInnerEntry by innerNavController.currentBackStackEntryAsState()
    // Handle deep links manually due to activity launch mode
    // https://developer.android.com/guide/navigation/design/deep-link#handle
    val activity = LocalActivity.current as ComponentActivity
    DisposableEffect(Unit) {
        val deeplinkListener = Consumer<Intent> { intent ->
            outerNavController.handleDeepLink(intent)
        }
        activity.addOnNewIntentListener(deeplinkListener)
        onDispose {
            activity.removeOnNewIntentListener(deeplinkListener)
        }
    }
    val isRecognitionRequested = pendingRecognitionRequest != null
    // Conditional navigation for recognition request
    LaunchedEffect(
        optionalOuterEntry,
        optionalInnerEntry,
        isRecognitionRequested,
    ) {
        // optionalInnerEntry can be null, for example if nav graph is created with track deeplink
        val outerEntry = optionalOuterEntry ?: return@LaunchedEffect
        val innerEntry = optionalInnerEntry
        // navigate to recognition screen if recognition requested
        if (isRecognitionRequested) {
            if (outerEntry.destination.route != BAR_HOST_ROUTE) {
                outerNavController.popBackStack(BAR_HOST_ROUTE, inclusive = false)
                return@LaunchedEffect
            } else if (innerEntry != null && innerEntry.destination.route != RecognitionScreen.ROUTE) {
                innerNavController.navigate(TopLevelDestination.Recognition.route) {
                    popUpTo(innerNavController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                return@LaunchedEffect
            }
        }
        // hide splash screen if result screen is resumed
        if (outerEntry.destination.route == BAR_HOST_ROUTE) {
            optionalInnerEntry?.lifecycle?.withResumed(hideSplashScreen)
        } else {
            outerEntry.lifecycle.withResumed(hideSplashScreen)
        }
    }
    NavHost(
        navController = outerNavController,
        startDestination = BAR_HOST_ROUTE,
        enterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        exitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        popEnterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        popExitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        predictivePopEnterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        predictivePopExitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        modifier = Modifier.fillMaxSize(),
    ) {
        barNavHost(
            unviewedTracksCount = unviewedTracksCount,
            pendingRecognitionRequest = pendingRecognitionRequest,
            setPendingRecognitionRequest = setPendingRecognitionRequest,
            shouldShowNavRail = shouldShowNavRail,
            outerNavController = outerNavController,
            innerNavController = innerNavController
        )
        librarySearchScreen(
            onBackPressed = outerNavController::navigateUp,
            onTrackClick = { trackId, from ->
                outerNavController.navigateToTrackScreen(trackId = trackId, from = from)
            }
        )
        navigation(
            route = "nav_graph_track",
            startDestination = TrackScreen.ROUTE,
        ) {
            trackScreen(
                isExpandedScreen = isExpandedScreen,
                onBackPressed = outerNavController::navigateUp,
                onNavigateToLyricsScreen = { trackId, from ->
                    outerNavController.navigateToLyricsScreen(trackId = trackId, from = from)
                },
                onRetryRequested = { setPendingRecognitionRequest(RecognitionRequest.Retry) }
            )
            lyricsScreen(onBackPressed = outerNavController::navigateUp)
        }
        aboutScreen(
            onBackPressed = outerNavController::navigateUp,
            onNavigateToSoftwareScreen = { from ->
                outerNavController.navigateToSoftwareScreen(from)
            },
            onNavigateToAppLicenseScreen = { from ->
                outerNavController.navigateToAppLicenseScreen(from)
            }
        )
        softwareScreen(
            onBackPressed = outerNavController::navigateUp,
            onNavigateToSoftwareDetailsScreen = { uniqueId, from ->
                outerNavController.navigateToSoftwareDetailsScreen(uniqueId, from)
            }
        )
        softwareDetailsScreen(onBackPressed = outerNavController::navigateUp)
        appLicenseScreen(onBackPressed = outerNavController::navigateUp)
        backupRestoreScreen(onBackPressed = outerNavController::navigateUp)
        developerScreen(onBackPressed = outerNavController::navigateUp)
    }
}

private const val BAR_HOST_ROUTE = "bar_host"

private fun NavGraphBuilder.barNavHost(
    unviewedTracksCount: State<Int>,
    pendingRecognitionRequest: RecognitionRequest?,
    setPendingRecognitionRequest: (RecognitionRequest?) -> Unit,
    shouldShowNavRail: Boolean,
    outerNavController: NavController,
    innerNavController: NavHostController
) {
    composable(BAR_HOST_ROUTE) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
            ) {
                if (shouldShowNavRail) {
                    AppNavigationRail(
                        unviewedTracksCount = unviewedTracksCount,
                        navController = innerNavController
                    )
                    VerticalDivider(modifier = Modifier.alpha(0.2f))
                }
                BarNavHost(
                    pendingRecognitionRequest = pendingRecognitionRequest,
                    setPendingRecognitionRequest = setPendingRecognitionRequest,
                    outerNavController = outerNavController,
                    innerNavController = innerNavController,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (shouldShowNavRail) {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                )
                            } else {
                                Modifier.consumeWindowInsets(
                                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                )
                            }
                        ),
                )
            }
            if (!shouldShowNavRail) {
                AppNavigationBar(
                    unviewedTracksCount = unviewedTracksCount,
                    navController = innerNavController
                )
            }
        }
    }
}

@Composable
private fun BarNavHost(
    pendingRecognitionRequest: RecognitionRequest?,
    setPendingRecognitionRequest: (RecognitionRequest?) -> Unit,
    outerNavController: NavController,
    innerNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = innerNavController,
        startDestination = TopLevelDestination.Recognition.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        exitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        popEnterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        popExitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        predictivePopEnterTransition = { fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
        predictivePopExitTransition = { fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION)) },
    ) {
        navigation(
            startDestination = RecognitionScreen.ROUTE,
            route = TopLevelDestination.Recognition.route
        ) {
            recognitionScreen(
                pendingRecognitionRequest = pendingRecognitionRequest,
                onResetPendingRecognitionRequest = { setPendingRecognitionRequest(null) },
                onNavigateToTrackScreen = { trackId, from ->
                    outerNavController.navigateToTrackScreen(
                        trackId = trackId,
                        isRetryAvailable = true,
                        from = from
                    )
                },
                onNavigateToQueueScreen = { _, from ->
                    innerNavController.navigateToQueueScreen(
                        from = from,
                        navOptions = navOptions {
                            popUpTo(innerNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    )
                },
                onNavigateToPreferencesScreen = { from ->
                    innerNavController.navigateToPreferencesScreen(
                        from = from,
                        navOptions = navOptions {
                            popUpTo(innerNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    )
                }
            )
        }
        navigation(
            startDestination = LibraryScreen.ROUTE,
            route = TopLevelDestination.Library.route
        ) {
            libraryScreen(
                onTrackClick = { trackId, from ->
                    outerNavController.navigateToTrackScreen(trackId = trackId, from = from)
                },
                onTrackSearchClick = { from ->
                    outerNavController.navigateToLibrarySearchScreen(from = from)
                }
            )
        }
        navigation(
            startDestination = RecognitionQueueScreen.ROUTE,
            route = TopLevelDestination.Queue.route
        ) {
            queueScreen(
                onNavigateToTrackScreen = { trackId, from ->
                    outerNavController.navigateToTrackScreen(trackId = trackId, from = from)
                }
            )
        }
        navigation(
            startDestination = PreferencesScreen.ROUTE,
            route = TopLevelDestination.Preferences.route
        ) {
            preferencesScreen(
                showDeveloperOptions = BuildConfig.DEV_OPTIONS,
                onNavigateToAboutScreen = { from ->
                    outerNavController.navigateToAboutScreen(from)
                },
                onNavigateToBackupRestoreScreen = { from ->
                    outerNavController.navigateToBackupRestoreScreen(from)
                },
                onNavigateToDeveloperScreen = { from ->
                    outerNavController.navigateToDeveloperScreen(from)
                }
            )
        }
    }
}
