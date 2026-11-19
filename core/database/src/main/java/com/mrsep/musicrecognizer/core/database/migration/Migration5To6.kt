package com.mrsep.musicrecognizer.core.database.migration

import android.os.Build
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mrsep.musicrecognizer.core.database.migration.DatabaseMigrationUtils.isSQLiteVersionAtLeast

internal val Migration5To6 = object : Migration(5, 6) {

    override suspend fun migrate(connection: SQLiteConnection) {
        migrateTrackTable(connection)
        migrateEnqueuedRecognitionTable(connection)
    }

    private fun migrateTrackTable(connection: SQLiteConnection) {
        // APIs lower than 30 have SQLite < v3.25.0 by default, which does not support RENAME COLUMN
        val renameAllowed = connection.isSQLiteVersionAtLeast("3.25.0")
            ?: (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
        if (renameAllowed) {
            connection.execSQL("ALTER TABLE track RENAME COLUMN last_recognition_date TO recognition_date")
            connection.execSQL("ALTER TABLE track ADD COLUMN recognized_by TEXT NOT NULL DEFAULT 'Audd'")
            connection.execSQL("ALTER TABLE track ADD COLUMN is_viewed INTEGER NOT NULL DEFAULT 1")
        } else {
            connection.execSQL("CREATE TABLE track_new (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT, `release_date` INTEGER, `duration` INTEGER, `recognized_at` INTEGER, `recognized_by` TEXT NOT NULL, `recognition_date` INTEGER NOT NULL, `lyrics` TEXT, `link_artwork` TEXT, `link_amazon_music` TEXT, `link_anghami` TEXT, `link_apple_music` TEXT, `link_audiomack` TEXT, `link_audius` TEXT, `link_boomplay` TEXT, `link_deezer` TEXT, `link_musicbrainz` TEXT, `link_napster` TEXT, `link_pandora` TEXT, `link_soundcloud` TEXT, `link_spotify` TEXT, `link_tidal` TEXT, `link_yandex_music` TEXT, `link_youtube` TEXT, `link_youtube_music` TEXT, `is_favorite` INTEGER NOT NULL, `is_viewed` INTEGER NOT NULL, `theme_seed_color` INTEGER, PRIMARY KEY(`id`))")
            connection.execSQL("INSERT INTO track_new (`id`, `title`, `artist`, `album`, `release_date`, `duration`, `recognized_at`, `recognized_by`, `recognition_date`, `lyrics`, `link_artwork`, `link_amazon_music`, `link_anghami`, `link_apple_music`, `link_audiomack`, `link_audius`, `link_boomplay`, `link_deezer`, `link_musicbrainz`, `link_napster`, `link_pandora`, `link_soundcloud`, `link_spotify`, `link_tidal`, `link_yandex_music`, `link_youtube`, `link_youtube_music`, `is_favorite`, `is_viewed`, `theme_seed_color`) SELECT `id`, `title`, `artist`, `album`, `release_date`, `duration`, `recognized_at`, 'Audd' AS `recognized_by`, `last_recognition_date` AS `recognition_date`, `lyrics`, `link_artwork`, `link_amazon_music`, `link_anghami`, `link_apple_music`, `link_audiomack`, `link_audius`, `link_boomplay`, `link_deezer`, `link_musicbrainz`, `link_napster`, `link_pandora`, `link_soundcloud`, `link_spotify`, `link_tidal`, `link_yandex_music`, `link_youtube`, `link_youtube_music`, `is_favorite`, 1 AS `is_viewed`, `theme_seed_color` FROM track")
            connection.execSQL("DROP TABLE track")
            connection.execSQL("ALTER TABLE track_new RENAME TO track")
        }
        connection.execSQL("CREATE INDEX index_track_is_viewed ON track (is_viewed)")
    }

    private fun migrateEnqueuedRecognitionTable(connection: SQLiteConnection) {
        connection.execSQL("""
            UPDATE enqueued_recognition
            SET result_type = CASE 
                WHEN result_type = 'WrongToken' THEN 'AuthError' 
                WHEN result_type = 'LimitedToken' THEN 'ApiUsageLimited' 
                ELSE result_type
            END
        """)
    }
}
