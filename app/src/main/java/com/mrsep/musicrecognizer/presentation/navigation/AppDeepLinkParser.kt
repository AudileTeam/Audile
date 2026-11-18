package com.mrsep.musicrecognizer.presentation.navigation

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.invoke
import com.mrsep.musicrecognizer.feature.backup.presentation.BackupRestoreRoute
import com.mrsep.musicrecognizer.feature.library.presentation.library.LibraryRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.QueueRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.recognitionscreen.RecognitionRoute
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.LyricsRoute
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackRoute

data class ParsedDeepLink(
    val owner: NavKey,
    val stack: List<NavKey>,
)

private val appDeepLinkMatchers: List<UriDeepLinkMatcher<NavKey>> = listOf(
    UriDeepLinkMatcher(DeepLinkUri(LibraryRoute.deepLinkUri()), LibraryRoute.serializer()),
    UriDeepLinkMatcher(DeepLinkUri(QueueRoute.deepLinkUri()), QueueRoute.serializer()),
    UriDeepLinkMatcher(DeepLinkUri(PreferencesRoute.deepLinkUri()), PreferencesRoute.serializer()),
    UriDeepLinkMatcher(DeepLinkUri(BackupRestoreRoute.deepLinkUri()), BackupRestoreRoute.serializer()),
    UriDeepLinkMatcher(DeepLinkUri(TrackRoute.DEEP_LINK_PATTERN), TrackRoute.serializer()),
    UriDeepLinkMatcher(DeepLinkUri(LyricsRoute.DEEP_LINK_PATTERN), LyricsRoute.serializer()),
)

fun parseAppDeepLink(intent: Intent): ParsedDeepLink? {
    if (intent.action != Intent.ACTION_VIEW || intent.data == null) return null
    val match = appDeepLinkMatchers
        .mapNotNull { it.match(DeepLinkRequest(intent)) }
        .maxOrNull()
        ?: return null
    return syntheticStack(match.key)
}

private fun syntheticStack(key: NavKey): ParsedDeepLink? = when (key) {
    LibraryRoute -> ParsedDeepLink(LibraryRoute, listOf(LibraryRoute))
    QueueRoute -> ParsedDeepLink(QueueRoute, listOf(QueueRoute))
    PreferencesRoute -> ParsedDeepLink(PreferencesRoute, listOf(PreferencesRoute))
    BackupRestoreRoute -> ParsedDeepLink(
        owner = PreferencesRoute,
        stack = listOf(PreferencesRoute, BackupRestoreRoute),
    )
    is TrackRoute -> ParsedDeepLink(
        owner = RecognitionRoute,
        stack = listOf(RecognitionRoute, key),
    )
    is LyricsRoute -> ParsedDeepLink(
        owner = RecognitionRoute,
        stack = listOf(
            RecognitionRoute,
            TrackRoute(trackId = key.trackId, allowRetry = false),
            key,
        ),
    )
    else -> null
}
