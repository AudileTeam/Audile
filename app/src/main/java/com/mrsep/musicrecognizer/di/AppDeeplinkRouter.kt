package com.mrsep.musicrecognizer.di

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import androidx.core.net.toUri
import com.mrsep.musicrecognizer.feature.backup.presentation.BackupRestoreScreenNavigation
import com.mrsep.musicrecognizer.feature.library.presentation.library.LibraryScreen
import com.mrsep.musicrecognizer.feature.preferences.presentation.PreferencesScreen
import com.mrsep.musicrecognizer.feature.recognition.presentation.queuescreen.RecognitionQueueScreen
import com.mrsep.musicrecognizer.core.common.DeeplinkRouter
import com.mrsep.musicrecognizer.feature.track.presentation.lyrics.LyricsScreen
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackScreen
import com.mrsep.musicrecognizer.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AppDeeplinkRouter @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : DeeplinkRouter {

    override fun getDeepLinkIntentToTrack(trackId: String): Intent {
        return getDeepLinkIntent(TrackScreen.createDeepLink(trackId).toUri())
    }

    override fun getDeepLinkIntentToLyrics(trackId: String): Intent {
        return getDeepLinkIntent(LyricsScreen.createDeepLink(trackId).toUri())
    }

    override fun getDeepLinkIntentToLibrary(): Intent {
        return getDeepLinkIntent(LibraryScreen.createDeepLink().toUri())
    }

    override fun getDeepLinkIntentToQueue(): Intent {
        return getDeepLinkIntent(RecognitionQueueScreen.createDeepLink().toUri())
    }

    override fun getDeepLinkIntentToBackupRestore(): Intent {
        return getDeepLinkIntent(BackupRestoreScreenNavigation.createDeepLink().toUri())
    }

    override fun getDeepLinkIntentToPreferences(): Intent {
        return getDeepLinkIntent(PreferencesScreen.createDeepLink().toUri())
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
