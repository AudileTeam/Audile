package com.mrsep.musicrecognizer.core.database

import androidx.room3.ColumnTypeConverter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class DurationRoomConverter {

    @ColumnTypeConverter
    fun durationToTimestamp(duration: Duration): Long {
        return duration.inWholeMilliseconds
    }

    @ColumnTypeConverter
    fun timestampToDuration(millis: Long): Duration {
        return millis.milliseconds
    }
}
