package com.mrsep.musicrecognizer.core.metadata.tracklink.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyServerTimeJson(
    @SerialName("serverTime")
    val serverTime: Long? = null,
)

@Serializable
internal data class SpotifyTokenResponseJson(
    @SerialName("accessToken")
    val accessToken: String? = null,
    @SerialName("accessTokenExpirationTimestampMs")
    val accessTokenExpirationTimestampMs: Long? = null,
    @SerialName("totpVerExpired")
    val totpVerExpired: String? = null,
)

@Serializable
internal data class SpotifySearchResponseJson(
    @SerialName("data")
    val data: DataJson? = null,
    @SerialName("errors")
    val errors: List<ErrorJson?>? = null,
) {
    @Serializable
    internal data class DataJson(
        @SerialName("searchV2")
        val searchV2: SearchJson? = null,
        @SerialName("search")
        val search: SearchJson? = null,
    )

    @Serializable
    internal data class ErrorJson(
        @SerialName("message")
        val message: String? = null,
    )

    @Serializable
    internal data class SearchJson(
        @SerialName("tracks")
        val tracks: TracksJson? = null,
    )

    @Serializable
    internal data class TracksJson(
        @SerialName("items")
        val items: List<TrackItemJson?>? = null,
    )

    @Serializable
    internal data class TrackItemJson(
        @SerialName("track")
        val track: TrackJson? = null,
    )

    @Serializable
    internal data class TrackJson(
        @SerialName("id")
        val id: String? = null,
        @SerialName("uri")
        val uri: String? = null,
        @SerialName("name")
        val name: String? = null,
        @SerialName("artists")
        val artists: ArtistsJson? = null,
        @SerialName("playability")
        val playability: PlayabilityJson? = null,
    )

    @Serializable
    internal data class ArtistsJson(
        @SerialName("items")
        val items: List<ArtistItemJson?>? = null,
    )

    @Serializable
    internal data class ArtistItemJson(
        @SerialName("profile")
        val profile: ProfileJson? = null,
    )

    @Serializable
    internal data class ProfileJson(
        @SerialName("name")
        val name: String? = null,
    )

    @Serializable
    internal data class PlayabilityJson(
        @SerialName("playable")
        val playable: Boolean? = null,
    )
}
