package com.mrsep.musicrecognizer.feature.library.presentation.model

import com.mrsep.musicrecognizer.core.common.util.AppDateTimeFormatter
import com.mrsep.musicrecognizer.core.domain.track.model.TrackPreview
import java.time.ZoneId
import javax.annotation.concurrent.Immutable

@Immutable
internal data class TrackUi(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkThumbUrl: String?,
    val recognitionDate: String,
    val isViewed: Boolean,
)

internal fun TrackPreview.toUi(dateTimeFormatter: AppDateTimeFormatter) = TrackUi(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artworkThumbUrl = artworkThumbUrl ?: artworkUrl,
    recognitionDate = dateTimeFormatter.formatRelativeToToday(
        recognitionDate.atZone(ZoneId.systemDefault())
    ),
    isViewed = isViewed,
)
