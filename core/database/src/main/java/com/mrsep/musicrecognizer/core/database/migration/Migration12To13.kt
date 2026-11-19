package com.mrsep.musicrecognizer.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val Migration12To13 = object : Migration(12, 13) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE enqueued_recognition ADD COLUMN work_status TEXT NOT NULL DEFAULT 'IDLE'")
        connection.execSQL("ALTER TABLE enqueued_recognition ADD COLUMN queued_at INTEGER")
        connection.execSQL("ALTER TABLE enqueued_recognition ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE enqueued_recognition ADD COLUMN next_attempt_at INTEGER")
    }
}
