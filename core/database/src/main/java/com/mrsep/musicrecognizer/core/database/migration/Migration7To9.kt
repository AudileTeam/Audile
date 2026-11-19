package com.mrsep.musicrecognizer.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val Migration7To8 = object : Migration(7, 8) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE track ADD COLUMN is_lyrics_synced INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE track SET recognition_date = recognition_date * 1000")
    }
}

// Due to changes in InstantRoomConverter (37692e5f) and released incomplete Migration7To8,
// table 'enqueued_recognition' contains dates in mixed formats: epoch seconds/millis.
// Migration8To9 fixes this by converting seconds to milliseconds.
internal val Migration8To9 = object : Migration(8, 9) {

    override suspend fun migrate(connection: SQLiteConnection) {
        // A reasonable threshold is 10_000_000_000L
        // ::ofEpochSecond 2286-11-20T17:46:40Z, ::ofEpochMilli 1970-04-26T17:46:40Z
        // Avoid using exact release dates since device clocks (stored dates) might be inaccurate
        val threshold = 10_000_000_000L
        connection.execSQL(
            """
            UPDATE enqueued_recognition 
            SET 
                creation_date = CASE 
                    WHEN creation_date < $threshold THEN creation_date * 1000 
                    ELSE creation_date 
                END, 
                result_date = CASE 
                    WHEN result_date IS NOT NULL AND result_date < $threshold 
                    THEN result_date * 1000 
                    ELSE result_date 
                END
            """.trimIndent()
        )
    }
}

// Shortcut
internal val Migration7To9 = object : Migration(7, 9) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE track ADD COLUMN is_lyrics_synced INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("UPDATE track SET recognition_date = recognition_date * 1000")
        connection.execSQL("""
            UPDATE enqueued_recognition 
            SET 
                creation_date = creation_date * 1000, 
                result_date = CASE 
                    WHEN result_date IS NOT NULL 
                    THEN result_date * 1000 
                    ELSE result_date 
                END
        """.trimIndent())
    }
}
