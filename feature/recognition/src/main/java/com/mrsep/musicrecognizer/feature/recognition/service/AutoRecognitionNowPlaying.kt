package com.mrsep.musicrecognizer.feature.recognition.service

import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

// Less than NowPlayingMinRemaining is never shown
private val NowPlayingMinRemaining = 5.seconds
private val NowPlayingWithoutDuration = 10.minutes

private fun AutoRecognitionState.LastPlaying.displayBudget(): Duration? {
    val songDuration = duration
    val offset = recognizedAt
    return if (songDuration != null && offset != null) {
        val full = songDuration - offset
        if (full <= NowPlayingMinRemaining) null else full
    } else {
        val full = songDuration ?: NowPlayingWithoutDuration
        if (full <= Duration.ZERO) null else full
    }
}

private fun AutoRecognitionState.LastPlaying.timeUntilHidden(
    now: Instant = Instant.now(),
): Duration? {
    val budget = displayBudget() ?: return null
    val elapsed = java.time.Duration.between(matchedAt, now).toKotlinDuration()
    val left = budget - elapsed
    return if (left <= Duration.ZERO) null else left
}

private fun AutoRecognitionState.LastPlaying.shouldShowNowPlaying(
    now: Instant = Instant.now(),
): Boolean = timeUntilHidden(now) != null

internal fun AutoRecognitionState.LastPlaying.isNowPlayingExpired(
    now: Instant = Instant.now(),
): Boolean = displayBudget() != null && timeUntilHidden(now) == null

internal suspend fun AutoRecognitionState.LastPlaying.awaitNowPlayingHidden() {
    while (true) {
        val wait = timeUntilHidden() ?: return
        delay(wait.coerceAtLeast(1.milliseconds))
    }
}

internal fun AutoRecognitionState.visibleNowPlayingTrackId(): String? {
    val running = this as? AutoRecognitionState.Running ?: return null
    val playing = running.lastPlaying ?: return null
    if (running.phase == AutoRecognitionState.Phase.WaitingForNetwork ||
        running.phase == AutoRecognitionState.Phase.PausedLowBattery ||
        running.phase == AutoRecognitionState.Phase.PausedDuringCall
    ) {
        return null
    }
    if (!playing.shouldShowNowPlaying()) return null
    return playing.trackId
}

internal data class AutoRecognitionTileContent(
    val running: Boolean,
    val nowPlayingTrackId: String?,
    val blocker: AutoRecognitionState.Phase?,
)

internal fun AutoRecognitionState.tileContent(): AutoRecognitionTileContent {
    val running = this as? AutoRecognitionState.Running
        ?: return AutoRecognitionTileContent(running = false, nowPlayingTrackId = null, blocker = null)
    val blocker = when (running.phase) {
        AutoRecognitionState.Phase.WaitingForNetwork,
        AutoRecognitionState.Phase.PausedLowBattery,
        AutoRecognitionState.Phase.PausedDuringCall,
        -> running.phase
        else -> null
    }
    return AutoRecognitionTileContent(
        running = true,
        nowPlayingTrackId = if (blocker == null) visibleNowPlayingTrackId() else null,
        blocker = blocker,
    )
}
