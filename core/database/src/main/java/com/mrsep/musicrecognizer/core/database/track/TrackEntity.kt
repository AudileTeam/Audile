package com.mrsep.musicrecognizer.core.database.track

import androidx.room.*
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration

@Entity(
    tableName = "track",
    indices = [
        Index(value = ["is_viewed"]),
    ],
)
data class TrackEntity(
    @PrimaryKey @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "album")
    val album: String?,
    @ColumnInfo(name = "release_date")
    val releaseDate: LocalDate?,
    @ColumnInfo(name = "duration")
    val duration: Duration?,
    @ColumnInfo(name = "recognized_at")
    val recognizedAt: Duration?,
    @ColumnInfo(name = "recognized_by")
    val recognizedBy: RecognitionProvider,
    @ColumnInfo(name = "recognition_date")
    val recognitionDate: Instant,
    @ColumnInfo(name = "lyrics")
    val lyrics: String?,
    @ColumnInfo(name = "is_lyrics_synced")
    val isLyricsSynced: Boolean,
    @ColumnInfo(name = "isrc")
    val isrc: String?,
    @Embedded(prefix = "link_")
    val links: Links,
    @Embedded
    val properties: Properties,
) {

    data class Links(
        @ColumnInfo(name = "artwork_thumb")
        val artworkThumbnail: String?,
        @ColumnInfo(name = "artwork")
        val artwork: String?,
        @ColumnInfo(name = "amazon_music")
        val amazonMusic: String?,
        @ColumnInfo(name = "anghami")
        val anghami: String?,
        @ColumnInfo(name = "apple_music")
        val appleMusic: String?,
        @ColumnInfo(name = "audiomack")
        val audiomack: String?,
        @ColumnInfo(name = "audius")
        val audius: String?,
        @ColumnInfo(name = "boomplay")
        val boomplay: String?,
        @ColumnInfo(name = "deezer")
        val deezer: String?,
        @ColumnInfo(name = "jiosaavn")
        val jioSaavn: String?,
        @ColumnInfo(name = "musicbrainz")
        val musicBrainz: String?,
        @ColumnInfo(name = "napster")
        val napster: String?,
        @ColumnInfo(name = "pandora")
        val pandora: String?,
        @ColumnInfo(name = "qobuz")
        val qobuz: String?,
        @ColumnInfo(name = "soundcloud")
        val soundCloud: String?,
        @ColumnInfo(name = "spotify")
        val spotify: String?,
        @ColumnInfo(name = "tidal")
        val tidal: String?,
        @ColumnInfo(name = "yandex_music")
        val yandexMusic: String?,
        @ColumnInfo(name = "youtube")
        val youtube: String?,
        @ColumnInfo(name = "youtube_music")
        val youtubeMusic: String?,
    )

    data class Properties(
        @ColumnInfo(name = "is_favorite")
        val isFavorite: Boolean,
        @ColumnInfo(name = "is_viewed")
        val isViewed: Boolean,
        @ColumnInfo(name = "theme_seed_color")
        val themeSeedColor: Int?,
    )
}
