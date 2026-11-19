package com.mrsep.musicrecognizer.core.database.enqueued

import androidx.room3.*
import com.mrsep.musicrecognizer.core.database.DatabaseUtils.dbChunkedMap
import com.mrsep.musicrecognizer.core.database.DatabaseUtils.eachDbChunk
import com.mrsep.musicrecognizer.core.database.enqueued.model.EnqueuedRecognitionEntity
import com.mrsep.musicrecognizer.core.database.enqueued.model.EnqueuedRecognitionEntityWithTrack
import com.mrsep.musicrecognizer.core.database.enqueued.model.RemoteRecognitionResultType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface EnqueuedRecognitionDao {

    @Insert
    suspend fun insert(recognition: EnqueuedRecognitionEntity): Long

    @Query("SELECT * FROM enqueued_recognition WHERE id = :recognitionId")
    suspend fun getRecognition(recognitionId: Int): EnqueuedRecognitionEntity?

    @Query("UPDATE enqueued_recognition SET title = :newTitle WHERE id = :recognitionId")
    suspend fun updateTitle(recognitionId: Int, newTitle: String)

    @Query("SELECT record_file FROM enqueued_recognition WHERE id = :recognitionId")
    suspend fun getSampleFileName(recognitionId: Int): String?

    @Transaction
    suspend fun getSampleFileNames(recognitionIds: List<Int>): List<String> {
        return recognitionIds.dbChunkedMap(::getSampleFileNamesInternal)
    }

    @Query("SELECT record_file FROM enqueued_recognition WHERE id IN (:recognitionIds)")
    suspend fun getSampleFileNamesInternal(recognitionIds: List<Int>): List<String>

    @Transaction
    suspend fun delete(recognitionIds: List<Int>) {
        recognitionIds.eachDbChunk(::deleteInternal)
    }

    @Query("DELETE FROM enqueued_recognition WHERE id IN (:recognitionIds)")
    suspend fun deleteInternal(recognitionIds: List<Int>)

    @Query("DELETE FROM enqueued_recognition")
    suspend fun deleteAll()

    @Transaction
    @Query("SELECT * FROM enqueued_recognition WHERE id = :recognitionId")
    fun getRecognitionWithTrackFlow(recognitionId: Int): Flow<EnqueuedRecognitionEntityWithTrack?>

    @Transaction
    @Query("SELECT * FROM enqueued_recognition ORDER BY creation_date DESC")
    fun getAllRecognitionsWithTrackFlow(): Flow<List<EnqueuedRecognitionEntityWithTrack>>

    @Transaction
    suspend fun markQueuedFromIdle(recognitionIds: List<Int>, queuedAt: Instant) {
        recognitionIds.eachDbChunk { chunk ->
            markQueuedFromIdleInternal(chunk, queuedAt)
        }
    }

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'QUEUED',
            queued_at = :queuedAt,
            attempt_count = 0,
            next_attempt_at = NULL,
            result_type = NULL,
            result_track_id = NULL,
            result_message = NULL,
            result_date = NULL
        WHERE id IN (:recognitionIds) AND work_status = 'IDLE'
        """
    )
    suspend fun markQueuedFromIdleInternal(recognitionIds: List<Int>, queuedAt: Instant): Int

    @Transaction
    suspend fun claimNext(now: Instant): EnqueuedRecognitionEntity? {
        val id = selectNextEligibleId(now) ?: return null
        if (claimIfQueued(id) == 0) return null
        return getRecognition(id)
    }

    @Query(
        """
        SELECT id FROM enqueued_recognition
        WHERE work_status = 'QUEUED'
          AND ((queued_at <= :now AND next_attempt_at IS NULL) OR next_attempt_at <= :now)
        ORDER BY queued_at ASC, creation_date ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun selectNextEligibleId(now: Instant): Int?

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'RUNNING',
            result_type = NULL,
            result_track_id = NULL,
            result_message = NULL,
            result_date = NULL
        WHERE id = :recognitionId AND work_status = 'QUEUED'
        """
    )
    suspend fun claimIfQueued(recognitionId: Int): Int

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'IDLE',
            queued_at = NULL,
            attempt_count = 0,
            next_attempt_at = NULL,
            result_type = :resultType,
            result_track_id = :resultTrackId,
            result_message = :resultMessage,
            result_date = :resultDate
        WHERE id = :recognitionId
          AND work_status = 'RUNNING'
        """
    )
    suspend fun finishRun(
        recognitionId: Int,
        resultType: RemoteRecognitionResultType?,
        resultTrackId: String?,
        resultMessage: String?,
        resultDate: Instant?,
    ): Int

    // result on QUEUED is why the row is rescheduled (previous failure)
    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'QUEUED',
            attempt_count = attempt_count + 1,
            next_attempt_at = :nextAttemptAt,
            result_type = :resultType,
            result_track_id = NULL,
            result_message = :resultMessage,
            result_date = :resultDate
        WHERE id = :recognitionId
          AND work_status = 'RUNNING'
        """
    )
    suspend fun holdForRetry(
        recognitionId: Int,
        nextAttemptAt: Instant,
        resultType: RemoteRecognitionResultType?,
        resultMessage: String?,
        resultDate: Instant,
    ): Int

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'QUEUED'
        WHERE id = :recognitionId
          AND work_status = 'RUNNING'
        """
    )
    suspend fun returnToQueued(recognitionId: Int): Int

    @Transaction
    suspend fun cancelWork(recognitionIds: List<Int>) {
        recognitionIds.eachDbChunk(::cancelWorkInternal)
    }

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'IDLE',
            queued_at = NULL,
            attempt_count = 0,
            next_attempt_at = NULL,
            result_type = NULL,
            result_track_id = NULL,
            result_message = NULL,
            result_date = NULL
        WHERE id IN (:recognitionIds) AND work_status IN ('QUEUED', 'RUNNING')
        """
    )
    suspend fun cancelWorkInternal(recognitionIds: List<Int>): Int

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'IDLE',
            queued_at = NULL,
            attempt_count = 0,
            next_attempt_at = NULL,
            result_type = NULL,
            result_track_id = NULL,
            result_message = NULL,
            result_date = NULL
        WHERE work_status IN ('QUEUED', 'RUNNING')
        """
    )
    suspend fun cancelAllWork(): Int

    @Query(
        """
        UPDATE enqueued_recognition
        SET work_status = 'QUEUED'
        WHERE work_status = 'RUNNING'
        """
    )
    suspend fun resetStaleRunning(): Int

    @Query(QUEUED_NEXT_ATTEMPT_SQL)
    suspend fun queuedNextAttempt(): Instant?

    @Query(QUEUED_NEXT_ATTEMPT_SQL)
    fun queuedNextAttemptFlow(): Flow<Instant?>
}

private const val QUEUED_NEXT_ATTEMPT_SQL =
    """
    SELECT MIN(COALESCE(next_attempt_at, queued_at))
    FROM enqueued_recognition
    WHERE work_status = 'QUEUED'
    """
