package com.mrsep.musicrecognizer.core.database.enqueued.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.mrsep.musicrecognizer.core.database.track.TrackEntity
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionWorkStatus
import java.time.Instant

@Entity(
    tableName = "enqueued_recognition",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("result_track_id"),
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["result_track_id"])]
)
data class EnqueuedRecognitionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id")
    val id: Int,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "record_file")
    val sampleFileName: String,
    @ColumnInfo(name = "creation_date")
    val creationDate: Instant,

    @ColumnInfo(name = "work_status")
    val workStatus: RecognitionWorkStatus = RecognitionWorkStatus.IDLE,
    @ColumnInfo(name = "queued_at")
    val queuedAt: Instant? = null,
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Instant? = null,

    // result can't be embedded object due to constraint of foreign key indexing, see Index docs
    @ColumnInfo(name = "result_type")
    val resultType: RemoteRecognitionResultType? = null,
    @ColumnInfo(name = "result_track_id")
    val resultTrackId: String? = null,
    @ColumnInfo(name = "result_message")
    val resultMessage: String? = null,
    @ColumnInfo(name = "result_date")
    val resultDate: Instant? = null,
)
