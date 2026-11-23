package com.mrsep.musicrecognizer.core.data.track

import com.mrsep.musicrecognizer.core.database.track.TrackPreviewTuple
import com.mrsep.musicrecognizer.core.domain.track.model.TrackPreview

internal fun TrackPreviewTuple.toDomain() = TrackPreview(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artworkThumbUrl = artworkThumbnail,
    artworkUrl = artwork,
    recognitionDate = recognitionDate,
    isViewed = isViewed
)