package com.mrsep.musicrecognizer.di

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import androidx.core.net.toUri
import com.mrsep.musicrecognizer.feature.backup.presentation.BackupRestoreRoute
import com.mrsep.musicrecognizer.feature.library.presentation.library.LibraryRoute
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesRoute
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.QueueRoute
import com.mrsep.musicrecognizer.core.common.DeeplinkRouter
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.LyricsRoute
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackRoute
import com.mrsep.musicrecognizer.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AppDeeplinkRouter @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : DeeplinkRouter {

    override fun getDeepLinkIntentToTrack(trackId: String): Intent {
        return getDeepLinkIntent(TrackRoute(trackId).deepLinkUri().toUri())
    }

    override fun getDeepLinkIntentToLyrics(trackId: String): Intent {
        return getDeepLinkIntent(LyricsRoute(trackId).deepLinkUri().toUri())
    }

    override fun getDeepLinkIntentToLibrary(): Intent {
        return getDeepLinkIntent(LibraryRoute.deepLinkUri().toUri())
    }

    override fun getDeepLinkIntentToQueue(): Intent {
        return getDeepLinkIntent(QueueRoute.deepLinkUri().toUri())
    }

    override fun getDeepLinkIntentToBackupRestore(): Intent {
        return getDeepLinkIntent(BackupRestoreRoute.deepLinkUri().toUri())
    }

    override fun getDeepLinkIntentToPreferences(): Intent {
        return getDeepLinkIntent(PreferencesRoute.deepLinkUri().toUri())
    }

    private fun getDeepLinkIntent(uri: Uri): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            uri,
            appContext,
            MainActivity::class.java
        ).apply {
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
