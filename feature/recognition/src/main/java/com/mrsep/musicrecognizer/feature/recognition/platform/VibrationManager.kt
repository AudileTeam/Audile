package com.mrsep.musicrecognizer.feature.recognition.platform

internal interface VibrationManager {
    fun vibrateSuccess()
    fun vibrateFailure()
    fun vibrateOnTap()
    fun vibrateOnNowPlaying()
}
