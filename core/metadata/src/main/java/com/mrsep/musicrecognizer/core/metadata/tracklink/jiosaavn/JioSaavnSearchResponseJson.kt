package com.mrsep.musicrecognizer.core.metadata.tracklink.jiosaavn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JioSaavnSearchResponseJson(
    @SerialName("results")
    val results: List<Result?>? = null,
) {
    @Serializable
    internal data class Result(
        @SerialName("song")
        val song: String? = null,
        @SerialName("album")
        val album: String? = null,
        @SerialName("primary_artists")
        val primaryArtists: String? = null,
        @SerialName("perma_url")
        val permaUrl: String? = null,
    )
}
