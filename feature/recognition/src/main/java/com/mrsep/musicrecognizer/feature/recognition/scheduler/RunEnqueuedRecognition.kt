package com.mrsep.musicrecognizer.feature.recognition.scheduler

import android.content.Context
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionRepository
import com.mrsep.musicrecognizer.core.domain.recognition.RecognitionServiceFactory
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.recognition.model.ClaimedEnqueuedRecognition
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionWorkStatus
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.ui.util.generateThemeSeedColor
import com.mrsep.musicrecognizer.feature.recognition.domain.recognitionServiceConfig
import com.mrsep.musicrecognizer.feature.recognition.service.ResultNotificationHelper
import com.mrsep.musicrecognizer.feature.recognition.service.ext.downloadImageToDiskCache
import com.mrsep.musicrecognizer.feature.recognition.service.ext.getCachedImageOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.math.pow

internal class RunEnqueuedRecognition @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val recognitionServiceFactory: RecognitionServiceFactory,
    private val trackRepository: TrackRepository,
    private val preferencesRepository: PreferencesRepository,
    private val enqueuedRecognitionRepository: EnqueuedRecognitionRepository,
    private val resultNotificationHelper: ResultNotificationHelper,
    private val trackMetadataFetchManager: TrackMetadataFetchManager,
) {

    suspend operator fun invoke(claimed: ClaimedEnqueuedRecognition) {
        val enqueued = enqueuedRecognitionRepository
            .getRecognitionFlow(claimed.id)
            .first() ?: return
        val sample = enqueuedRecognitionRepository.getAudioSample(claimed.id) ?: let {
            return finish(
                claimed = claimed,
                result = RemoteRecognitionResult.Error.BadRecording("Failed to read audio sample file"),
            )
        }

        val userPreferences = preferencesRepository.userPreferencesFlow.first()
        val serviceConfig = userPreferences.recognitionServiceConfig()
        val result = recognitionServiceFactory.getService(serviceConfig).recognize(sample)

        return when (result) {
            is RemoteRecognitionResult.Success -> {
                val resultDate = Instant.now()
                val updatedTrack = enqueuedRecognitionRepository.finishSuccessfulRun(
                    recognitionId = claimed.id,
                    track = result.track,
                    resultDate = resultDate,
                ) ?: return
                val updatedResult = result.copy(track = updatedTrack)
                val latestPreferences = preferencesRepository.userPreferencesFlow.first()
                val shouldSearchForLinks = latestPreferences
                    .requiredMusicServices.any { it !in updatedTrack.trackLinks }
                if (shouldSearchForLinks) {
                    trackMetadataFetchManager.enqueueTrackLinksFetcher(updatedTrack.id)
                }
                if (updatedTrack.lyrics == null) {
                    trackMetadataFetchManager.enqueueLyricsFetcher(updatedTrack.id)
                }
                prepareTrackImages(updatedTrack)
                resultNotificationHelper.notifyResult(
                    enqueued.copy(
                        result = updatedResult,
                        resultDate = resultDate,
                        workStatus = RecognitionWorkStatus.IDLE,
                    )
                )
                if (latestPreferences.autoDeleteSavedRecordingOnMatch) {
                    enqueuedRecognitionRepository.delete(listOf(claimed.id))
                }
                Unit
            }

            RemoteRecognitionResult.NoSoundDetected,
            is RemoteRecognitionResult.NoMatches -> finish(claimed, result)

            RemoteRecognitionResult.Error.BadConnection,
            is RemoteRecognitionResult.Error.ApiUsageLimited -> retryOrFinish(claimed, result)

            is RemoteRecognitionResult.Error.HttpError -> {
                if (result.code in 400..499 && result.code != 429) {
                    finish(claimed, result)
                } else {
                    retryOrFinish(claimed, result)
                }
            }

            is RemoteRecognitionResult.Error.AuthError,
            is RemoteRecognitionResult.Error.BadRecording,
            is RemoteRecognitionResult.Error.UnhandledError -> finish(claimed, result)
        }
    }

    private suspend fun retryOrFinish(
        claimed: ClaimedEnqueuedRecognition,
        result: RemoteRecognitionResult,
    ) {
        val nextAttempt = claimed.attemptCount + 1
        if (nextAttempt >= MAX_ATTEMPTS) {
            return finish(claimed, result)
        }
        val now = Instant.now()
        enqueuedRecognitionRepository.holdForRetry(
            recognitionId = claimed.id,
            nextAttemptAt = now.plusMillis(retryDelayMs(nextAttempt)),
            result = result,
            resultDate = now,
        )
    }

    private suspend fun finish(
        claimed: ClaimedEnqueuedRecognition,
        result: RemoteRecognitionResult,
    ) {
        enqueuedRecognitionRepository.finishRun(
            recognitionId = claimed.id,
            result = result,
            resultDate = Instant.now(),
        )
    }

    private suspend fun prepareTrackImages(track: Track) = withContext(Dispatchers.Default) {
        val urls = listOfNotNull(track.artworkThumbUrl, track.artworkUrl).distinct()
        urls
            .map { imageUrl -> async { appContext.downloadImageToDiskCache(imageUrl) } }
            .awaitAll()
        // Thumb first: it is the image the UI shows first, and cheaper to decode.
        urls
            .firstNotNullOfOrNull { url -> appContext.getCachedImageOrNull(url, allowHardware = false) }
            ?.generateThemeSeedColor()
            ?.let { themeColor -> trackRepository.setThemeSeedColor(track.id, themeColor) }
    }

    companion object {
        const val MAX_ATTEMPTS = 3

        fun retryDelayMs(attemptCount: Int): Long {
            val exponent = (attemptCount - 1).coerceAtLeast(0)
            val delayMs = 30_000.0 * 2.0.pow(exponent)
            return delayMs.coerceAtMost(5 * 60_000.0).toLong()
        }
    }
}
