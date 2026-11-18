package com.mrsep.musicrecognizer.presentation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.util.Consumer
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.mrsep.musicrecognizer.BuildConfig
import com.mrsep.musicrecognizer.feature.backup.presentation.BackupRestoreRoute
import com.mrsep.musicrecognizer.feature.backup.presentation.backupRestoreEntry
import com.mrsep.musicrecognizer.feature.developermode.presentation.DeveloperRoute
import com.mrsep.musicrecognizer.feature.developermode.presentation.developerEntry
import com.mrsep.musicrecognizer.feature.library.presentation.library.libraryEntry
import com.mrsep.musicrecognizer.feature.library.presentation.search.LibrarySearchRoute
import com.mrsep.musicrecognizer.feature.library.presentation.search.librarySearchEntry
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.AboutRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.AppLicenseRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.SoftwareDetailsRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.SoftwareRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.aboutEntry
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.appLicenseEntry
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.softwareDetailsEntry
import com.mrsep.musicrecognizer.feature.preferences.presentation.about.softwareEntry
import com.mrsep.musicrecognizer.feature.preferences.presentation.preferencesEntry
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.QueueRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.queueEntry
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionRequest
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.recognitionEntry
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.LyricsRoute
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.lyricsEntry
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackRoute
import com.mrsep.musicrecognizer.feature.track.presentation.track.trackEntry
import com.mrsep.musicrecognizer.presentation.navigation.Navigator
import com.mrsep.musicrecognizer.presentation.navigation.TrackPopStyle
import com.mrsep.musicrecognizer.presentation.navigation.parseAppDeepLink
import com.mrsep.musicrecognizer.presentation.navigation.rememberNavigationState

@Composable
internal fun AppNavigation(
    unviewedTracksCount: State<Int>,
    pendingRecognitionRequest: RecognitionRequest?,
    setPendingRecognitionRequest: (RecognitionRequest?) -> Unit,
    shouldShowNavRail: Boolean,
    isExpandedScreen: Boolean,
    restoreState: Boolean,
    hideSplashScreen: () -> Unit,
) {
    val activity = LocalActivity.current as ComponentActivity
    // Parsed once. Restored process must not seed from the launch intent again.
    val initialDeepLink = remember {
        if (restoreState) null else parseAppDeepLink(activity.intent)
    }
    val navigationState = rememberNavigationState(
        startRoute = TopLevelDestination.Recognition.key,
        topLevelRoutes = TopLevelDestination.keys,
        initial = initialDeepLink,
    )
    val navigator = remember(navigationState) { Navigator(navigationState) }
    // Warm singleTask intents only. The launch intent is not delivered here.
    DisposableEffect(activity, navigator) {
        val listener = Consumer<Intent> { intent ->
            val link = parseAppDeepLink(intent) ?: return@Consumer
            navigator.applyDeepLink(link)
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    // Entry content is recreated only when that tab's back stack changes.
    val pendingRequest = rememberUpdatedState(pendingRecognitionRequest)
    val expandedScreen = rememberUpdatedState(isExpandedScreen)
    val showRail = rememberUpdatedState(shouldShowNavRail)
    val barHeight = remember { mutableIntStateOf(0) }
    val railWidth = remember { mutableIntStateOf(0) }

    val visible = navigationState.backStacks.getValue(navigationState.topLevelRoute).last()
    LaunchedEffect(visible, pendingRecognitionRequest) {
        if (pendingRecognitionRequest != null && visible != RecognitionRoute) {
            navigator.openTopLevel(RecognitionRoute)
        }
    }

    val atTabRoot = visible == navigationState.topLevelRoute
    val barVisible = atTabRoot && !shouldShowNavRail
    val railVisible = atTabRoot && shouldShowNavRail

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            entries = navigationState.toEntries(
                entryProvider = entryProvider {
                    recognitionEntry(
                        pendingRecognitionRequest = pendingRequest,
                        onResetPendingRecognitionRequest = { setPendingRecognitionRequest(null) },
                        onNavigateToTrack = { trackId ->
                            navigator.navigate(TrackRoute(trackId, allowRetry = true))
                        },
                        onNavigateToQueue = { navigator.navigate(QueueRoute) },
                        onNavigateToPreferences = { navigator.navigate(PreferencesRoute) },
                        barHeight = barHeight,
                        railWidth = railWidth,
                        showRail = showRail,
                        hideSplashScreen = hideSplashScreen,
                    )
                    libraryEntry(
                        onNavigateToTrack = { trackId -> navigator.navigate(TrackRoute(trackId)) },
                        onNavigateToLibrarySearch = { navigator.navigate(LibrarySearchRoute) },
                        barHeight = barHeight,
                        railWidth = railWidth,
                        showRail = showRail,
                        hideSplashScreen = hideSplashScreen,
                    )
                    queueEntry(
                        onNavigateToTrack = { trackId -> navigator.navigate(TrackRoute(trackId)) },
                        barHeight = barHeight,
                        railWidth = railWidth,
                        showRail = showRail,
                        hideSplashScreen = hideSplashScreen,
                    )
                    preferencesEntry(
                        showDeveloperOptions = BuildConfig.DEV_OPTIONS,
                        onNavigateToAbout = { navigator.navigate(AboutRoute) },
                        onNavigateToBackupRestore = { navigator.navigate(BackupRestoreRoute) },
                        onNavigateToDeveloper = { navigator.navigate(DeveloperRoute) },
                        barHeight = barHeight,
                        railWidth = railWidth,
                        showRail = showRail,
                        hideSplashScreen = hideSplashScreen,
                    )
                    librarySearchEntry(
                        onBackPressed = navigator::goBack,
                        onNavigateToTrack = { trackId -> navigator.navigate(TrackRoute(trackId)) },
                        hideSplashScreen = hideSplashScreen,
                    )
                    trackEntry(
                        isExpandedScreen = expandedScreen,
                        onBackPressed = navigator::goBack,
                        onTrackDeleted = { navigator.popTrack(TrackPopStyle.Deleted) },
                        onRetryRequested = {
                            setPendingRecognitionRequest(RecognitionRequest.Retry)
                            navigator.popTrack(TrackPopStyle.Dismissed)
                            // The pending request effect runs on the next frame and would
                            // replace this pop, dropping the dismiss animation. Switch in
                            // this snapshot when the pop didn't already land on Recognition.
                            val landed = navigationState.backStacks
                                .getValue(navigationState.topLevelRoute)
                                .last()
                            if (landed != RecognitionRoute) {
                                navigator.openTopLevel(RecognitionRoute)
                            }
                        },
                        onNavigateToLyrics = { trackId -> navigator.navigate(LyricsRoute(trackId)) },
                        hideSplashScreen = hideSplashScreen,
                        metadata = trackPopMetadata(navigator),
                    )
                    lyricsEntry(
                        onBackPressed = navigator::goBack,
                        hideSplashScreen = hideSplashScreen,
                    )
                    aboutEntry(
                        onBackPressed = navigator::goBack,
                        onNavigateToSoftware = { navigator.navigate(SoftwareRoute) },
                        onNavigateToAppLicense = { navigator.navigate(AppLicenseRoute) },
                        hideSplashScreen = hideSplashScreen,
                    )
                    softwareEntry(
                        onBackPressed = navigator::goBack,
                        onNavigateToSoftwareDetails = { uniqueId ->
                            navigator.navigate(SoftwareDetailsRoute(uniqueId))
                        },
                        hideSplashScreen = hideSplashScreen,
                    )
                    softwareDetailsEntry(
                        onBackPressed = navigator::goBack,
                        hideSplashScreen = hideSplashScreen,
                    )
                    appLicenseEntry(
                        onBackPressed = navigator::goBack,
                        hideSplashScreen = hideSplashScreen,
                    )
                    backupRestoreEntry(
                        onBackPressed = navigator::goBack,
                        hideSplashScreen = hideSplashScreen,
                    )
                    developerEntry(
                        onBackPressed = navigator::goBack,
                        hideSplashScreen = hideSplashScreen,
                    )
                },
            ),
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { defaultScreenTransition },
            popTransitionSpec = { defaultScreenTransition },
            predictivePopTransitionSpec = { _ -> defaultScreenTransition },
            onBack = navigator::goBack,
        )
        AnimatedVisibility(
            visible = barVisible,
            enter = defaultEnterTransition,
            exit = defaultExitTransition,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            AppNavigationBar(
                unviewedTracksCount = unviewedTracksCount,
                selectedKey = navigationState.topLevelRoute,
                onSelect = navigator::navigate,
                enabled = barVisible,
                modifier = Modifier
                    .onSizeChanged { barHeight.intValue = it.height }
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    ),
            )
        }
        AnimatedVisibility(
            visible = railVisible,
            enter = defaultEnterTransition,
            exit = defaultExitTransition,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .onSizeChanged { railWidth.intValue = it.width },
            ) {
                AppNavigationRail(
                    unviewedTracksCount = unviewedTracksCount,
                    selectedKey = navigationState.topLevelRoute,
                    onSelect = navigator::navigate,
                    enabled = railVisible,
                    modifier = Modifier.fillMaxHeight(),
                )
                VerticalDivider(modifier = Modifier.alpha(0.2f))
            }
        }
    }
}

