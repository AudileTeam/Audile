package com.mrsep.musicrecognizer.feature.backup

import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track

internal sealed interface CsvField {

    fun extractFrom(track: Track): String?

    companion object {
        fun List<CsvField>.extractFrom(track: Track): List<String?> =
            map { field -> field.extractFrom(track) }
    }
}

internal enum class TrackField(
    private val extractor: Track.() -> String?
): CsvField {
    TITLE({ title }),
    ARTIST({ artist }),
    ALBUM({ album }),
    RELEASE_DATE({ releaseDate?.toString() }),
    ISRC({ isrc }),
    RECOGNITION_DATE({ recognitionDate.toString() }),
    DURATION({ duration?.toString() }),
    PLAYBACK_OFFSET({ recognizedAt?.toString() }),
    RECOGNITION_PROVIDER({ recognizedBy.toString() }),
    IS_FAVORITE({ properties.isFavorite.toString() }),
    LYRICS({ lyrics?.plain }),
    LINK_ARTWORK({ artworkUrl ?: artworkThumbUrl });

    override fun extractFrom(track: Track) = extractor(track)
}

internal data class TrackLinkField(val service: MusicService): CsvField {
    override fun extractFrom(track: Track) = track.trackLinks[service]
}
