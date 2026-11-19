package com.mrsep.musicrecognizer.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal val Migration6To7 = object : Migration(6, 7) {

    private val deezerPattern by lazy {
        "^https://e-cdns-images\\.dzcdn\\.net/images/(?:cover|artist)/[-/a-z0-9]+/(\\d+)x(\\d+).*$".toRegex()
    }

    private val appleMusicPattern by lazy {
        "^https://[-.a-z0-9]*mzstatic\\.com/image/thumb/[-_./a-zA-Z0-9]+/(\\d+)x(\\d+)bb.*$".toRegex()
    }

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE track ADD COLUMN link_artwork_thumb TEXT")
        createThumbnails(connection)
    }

    private fun createThumbnails(connection: SQLiteConnection) {
        val trackIdToThumbnailUrl = mutableListOf<Pair<String, String>>()
        connection.prepare("SELECT id, link_artwork FROM track").use { statement ->
            while (statement.step()) {
                val trackId = statement.getText(0)
                val artworkUrl = if (statement.isNull(1)) null else statement.getText(1)
                if (artworkUrl == null) continue
                val thumbnailUrl = parseDeezerThumbnailUrl(artworkUrl)
                    ?: parseAppleMusicThumbnailUrl(artworkUrl)
                    ?: continue
                trackIdToThumbnailUrl.add(trackId to thumbnailUrl)
            }
        }
        if (trackIdToThumbnailUrl.isNotEmpty()) {
            connection.prepare("UPDATE track SET link_artwork_thumb = ? WHERE id = ?").use { statement ->
                trackIdToThumbnailUrl.forEach { (trackId, thumbnailUrl) ->
                    statement.bindText(1, thumbnailUrl)
                    statement.bindText(2, trackId)
                    statement.step()
                    statement.reset()
                }
            }
        }
    }

    private fun parseDeezerThumbnailUrl(artworkUrl: String): String? {
        val matchGroups = deezerPattern.find(artworkUrl)?.groups?.takeIf { it.size == 3 } ?: return null
        matchGroups[1]?.value?.toIntOrNull()?.takeIf { it >= 500 } ?: return null
        matchGroups[2]?.value?.toIntOrNull()?.takeIf { it >= 500 } ?: return null
        val widthRange = matchGroups[1]?.range ?: return null
        val heightRange = matchGroups[2]?.range ?: return null
        return artworkUrl.replaceRange(widthRange.first..heightRange.last, "250x250")
    }

    private fun parseAppleMusicThumbnailUrl(artworkUrl: String): String? {
        val matchGroups = appleMusicPattern.find(artworkUrl)?.groups?.takeIf { it.size == 3 } ?: return null
        matchGroups[1]?.value?.toIntOrNull()?.takeIf { it >= 500 } ?: return null
        matchGroups[2]?.value?.toIntOrNull()?.takeIf { it >= 500 } ?: return null
        val widthRange = matchGroups[1]?.range ?: return null
        val heightRange = matchGroups[2]?.range ?: return null
        return artworkUrl.replaceRange(widthRange.first..heightRange.last, "300x300")
    }
}
