package com.mrsep.musicrecognizer.core.domain.track.model

import java.time.Instant

data class TrackPreview(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkThumbUrl: String?,
    val artworkUrl: String?,
    val recognitionDate: Instant,
    val isViewed: Boolean,
)
