package com.mrsep.musicrecognizer.core.metadata.tracklink

import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track

interface TrackLinksFetcher {
    val source: TrackLinksSource
    val supportedServices: Set<MusicService>
    suspend fun fetch(track: Track): NetworkResult<RemoteTrackLinks>
}

data class RemoteTrackLinks(
    val artworkThumbUrl: String? = null,
    val artworkUrl: String? = null,
    val trackLinks: Map<MusicService, String> = emptyMap(),
)

enum class TrackLinksSource {
    Odesli,
    YouTube,
    Qobuz,
    JioSaavn,
    MusicBrainz,
    Spotify,
}