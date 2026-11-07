package com.mrsep.musicrecognizer.core.domain.recognition.model

import java.time.Instant
import kotlin.time.Duration

sealed interface AutoRecognitionState {

    val running: Boolean
        get() = this is Running

    data object Idle : AutoRecognitionState

    data class Running(
        val phase: Phase,
        val sessionTrackIds: List<String> = emptyList(),
        val lastPlaying: LastPlaying? = null,
    ) : AutoRecognitionState

    /**
     * Latest match in this session, including a repeat of the same track.
     * [matchedAt] is this attempt's clock, not the stored `recognitionDate`
     * A duplicate is not written, so the stored row keeps the first match's clock.
     */
    data class LastPlaying(
        val trackId: String,
        val matchedAt: Instant,
        val recognizedAt: Duration?,
        val duration: Duration?,
    )

    enum class Phase {
        WaitingForNetwork,
        PausedLowBattery,
        PausedDuringCall,
        Recording,
        Recognizing,
        BetweenAttempts,
    }
}
