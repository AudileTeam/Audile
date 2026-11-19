package com.mrsep.musicrecognizer.core.data.enqueued

import androidx.room3.withWriteTransaction
import com.mrsep.musicrecognizer.core.common.di.ApplicationScope
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.data.PersistentStoreLock
import com.mrsep.musicrecognizer.core.data.track.toDomain
import com.mrsep.musicrecognizer.core.data.track.toEntity
import com.mrsep.musicrecognizer.core.database.ApplicationDatabase
import com.mrsep.musicrecognizer.core.database.enqueued.model.EnqueuedRecognitionEntity
import com.mrsep.musicrecognizer.core.database.enqueued.model.RemoteRecognitionResultType
import com.mrsep.musicrecognizer.core.domain.recognition.AudioSample
import com.mrsep.musicrecognizer.core.domain.recognition.EnqueuedRecognitionRepository
import com.mrsep.musicrecognizer.core.domain.recognition.model.ClaimedEnqueuedRecognition
import com.mrsep.musicrecognizer.core.domain.recognition.model.EnqueuedRecognition
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

internal class EnqueuedRecognitionRepositoryImpl @Inject constructor(
    private val audioSampleDataSource: AudioSampleDataSource,
    private val storeLock: PersistentStoreLock,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val database: ApplicationDatabase
) : EnqueuedRecognitionRepository {

    private val dao = database.enqueuedRecognitionDao()
    private val trackDao = database.trackDao()
    private val persistentCoroutineContext = appScope.coroutineContext + ioDispatcher

    // App start must not clear a claim this process has already made
    private val staleRunningMutex = Mutex()
    private var hasResetStaleRunning = false

    override suspend fun createRecognition(sample: AudioSample, title: String): Int? {
        return withStoreWrite {
            audioSampleDataSource.copy(sample)?.let { sample ->
                val enqueued = EnqueuedRecognitionEntity(
                    id = 0,
                    title = title,
                    sampleFileName = sample.file.name,
                    creationDate = sample.timestamp
                )
                dao.insert(enqueued).toInt()
            }
        }
    }

    override suspend fun updateTitle(recognitionId: Int, newTitle: String) {
        withStoreWrite {
            dao.updateTitle(recognitionId, newTitle)
        }
    }

    override suspend fun getAudioSampleFile(recognitionId: Int): File? {
        return withContext(ioDispatcher) {
            dao.getSampleFileName(recognitionId)?.let(audioSampleDataSource::resolve)
        }
    }

    override suspend fun getAudioSample(recognitionId: Int): AudioSample? {
        return withContext(ioDispatcher) {
            dao.getRecognition(recognitionId)?.let { recognition ->
                audioSampleDataSource.read(
                    audioSampleDataSource.resolve(recognition.sampleFileName),
                    recognition.creationDate
                )
            }
        }
    }

    override suspend fun delete(recognitionIds: List<Int>) {
        withStoreWrite {
            val files = dao.getSampleFileNames(recognitionIds)
                .map(audioSampleDataSource::resolve)
            dao.delete(recognitionIds)
            files.forEach { file -> audioSampleDataSource.delete(file) }
        }
    }

    override suspend fun deleteAll() {
        withStoreWrite {
            dao.deleteAll()
            audioSampleDataSource.deleteAll()
        }
    }

    override fun getRecognitionFlow(recognitionId: Int): Flow<EnqueuedRecognition?> {
        return dao.getRecognitionWithTrackFlow(recognitionId)
            .map { entity ->
                entity?.toDomain(audioSampleDataSource.resolve(entity.enqueued.sampleFileName))
            }
            .flowOn(ioDispatcher)
    }

    override fun getAllRecognitionsFlow(): Flow<List<EnqueuedRecognition>> {
        return dao.getAllRecognitionsWithTrackFlow()
            .map { list ->
                list.map { entity ->
                    entity.toDomain(audioSampleDataSource.resolve(entity.enqueued.sampleFileName))
                }
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun markQueued(recognitionIds: List<Int>, queuedAt: Instant) {
        if (recognitionIds.isEmpty()) return
        withStoreWrite {
            dao.markQueuedFromIdle(recognitionIds, queuedAt)
        }
    }

    override suspend fun claimNext(now: Instant): ClaimedEnqueuedRecognition? {
        return withStoreWrite {
            dao.claimNext(now)?.toClaimed()
        }
    }

    override suspend fun finishRun(
        recognitionId: Int,
        result: RemoteRecognitionResult?,
        resultDate: Instant?,
    ): Boolean {
        return withStoreWrite {
            val columns = result.toResultColumns()
            dao.finishRun(
                recognitionId = recognitionId,
                resultType = columns.resultType,
                resultTrackId = columns.resultTrackId,
                resultMessage = columns.resultMessage,
                resultDate = resultDate,
            ) > 0
        }
    }

    override suspend fun finishSuccessfulRun(
        recognitionId: Int,
        track: Track,
        resultDate: Instant,
    ): Track? {
        return withStoreWrite {
            try {
                database.withWriteTransaction {
                    val stored = trackDao.upsertFromRecognition(track.toEntity()).toDomain()
                    val written = dao.finishRun(
                        recognitionId = recognitionId,
                        resultType = RemoteRecognitionResultType.Success,
                        resultTrackId = stored.id,
                        resultMessage = null,
                        resultDate = resultDate,
                    )
                    // Status is no longer RUNNING: roll back the track upsert
                    if (written == 0) throw FinishNotAppliedException()
                    stored
                }
            } catch (_: FinishNotAppliedException) {
                null
            }
        }
    }

    override suspend fun holdForRetry(
        recognitionId: Int,
        nextAttemptAt: Instant,
        result: RemoteRecognitionResult,
        resultDate: Instant,
    ): Boolean {
        return withStoreWrite {
            val columns = result.toResultColumns()
            dao.holdForRetry(
                recognitionId = recognitionId,
                nextAttemptAt = nextAttemptAt,
                resultType = columns.resultType,
                resultMessage = columns.resultMessage,
                resultDate = resultDate,
            ) > 0
        }
    }

    override suspend fun returnToQueued(recognitionId: Int): Boolean {
        return withStoreWrite {
            dao.returnToQueued(recognitionId) > 0
        }
    }

    override suspend fun cancelWork(recognitionIds: List<Int>) {
        if (recognitionIds.isEmpty()) return
        withStoreWrite {
            dao.cancelWork(recognitionIds)
        }
    }

    override suspend fun cancelAllWork() {
        withStoreWrite {
            dao.cancelAllWork()
        }
    }

    override suspend fun resetStaleRunningBeforeClaim() {
        staleRunningMutex.withLock {
            withStoreWrite { dao.resetStaleRunning() }
            hasResetStaleRunning = true
        }
    }

    override suspend fun resetStaleRunningOnAppStart() {
        staleRunningMutex.withLock {
            if (hasResetStaleRunning) return@withLock
            withStoreWrite { dao.resetStaleRunning() }
        }
    }

    override suspend fun queuedNextAttempt(): Instant? {
        return dao.queuedNextAttempt()
    }

    override fun queuedNextAttemptFlow(): Flow<Instant?> {
        return dao.queuedNextAttemptFlow()
    }

    private fun EnqueuedRecognitionEntity.toClaimed() = ClaimedEnqueuedRecognition(
        id = id,
        attemptCount = attemptCount,
    )

    private suspend fun <T> withStoreWrite(block: suspend () -> T): T =
        withContext(persistentCoroutineContext) {
            storeLock.withShared(block)
        }
}

private class FinishNotAppliedException : Exception()
