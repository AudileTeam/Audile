package com.mrsep.musicrecognizer.core.database

import androidx.room3.ColumnTypeConverter
import java.time.Instant

internal class InstantRoomConverter {

    @ColumnTypeConverter
    fun instantToTimestamp(instant: Instant): Long {
        return instant.toEpochMilli()
    }

    @ColumnTypeConverter
    fun timestampToInstant(epochMillis: Long): Instant {
        return Instant.ofEpochMilli(epochMillis)
    }
}
