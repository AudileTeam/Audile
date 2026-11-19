package com.mrsep.musicrecognizer.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val Migration10To11 = object : Migration(10, 11) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE track ADD COLUMN isrc TEXT")
        connection.execSQL("ALTER TABLE track ADD COLUMN link_qobuz TEXT")
    }
}
