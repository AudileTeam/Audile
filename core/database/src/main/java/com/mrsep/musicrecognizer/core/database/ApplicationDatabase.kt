package com.mrsep.musicrecognizer.core.database

import android.util.Log
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
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
@ColumnTypeConverters(
    value = [
        InstantRoomConverter::class,
        DurationRoomConverter::class,
        LocalDateRoomConverter::class,
    ]
)
abstract class ApplicationDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun enqueuedRecognitionDao(): EnqueuedRecognitionDao

    suspend fun getDataSize(): Long {
        val q = "SELECT page_count * page_size as size FROM pragma_page_count(), pragma_page_size()"
        return useReaderConnection { connection ->
            connection.usePrepared(q) { statement ->
                statement.step()
                statement.getLong(0)
            }
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
    private suspend fun checkout(): Boolean {
        return useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(FULL)") { statement ->
                if (!statement.step()) return@usePrepared false
                val busy = statement.getLong(0)
                val log = statement.getLong(1)
                val checkpointed = statement.getLong(2)
                if (busy != 0L) return@usePrepared false
                when {
                    log == -1L && checkpointed == -1L -> {
                        Log.w(DATABASE_NAME, "There is no write-ahead log for database")
                        true
                    }
                    log >= 0L && log == checkpointed -> true
                    else -> {
                        Log.w(DATABASE_NAME, "Incomplete WAL checkpoint: log=$log, checkpointed=$checkpointed")
                        false
                    }
                }
            }
        }
    }

    companion object {
        const val DATABASE_NAME = "application_database"
    }
}
