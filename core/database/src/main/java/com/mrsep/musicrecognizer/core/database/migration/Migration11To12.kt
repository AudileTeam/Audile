package com.mrsep.musicrecognizer.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import java.io.File

// Store sample filenames instead of absolute paths so restore works across Android user ids
internal val Migration11To12 = object : Migration(11, 12) {

    override suspend fun migrate(connection: SQLiteConnection) {
        val updates = mutableListOf<Pair<Long, String>>()
        connection.prepare("SELECT id, record_file FROM enqueued_recognition").use { statement ->
            while (statement.step()) {
                val id = statement.getLong(0)
                if (statement.isNull(1)) continue
                val absolutePath = statement.getText(1)
                val fileName = File(absolutePath).name
                if (fileName == absolutePath) continue
                updates += id to fileName
            }
        }
        if (updates.isEmpty()) return
        connection.prepare("UPDATE enqueued_recognition SET record_file = ? WHERE id = ?").use { statement ->
            for ((id, fileName) in updates) {
                statement.bindText(1, fileName)
                statement.bindLong(2, id)
                statement.step()
                statement.reset()
            }
        }
    }
}
