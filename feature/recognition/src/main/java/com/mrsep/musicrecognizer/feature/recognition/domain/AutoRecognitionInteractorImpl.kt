package com.mrsep.musicrecognizer.feature.recognition.domain

import android.os.Build
import com.mrsep.musicrecognizer.core.audio.audiorecord.AudioCaptureConfig
import com.mrsep.musicrecognizer.core.audio.audiorecord.AudioRecordingControllerFactory
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.AudioRecordingSession
import com.mrsep.musicrecognizer.core.domain.recognition.AudioSample
import com.mrsep.musicrecognizer.core.domain.recognition.AutoRecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.NetworkMonitor
import com.mrsep.musicrecognizer.core.domain.recognition.RecognitionServiceFactory
import com.mrsep.musicrecognizer.core.domain.recognition.RemoteRecognitionService
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.recognition.model.AutoRecognitionState
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecordingScheme
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.toAudioSample
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.feature.recognition.platform.AlarmWait
import com.mrsep.musicrecognizer.feature.recognition.platform.AttemptWakeLock
import com.mrsep.musicrecognizer.feature.recognition.platform.BatteryMonitor
import com.mrsep.musicrecognizer.feature.recognition.platform.CallMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
internal class AutoRecognitionInteractorImpl @Inject constructor(
    private val recognitionServiceFactory: RecognitionServiceFactory,
    private val preferencesRepository: PreferencesRepository,
    private val trackRepository: TrackRepository,
    private val trackMetadataFetchManager: TrackMetadataFetchManager,
    private val networkMonitor: NetworkMonitor,
    private val batteryMonitor: BatteryMonitor,
    private val callMonitor: CallMonitor,
    private val alarmWait: AlarmWait,
    private val attemptWakeLock: AttemptWakeLock,
    private val audioRecordingControllerFactory: AudioRecordingControllerFactory,
) : AutoRecognitionInteractor {

    private val _state = MutableStateFlow<AutoRecognitionState>(AutoRecognitionState.Idle)
    override val state = _state.asStateFlow()

    private val sessionActive = AtomicBoolean(false)

    // Cleared on a not-low reading or session end. The next low episode pauses again.
    private val skipLowBatteryPause = MutableStateFlow(false)

    private var lastAcceptedTrackId: String? = null
    private var failureStreak = 0

    override fun allowLowBatteryThisTime() {
        if (_state.value !is AutoRecognitionState.Running) return
        if (!batteryMonitor.isBatteryLow()) return
        skipLowBatteryPause.value = true
    }

    override fun clearLastPlaying(playing: AutoRecognitionState.LastPlaying) {
        _state.update { state ->
            if (state !is AutoRecognitionState.Running || state.lastPlaying != playing) {
                state
            } else {
                state.copy(lastPlaying = null)
            }
        }
    }

    override suspend fun run(): RemoteRecognitionResult.Error? {
        if (!sessionActive.compareAndSet(false, true)) return null
        try {
            return executeSession()
        } finally {
            withContext(NonCancellable) {
                // Before releasing sessionActive. A new run() must not start under this cleanup.
                _state.value = AutoRecognitionState.Idle
                skipLowBatteryPause.value = false
                lastAcceptedTrackId = null
                failureStreak = 0
                attemptWakeLock.release()
                sessionActive.set(false)
            }
        }
    }

    private suspend fun executeSession(): RemoteRecognitionResult.Error {
        val batteryLow = MutableStateFlow(batteryMonitor.isBatteryLow())
        return coroutineScope {
            // Keep for the session. When battery is no longer low, the next low period will pause.
            val batteryUpdates = launch {
                batteryMonitor.isBatteryLowFlow().collect { low ->
                    batteryLow.value = low
                    if (!low) skipLowBatteryPause.value = false
                }
            }
            try {
                attemptUntilFatal(batteryLow)
            } finally {
                batteryUpdates.cancel()
            }
        }
    }

    private suspend fun attemptUntilFatal(
        batteryLow: StateFlow<Boolean>,
    ): RemoteRecognitionResult.Error {
        _state.value = AutoRecognitionState.Running(AutoRecognitionState.Phase.BetweenAttempts)
        while (true) {
            val fatal = try {
                runAttempt(batteryLow)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publishLastPlaying(null)
                countFailureStreakUntilFatal(RemoteRecognitionResult.Error.UnhandledError(cause = e))
            }
            if (fatal != null) return fatal
            publish(AutoRecognitionState.Phase.BetweenAttempts)
            alarmWait.await(PAUSE_BETWEEN_ATTEMPTS)
        }
    }

    private suspend fun runAttempt(batteryLow: StateFlow<Boolean>): RemoteRecognitionResult.Error? {
        awaitReadyToRecord(batteryLow)
        val preferences = preferencesRepository.userPreferencesFlow.first()
        val service = recognitionServiceFactory.getService(preferences.recognitionServiceConfig())
        publish(AutoRecognitionState.Phase.Recording)
        return recordAndRecognize(service)
    }

    private suspend fun awaitReadyToRecord(batteryLow: StateFlow<Boolean>) {
        attemptWakeLock.acquire()
        while (true) {
            if (pauseWhileInCall()) continue
            if (pauseWhileBatteryLow(batteryLow)) continue
            if (pauseWhileOffline()) continue
            // A call can start while the offline read is suspended
            if (callMonitor.inCall()) continue
            break
        }
        // Otherwise the pause text and Allow action stick through the preference read
        val phase = (_state.value as? AutoRecognitionState.Running)?.phase
        if (phase == AutoRecognitionState.Phase.PausedLowBattery ||
            phase == AutoRecognitionState.Phase.PausedDuringCall ||
            phase == AutoRecognitionState.Phase.WaitingForNetwork
        ) {
            publish(AutoRecognitionState.Phase.BetweenAttempts)
        }
        // WakeLock stays held
    }

    private suspend fun pauseWhileInCall(): Boolean {
        if (!callMonitor.inCall()) return false
        publish(AutoRecognitionState.Phase.PausedDuringCall)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            attemptWakeLock.release()
            callMonitor.awaitCallEnd()
            attemptWakeLock.acquire()
        } else {
            alarmWait.await(PAUSE_BETWEEN_ATTEMPTS)
        }
        return true
    }

    private suspend fun pauseWhileBatteryLow(batteryLow: StateFlow<Boolean>): Boolean {
        if (!batteryLow.value || skipLowBatteryPause.value) return false
        publish(AutoRecognitionState.Phase.PausedLowBattery)
        // No alarm: plugging in wakes the device and clears EXTRA_BATTERY_LOW
        attemptWakeLock.release()
        combine(batteryLow, skipLowBatteryPause) { low, skip ->
            low && !skip
        }.first { paused -> !paused }
        attemptWakeLock.acquire()
        return true
    }

    private suspend fun pauseWhileOffline(): Boolean {
        if (!networkMonitor.isOffline.first()) return false
        publish(AutoRecognitionState.Phase.WaitingForNetwork)
        coroutineScope {
            // UNDISPATCHED so alarmWait.await() releases the lock before the network child exists
            val alarm = async(start = CoroutineStart.UNDISPATCHED) {
                alarmWait.await(PAUSE_BETWEEN_ATTEMPTS)
            }
            val online = async {
                networkMonitor.isOffline.first { offline ->
                    if (!offline) attemptWakeLock.acquire()
                    !offline
                }
            }
            select {
                online.onAwait { alarm.cancel() }
                alarm.onAwait { online.cancel() }
            }
        }
        return true
    }

    private suspend fun recordAndRecognize(
        service: RemoteRecognitionService,
    ): RemoteRecognitionResult.Error? {
        val controller = audioRecordingControllerFactory.getAudioController(AudioCaptureConfig.Microphone)
        var session: AudioRecordingSession? = null
        try {
            return coroutineScope {
                val startedSession = controller.startRecordingSession(
                    scheme = service.autoRecognitionScheme(),
                    includeBuffered = false,
                )
                session = startedSession
                when (val recorded = startedSession.captureUsableSample()) {
                    is RecordedSample.Ready -> {
                        recognizeSample(service, recorded.sample)
                    }
                    RecordedSample.NoSound -> {
                        resetFailureStreak()
                        publishLastPlaying(null)
                        null
                    }
                    is RecordedSample.Failure -> {
                        publishLastPlaying(null)
                        countFailureStreakUntilFatal(
                            RemoteRecognitionResult.Error.BadRecording(cause = recorded.cause)
                        )
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                session?.cancelAndDeleteSessionFiles()
                controller.release()
            }
        }
    }

    private suspend fun recognizeSample(
        service: RemoteRecognitionService,
        sample: AudioSample,
    ): RemoteRecognitionResult.Error? {
        publish(AutoRecognitionState.Phase.Recognizing)
        return when (val result = service.recognize(sample)) {
            is RemoteRecognitionResult.Success -> {
                resetFailureStreak()
                acceptMatch(result.track)
                null
            }

            RemoteRecognitionResult.NoMatches,
            RemoteRecognitionResult.NoSoundDetected,
            RemoteRecognitionResult.Error.BadConnection -> {
                resetFailureStreak()
                publishLastPlaying(null)
                null
            }

            is RemoteRecognitionResult.Error.HttpError -> when {
                result.code == 429 -> {
                    resetFailureStreak()
                    publishLastPlaying(null)
                    null
                }
                result.code in 400..499 -> {
                    publishLastPlaying(null)
                    result
                }
                else -> {
                    publishLastPlaying(null)
                    countFailureStreakUntilFatal(result)
                }
            }

            is RemoteRecognitionResult.Error.AuthError,
            is RemoteRecognitionResult.Error.ApiUsageLimited -> {
                publishLastPlaying(null)
                result
            }

            is RemoteRecognitionResult.Error.BadRecording,
            is RemoteRecognitionResult.Error.UnhandledError -> {
                publishLastPlaying(null)
                countFailureStreakUntilFatal(result)
            }
        }
    }

    private suspend fun acceptMatch(track: Track) {
        val playing = track.toLastPlaying()
        if (track.id == lastAcceptedTrackId) {
            publishLastPlaying(playing)
            return
        }
        val saved = trackRepository.upsertFromRecognition(track)
        lastAcceptedTrackId = saved.id
        val preferences = preferencesRepository.userPreferencesFlow.first()
        if (preferences.requiredMusicServices.any { it !in saved.trackLinks }) {
            trackMetadataFetchManager.enqueueTrackLinksFetcher(saved.id)
        }
        if (saved.lyrics == null) {
            trackMetadataFetchManager.enqueueLyricsFetcher(saved.id)
        }
        val savedPlaying = playing.copy(trackId = saved.id)
        _state.update { state ->
            if (state !is AutoRecognitionState.Running) {
                state
            } else {
                state.copy(
                    sessionTrackIds = state.sessionTrackIds + saved.id,
                    lastPlaying = savedPlaying,
                )
            }
        }
    }

    private fun resetFailureStreak() {
        failureStreak = 0
    }

    private fun countFailureStreakUntilFatal(
        error: RemoteRecognitionResult.Error
    ): RemoteRecognitionResult.Error? {
        failureStreak += 1
        return if (failureStreak >= FAILURE_STREAK_LIMIT) error else null
    }

    private fun publish(phase: AutoRecognitionState.Phase) {
        _state.update { state ->
            if (state !is AutoRecognitionState.Running) state else state.copy(phase = phase)
        }
    }

    private fun publishLastPlaying(playing: AutoRecognitionState.LastPlaying?) {
        _state.update { state ->
            if (state !is AutoRecognitionState.Running) state
            else state.copy(lastPlaying = playing)
        }
    }

    private fun RemoteRecognitionService.autoRecognitionScheme() = RecordingScheme(
        steps = listOf(RecordingScheme.Step(listOf(autoRecognitionSampleDuration))),
        fallback = null,
        encodeSteps = recordingScheme.encodeSteps,
    )

    private fun Track.toLastPlaying() = AutoRecognitionState.LastPlaying(
        trackId = id,
        matchedAt = recognitionDate,
        recognizedAt = recognizedAt,
        duration = duration,
    )

    private suspend fun AudioRecordingSession.captureUsableSample(): RecordedSample {
        val received = recordings.receiveCatching()
        val cause = received.exceptionOrNull()
        if (cause is CancellationException) throw cause
        val recording = received.getOrNull()
        if (recording == null) {
            cause?.let { return RecordedSample.Failure(it) }
            error("Channel closed without recording or exception")
        }
        if (recording.nonSilenceDuration <= MIN_NON_SILENCE_DURATION) return RecordedSample.NoSound
        return RecordedSample.Ready(recording.toAudioSample())
    }

    private sealed interface RecordedSample {
        data class Ready(val sample: AudioSample) : RecordedSample
        data object NoSound : RecordedSample
        data class Failure(val cause: Throwable) : RecordedSample
    }

    private companion object {
        val PAUSE_BETWEEN_ATTEMPTS = 60.seconds
        val MIN_NON_SILENCE_DURATION = 2.seconds

        const val FAILURE_STREAK_LIMIT = 3
    }
}
