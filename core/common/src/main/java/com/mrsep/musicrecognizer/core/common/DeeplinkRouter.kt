package com.mrsep.musicrecognizer.core.common

import android.content.Intent

interface DeeplinkRouter {

    fun getDeepLinkIntentToTrack(trackId: String): Intent

    fun getDeepLinkIntentToLyrics(trackId: String): Intent

    fun getDeepLinkIntentToLibrary(): Intent

    fun getDeepLinkIntentToQueue(): Intent

    fun getDeepLinkIntentToBackupRestore(): Intent

    fun getDeepLinkIntentToPreferences(): Intent
}
