package com.mrsep.musicrecognizer.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed

/**
 * Hides the splash once this composition's lifecycle is [Lifecycle.State.RESUMED].
 * Must run inside a navigation entry so [LocalLifecycleOwner] is the destination, not the activity.
 */
@Composable
fun HideSplashWhenResumed(hideSplashScreen: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.withResumed(hideSplashScreen)
    }
}

inline fun Lifecycle.ifResumed(block: () -> Unit) {
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) block()
}
