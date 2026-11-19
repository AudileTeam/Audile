package com.mrsep.musicrecognizer.core.database

import androidx.room3.ColumnTypeConverter
import java.time.LocalDate

internal class LocalDateRoomConverter {

    @ColumnTypeConverter
    fun timestampToLocalDate(epochDay: Long): LocalDate {
        return LocalDate.ofEpochDay(epochDay)
    }

    @ColumnTypeConverter
    fun localDateToTimestamp(date: LocalDate): Long {
        return date.toEpochDay()
    }
}
