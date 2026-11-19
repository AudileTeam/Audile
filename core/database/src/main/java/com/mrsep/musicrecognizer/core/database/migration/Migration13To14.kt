package com.mrsep.musicrecognizer.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val Migration13To14 = object : Migration(13, 14) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE track ADD COLUMN link_jiosaavn TEXT")
    }
}
