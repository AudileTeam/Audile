package com.mrsep.musicrecognizer.feature.recognition.platform

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class AudioModeCallMonitor @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : CallMonitor {

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun inCall(): Boolean = audioManager.mode.isCallAudioMode()

    @RequiresApi(Build.VERSION_CODES.S)
    override suspend fun awaitCallEnd() {
        callbackFlow {
            val listener = AudioManager.OnModeChangedListener { mode ->
                trySend(mode)
            }
            audioManager.addOnModeChangedListener(appContext.mainExecutor, listener)
            // The call may already have ended between getMode() and this registration
            trySend(audioManager.mode)
            awaitClose { audioManager.removeOnModeChangedListener(listener) }
        }.first { mode -> !mode.isCallAudioMode() }
    }

    // Newer modes are never returned on APIs that predate them.
    // getMode() also applies backward-compatibility mappings.
    @SuppressLint("NewApi")
    private fun Int.isCallAudioMode(): Boolean {
        return this == AudioManager.MODE_RINGTONE ||
                this == AudioManager.MODE_IN_CALL ||
                this == AudioManager.MODE_IN_COMMUNICATION ||
                this == AudioManager.MODE_CALL_SCREENING ||
                this == AudioManager.MODE_CALL_REDIRECT ||
                this == AudioManager.MODE_COMMUNICATION_REDIRECT
    }
}
