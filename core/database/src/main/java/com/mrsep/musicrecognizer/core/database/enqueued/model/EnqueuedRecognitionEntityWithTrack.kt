package com.mrsep.musicrecognizer.core.database.enqueued.model

import androidx.room3.Embedded
import androidx.room3.Relation
import com.mrsep.musicrecognizer.core.database.track.TrackEntity

data class EnqueuedRecognitionEntityWithTrack(
    @Embedded val enqueued: EnqueuedRecognitionEntity,
    @Relation(
        parentColumns = ["result_track_id"],
        entityColumns = ["id"],
    )
    val track: TrackEntity?
)
