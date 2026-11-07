package com.mrsep.musicrecognizer.core.domain.recognition

import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import kotlinx.coroutines.flow.StateFlow

interface AutoRecognitionInteractor {

    val state: StateFlow<AutoRecognitionState>

    /**
     * Runs one session until canceled or a fatal error.
     * A second call, while the first is still running or cancelling,
     * returns null and does not join or cancel that session.
     */
    suspend fun run(): RemoteRecognitionResult.Error?

    /**
     * Skip the low-battery pause until the system leaves the low state or this session ends.
     * No-op when auto recognition is not running or the battery is not low.
     */
    fun allowLowBatteryThisTime()

    /** Drop [playing] if it is still the current `lastPlaying`. */
    fun clearLastPlaying(playing: AutoRecognitionState.LastPlaying)
}
