package com.mrsep.musicrecognizer.feature.recognition.scheduler

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkError
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksSource
import com.mrsep.musicrecognizer.core.metadata.tracklink.jiosaavn.JioSaavnTrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.musicbrainz.MusicBrainzTrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.odesli.OdesliTrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.qobuz.QobuzTrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.youtube.YoutubeTrackLinksFetcher
import com.mrsep.musicrecognizer.feature.recognition.scheduler.TrackMetadataFetchManagerImpl.Companion.buildWorkTagForTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.UUID

@HiltWorker
internal class TrackLinksFetchWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackRepository: TrackRepository,
    private val preferencesRepository: PreferencesRepository,
    private val odesliTrackLinksFetcher: OdesliTrackLinksFetcher,
    private val youtubeTrackLinksFetcher: YoutubeTrackLinksFetcher,
    private val qobuzTrackLinksFetcher: QobuzTrackLinksFetcher,
    private val musicBrainzTrackLinksFetcher: MusicBrainzTrackLinksFetcher,
    private val jioSaavnTrackLinksFetcher: JioSaavnTrackLinksFetcher,
) : CoroutineWorker(appContext, workerParams) {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        Log.d(TAG, "$TAG started with attempt #$runAttemptCount")
        val trackId = inputData.getTrackId()
        val allowedSources = inputData.getAllowedSources()

        val requiredServices = preferencesRepository.userPreferencesFlow.first()
            .requiredMusicServices.toSet().ifEmpty { return Result.success() }

        val failedSourcesToRetry = fetchAndUpdateTrackLinks(trackId, requiredServices, allowedSources)

        return when {
            failedSourcesToRetry.isEmpty() -> Result.success()

            runAttemptCount >= MAX_ATTEMPTS -> {
                Log.w(TAG, "$TAG canceled, attempt=$runAttemptCount, maxAttempts=$MAX_ATTEMPTS")
                Result.failure()
            }

            else -> {
                val workManager = WorkManager.getInstance(appContext)
                val updatedRequest = buildOneTimeWorkRequest(trackId, failedSourcesToRetry, id)
                val updateResult = workManager.updateWork(updatedRequest).await()
                if (updateResult == WorkManager.UpdateResult.NOT_APPLIED) {
                    Log.e(TAG, "Failed to update work request")
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        }
    }

    /*  Returns the set of sources whose fetch failed and should be retried */
    private suspend fun fetchAndUpdateTrackLinks(
        trackId: String,
        requiredServices: Set<MusicService>,
        allowedSources: Set<TrackLinksSource>,
    ): Set<TrackLinksSource> {
        if (requiredServices.isEmpty() || allowedSources.isEmpty()) return emptySet()

        val fetcherBatches = listOf(
            listOf(
                odesliTrackLinksFetcher,
                qobuzTrackLinksFetcher,
                musicBrainzTrackLinksFetcher,
                jioSaavnTrackLinksFetcher,
            ),
            listOf(youtubeTrackLinksFetcher),
        ).mapNotNull { batch ->
            batch
                .filter { fetcher ->
                    fetcher.source in allowedSources &&
                    fetcher.supportedServices.any(requiredServices::contains)
                }
                .takeIf { it.isNotEmpty() }
        }

        val sourcesToRetry = mutableSetOf<TrackLinksSource>()

        for (batch in fetcherBatches) {
            // Get the latest track snapshot for each batch
            val track = trackRepository.getTrackFlow(trackId).first() ?: return emptySet()

            val fetchersToRun = batch.filter { fetcher ->
                val supportedServices = fetcher.supportedServices intersect requiredServices
                supportedServices.any { service -> service !in track.trackLinks }
            }

            if (fetchersToRun.isEmpty()) continue

            channelFlow {
                fetchersToRun.forEach { fetcher ->
                    launch { send(fetcher.source to fetcher.fetch(track)) }
                }
            }.collect { (source, result) ->
                when (result) {
                    is NetworkResult.Success -> {
                        trackRepository.updateTransform(trackId) { previous ->
                            val mergedLinks = buildMap {
                                putAll(previous.trackLinks)
                                result.data.trackLinks.forEach(::putIfAbsent)
                            }
                            previous.copy(
                                artworkThumbUrl = previous.artworkThumbUrl ?: result.data.artworkThumbUrl,
                                artworkUrl = previous.artworkUrl ?: result.data.artworkUrl,
                                trackLinks = mergedLinks
                            )
                        }
                    }

                    is NetworkError.BadConnection -> sourcesToRetry += source
                    is NetworkError.HttpError -> if (result.isServerError) sourcesToRetry += source
                    is NetworkError.UnhandledError -> { } // Ignore unrecoverable errors
                }
            }
        }

        return pruneRetrySources(trackId, requiredServices, sourcesToRetry)
    }


    private suspend fun pruneRetrySources(
        trackId: String,
        requiredServices: Set<MusicService>,
        failedSources: Set<TrackLinksSource>,
    ): Set<TrackLinksSource> {
        if (failedSources.isEmpty()) return emptySet()

        val currentTrack = trackRepository.getTrackFlow(trackId).first() ?: return emptySet()
        val missingServices = requiredServices - currentTrack.trackLinks.keys

        if (missingServices.isEmpty()) return emptySet()

        return failedSources.filterTo(mutableSetOf()) { source ->
            val supportedServices = source.fetcher().supportedServices
            supportedServices.any(missingServices::contains)
        }
    }

    private fun TrackLinksSource.fetcher(): TrackLinksFetcher = when (this) {
        TrackLinksSource.Odesli -> odesliTrackLinksFetcher
        TrackLinksSource.YouTube -> youtubeTrackLinksFetcher
        TrackLinksSource.Qobuz -> qobuzTrackLinksFetcher
        TrackLinksSource.MusicBrainz -> musicBrainzTrackLinksFetcher
        TrackLinksSource.JioSaavn -> jioSaavnTrackLinksFetcher
    }

    companion object {
        const val TAG = "TrackLinksFetchWorker"
        private const val MAX_ATTEMPTS = 3
        private const val INPUT_KEY_TRACK_ID = "TRACK_ID"
        private const val INPUT_KEY_ALLOWED_SOURCES = "ALLOWED_SOURCES"

        fun buildUniqueWorkerName(trackId: String) = "TRACK_LINKS_FETCHER_ID#$trackId"

        fun buildOneTimeWorkRequest(
            trackId: String,
            allowedSources: Set<TrackLinksSource> = TrackLinksSource.entries.toSet(),
            workId: UUID? = null,
        ): OneTimeWorkRequest {
            val data = Data.Builder()
                .putString(INPUT_KEY_TRACK_ID, trackId)
                .putStringArray(INPUT_KEY_ALLOWED_SOURCES, allowedSources.toNameArray())
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            return OneTimeWorkRequestBuilder<TrackLinksFetchWorker>()
                .apply { workId?.let { setId(workId) } }
                .addTag(TAG)
                .addTag(buildWorkTagForTrack(trackId))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .setConstraints(constraints)
                .setInputData(data)
                .build()
        }

        private fun Set<TrackLinksSource>.toNameArray(): Array<String?> =
            map(TrackLinksSource::name).toTypedArray()

        private fun Data.getTrackId() = getString(INPUT_KEY_TRACK_ID)
            ?: error("$TAG requires track ID as parameter")

        private fun Data.getAllowedSources() = getNullableStringArray(INPUT_KEY_ALLOWED_SOURCES)
            ?.mapNotNull { source ->
                source?.let { runCatching { TrackLinksSource.valueOf(source) }.getOrNull() }
            }
            ?.toSet()
            ?: error("$TAG requires set of allowed sources as parameter")
    }
}
