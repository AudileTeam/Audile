package com.mrsep.musicrecognizer.core.database.track

import androidx.room3.ColumnInfo
import java.time.Instant

data class TrackPreviewTuple(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "album")
    val album: String?,
    @ColumnInfo(name = "recognition_date")
    val recognitionDate: Instant,
    @ColumnInfo(name = "link_artwork_thumb")
    val artworkThumbnail: String?,
    @ColumnInfo(name = "link_artwork")
    val artwork: String?,
    @ColumnInfo(name = "is_viewed")
    val isViewed: Boolean,
)