private const val SCREEN_TRANSITION_DURATION = 250
private val defaultEnterTransition = fadeIn(animationSpec = tween(SCREEN_TRANSITION_DURATION))
private val defaultExitTransition = fadeOut(animationSpec = tween(SCREEN_TRANSITION_DURATION))
private val defaultScreenTransition = defaultEnterTransition togetherWith defaultExitTransition

private fun trackPopMetadata(navigator: Navigator): Map<String, Any> =
    NavDisplay.popTransitionSpec { styledTrackPop(navigator.trackPopStyle) } +
        NavDisplay.predictivePopTransitionSpec { _ -> styledTrackPop(navigator.trackPopStyle) }

private fun styledTrackPop(style: TrackPopStyle): ContentTransform? = when (style) {
    TrackPopStyle.Deleted -> deletedTrackPop()
    TrackPopStyle.Dismissed -> dismissedTrackPop()
    TrackPopStyle.NoAction -> null
}

private fun deletedTrackPop(): ContentTransform =
    defaultEnterTransition togetherWith (
        fadeOut(animationSpec = tween(durationMillis = SCREEN_TRANSITION_DURATION - 50, delayMillis = 50)) +
            scaleOut(animationSpec = tween(durationMillis = SCREEN_TRANSITION_DURATION), targetScale = 0.85f)
        )

private fun dismissedTrackPop(): ContentTransform =
    defaultEnterTransition togetherWith (
        slideOutVertically(
            animationSpec = tween(SCREEN_TRANSITION_DURATION, easing = FastOutLinearInEasing),
            targetOffsetY = { fullHeight -> -fullHeight / 2 },
        ) + fadeOut(tween(durationMillis = SCREEN_TRANSITION_DURATION))
        )
