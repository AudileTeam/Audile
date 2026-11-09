package com.mrsep.musicrecognizer.core.database

import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mrsep.musicrecognizer.core.database.enqueued.EnqueuedRecognitionDao
import com.mrsep.musicrecognizer.core.database.enqueued.model.EnqueuedRecognitionEntity
import com.mrsep.musicrecognizer.core.database.migration.AutoMigrationSpec3To4
import com.mrsep.musicrecognizer.core.database.track.TrackDao
import com.mrsep.musicrecognizer.core.database.track.TrackEntity
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Database(
    entities = [
        TrackEntity::class,
        EnqueuedRecognitionEntity::class,
    ],
    version = 14,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = AutoMigrationSpec3To4::class),
        AutoMigration(from = 4, to = 5),
    ]
)
@TypeConverters(
    value = [
        InstantRoomConverter::class,
        DurationRoomConverter::class,
        LocalDateRoomConverter::class,
    ]
)
abstract class ApplicationDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun enqueuedRecognitionDao(): EnqueuedRecognitionDao

    fun getDataSize(): Long {
        val q = "SELECT page_count * page_size as size FROM pragma_page_count(), pragma_page_size()"
        return query(SimpleSQLiteQuery(q)).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }

    suspend fun checkoutWithRetry(): Boolean {
        var attemptCount = 1
        while (attemptCount <= 4) {
            if (checkout()) return true
            Log.i(DATABASE_NAME, "Database checkpoint was blocked, retry")
            delay(500.milliseconds * attemptCount)
            attemptCount++
        }
        return false
    }

    // https://www.sqlite.org/pragma.html#pragma_wal_checkpoint
    // TRUNCATE waits for all readers so it can reset the WAL, FULL does not
    private fun checkout(): Boolean {
        return query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val busy = cursor.getInt(0)
            val log = cursor.getInt(1)
            val checkpointed = cursor.getInt(2)
            if (busy != 0) return false
            when {
                log == -1 && checkpointed == -1 -> {
                    Log.w(DATABASE_NAME, "There is no write-ahead log for database")
                    true
                }
                log >= 0 && log == checkpointed -> true
                else -> {
                    Log.w(DATABASE_NAME, "Incomplete WAL checkpoint: log=$log, checkpointed=$checkpointed")
                    false
                }
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "application_database"
    }
}
