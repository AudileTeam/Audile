package com.mrsep.musicrecognizer.feature.recognition.domain

import com.mrsep.musicrecognizer.core.domain.preferences.FallbackAction
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.preferences.RecognitionServiceConfig
import com.mrsep.musicrecognizer.core.domain.preferences.ShazamConfig
import com.mrsep.musicrecognizer.core.domain.preferences.UserPreferences
import com.mrsep.musicrecognizer.core.domain.recognition.AudioRecording
import com.mrsep.musicrecognizer.core.domain.recognition.AudioRecordingController
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionScheduler
import com.mrsep.musicrecognizer.core.domain.recognition.NetworkMonitor
import com.mrsep.musicrecognizer.core.domain.recognition.RecognitionInteractor
import com.mrsep.musicrecognizer.core.domain.recognition.RecognitionServiceFactory
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionStatus
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionTask
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.toAudioSample
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
internal class RecognitionInteractorImpl @Inject constructor(
    private val recognitionServiceFactory: RecognitionServiceFactory,
    private val preferencesRepository: PreferencesRepository,
    private val trackRepository: TrackRepository,
    private val enqueuedRecognitionRepository: EnqueuedRecognitionRepository,
    private val enqueuedRecognitionScheduler: EnqueuedRecognitionScheduler,
    private val trackMetadataFetchManager: TrackMetadataFetchManager,
    private val networkMonitor: NetworkMonitor,
) : RecognitionInteractor {

    private val _status = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    override val status = _status.asStateFlow()

    private var recognitionJob: Job? = null
    private val isRecognitionJobCompleted get() = recognitionJob?.isCompleted ?: true

    context(scope: CoroutineScope)
    override fun launchRecognition(recordingController: AudioRecordingController, usePrerecordingBuffer: Boolean) {
        launchRecognitionIfPreviousCompleted {
            _status.update { RecognitionStatus.Recognizing(false) }
            val userPreferences = preferencesRepository.userPreferencesFlow.first()
            val isFallbackRequired = userPreferences.fallbackPolicy.isFallbackRequired
            val serviceConfig = userPreferences.recognitionServiceConfig()
            val recognitionService = recognitionServiceFactory.getService(serviceConfig)
            val recordingScheme = recognitionService.recordingScheme.takeIf { isFallbackRequired }
                ?: recognitionService.recordingScheme.copy(fallback = null)

            val recordingChannel = Channel<AudioRecording>(Channel.UNLIMITED)
            val fallbackRecording = if (isFallbackRequired) CompletableDeferred<AudioRecording>() else null

            val recordingSession = recordingController.startRecordingSession(
                recordingScheme,
                usePrerecordingBuffer
            )

            try {
                val extraTimeNotifier = launch {
                    val extraTime = recordingScheme.run { steps.first().recordings.last() + 3.5.seconds }
                    delay(extraTime)
                    _status.update { RecognitionStatus.Recognizing(extraTime = true) }
                }

                val remoteRecognitionResult = async {
                    recognitionService.recognizeUntilFirstMatch(
                        recordingChannel.receiveAsFlow().map { it.toAudioSample() }
                    )
                }

                val samplesDistributionResult = async {
                    try {
                        var sentAnyRecording = false
                        for (recording in recordingSession.recordings) {
                            if (recording.isFallback) {
                                fallbackRecording?.complete(recording)
                            } else if (recording.nonSilenceDuration > 0.5.seconds) {
                                recordingChannel.send(recording)
                                sentAnyRecording = true
                            }
                        }
                        Result.success(sentAnyRecording)
                    } catch (e: Exception) {
                        ensureActive()
                        Result.failure(e)
                    }
                }

                val result: RemoteRecognitionResult = select {
                    remoteRecognitionResult.onAwait { it }
                    samplesDistributionResult.onAwait { result ->
                        result.fold(
                            onSuccess = { sentAnyRecording ->
                                recordingChannel.close()
                                fallbackRecording?.completeExceptionally(
                                    IllegalStateException("Fallback recording missed")
                                )
                                if (sentAnyRecording) {
                                    remoteRecognitionResult.await()
                                } else {
                                    RemoteRecognitionResult.NoSoundDetected
                                }
                            },
                            onFailure = { cause ->
                                remoteRecognitionResult.cancel()
                                recordingChannel.close()
                                fallbackRecording?.completeExceptionally(cause)
                                RemoteRecognitionResult.Error.BadRecording(cause = cause)
                            }
                        )
                    }
                }

                extraTimeNotifier.cancelAndJoin()

                suspend fun stopRecordingAndDeleteSessionFiles() {
                    samplesDistributionResult.cancelAndJoin()
                    recordingSession.cancelAndDeleteSessionFiles()
                }

                val recognitionResult = when (result) {
                    is RemoteRecognitionResult.Success -> {
                        stopRecordingAndDeleteSessionFiles()
                        val updatedTrack = trackRepository.upsertFromRecognition(result.track)
                        val shouldSearchForLinks = preferencesRepository.userPreferencesFlow.first()
                            .requiredMusicServices.any { it !in updatedTrack.trackLinks }
                        if (shouldSearchForLinks) {
                            trackMetadataFetchManager.enqueueTrackLinksFetcher(updatedTrack.id)
                        }
                        if (updatedTrack.lyrics == null) {
                            trackMetadataFetchManager.enqueueLyricsFetcher(updatedTrack.id)
                        }
                        RecognitionStatus.Done(RecognitionResult.Success(updatedTrack))
                    }

                    RemoteRecognitionResult.NoMatches -> {
                        val recognitionTask = processDeferredRecognition(
                            userPreferences.fallbackPolicy.noMatches,
                            fallbackRecording
                        )
                        stopRecordingAndDeleteSessionFiles()
                        RecognitionStatus.Done(RecognitionResult.NoMatches(recognitionTask))
                    }

                    is RemoteRecognitionResult.NoSoundDetected -> {
                        stopRecordingAndDeleteSessionFiles()
                        RecognitionStatus.Done(RecognitionResult.NoSoundDetected)
                    }

                    is RemoteRecognitionResult.Error.BadConnection -> {
                        val recognitionTask = processDeferredRecognition(
                            userPreferences.fallbackPolicy.badConnection,
                            fallbackRecording
                        )
                        stopRecordingAndDeleteSessionFiles()
                        if (recognitionTask is RecognitionTask.Created && networkMonitor.isOffline.first()) {
                            RecognitionStatus.Done(RecognitionResult.ScheduledOffline(recognitionTask))
                        } else {
                            RecognitionStatus.Done(RecognitionResult.Error(result, recognitionTask))
                        }
                    }

                    is RemoteRecognitionResult.Error.BadRecording -> {
                        stopRecordingAndDeleteSessionFiles()
                        RecognitionStatus.Done(RecognitionResult.Error(result, RecognitionTask.Ignored))
                    }

                    is RemoteRecognitionResult.Error -> {
                        val recognitionTask = processDeferredRecognition(
                            userPreferences.fallbackPolicy.anotherFailure,
                            fallbackRecording
                        )
                        stopRecordingAndDeleteSessionFiles()
                        RecognitionStatus.Done(RecognitionResult.Error(result, recognitionTask))
                    }
                }

                _status.update { recognitionResult }
            } finally {
                withContext(NonCancellable) {
                    recordingSession.cancelAndDeleteSessionFiles()
                    recordingController.release()
                }
            }
        }
    }

    override suspend fun cancelAndJoin() {
        if (isRecognitionJobCompleted) {
            _status.update { RecognitionStatus.Ready }
        } else {
            recognitionJob?.cancelAndJoin()
        }
    }

    override fun resetFinalStatus() {
        _status.update { oldStatus ->
            if (oldStatus is RecognitionStatus.Done) RecognitionStatus.Ready else oldStatus
        }
    }

    context(scope: CoroutineScope)
    private fun launchRecognitionIfPreviousCompleted(block: suspend CoroutineScope.() -> Unit) {
        if (isRecognitionJobCompleted) {
            recognitionJob = scope.launch(block = block).setCancellationHandler()
        }
    }

    private suspend fun processDeferredRecognition(
        fallbackAction: FallbackAction,
        fallbackRecording: CompletableDeferred<AudioRecording>?,
    ): RecognitionTask {
        if (!fallbackAction.save) return RecognitionTask.Ignored
        requireNotNull(fallbackRecording)
        return try {
            enqueueRecognition(fallbackRecording.await(), fallbackAction.launch)
        } catch (e: CancellationException) {
            throw e
        } catch (cause: Exception) {
            RecognitionTask.Error(cause)
        }
    }

    private suspend fun enqueueRecognition(
        audioRecording: AudioRecording,
        launched: Boolean
    ): RecognitionTask {
        val recognitionId = enqueuedRecognitionRepository.createRecognition(
            sample = audioRecording.toAudioSample(),
            title = ""
        )
        recognitionId ?: return RecognitionTask.Error()
        if (launched) {
            enqueuedRecognitionScheduler.enqueue(listOf(recognitionId))
        }
        return RecognitionTask.Created(recognitionId, launched)
    }

    private fun Job.setCancellationHandler() = apply {
        invokeOnCompletion { cause ->
            when (cause) {
                is CancellationException -> _status.update { RecognitionStatus.Ready }
            }
        }
    }
}

internal fun UserPreferences.recognitionServiceConfig(): RecognitionServiceConfig {
    return when (currentRecognitionProvider) {
        RecognitionProvider.Audd -> auddConfig
        RecognitionProvider.AcrCloud -> acrCloudConfig
        RecognitionProvider.Shazam -> ShazamConfig
    }
}