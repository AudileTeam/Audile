package com.mrsep.musicrecognizer.core.metadata.tracklink.spotify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.tracklink.RemoteTrackLinks
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

// Catalog and result order depend on the caller IP

@RunWith(AndroidJUnit4::class)
class SpotifyTrackLinksFetcherTest {

    private val track = Track(
        id = "id",
        title = "Rolling in the deep",
        artist = "Adele",
        album = "21",
        releaseDate = null,
        duration = null,
        recognizedAt = null,
        recognizedBy = RecognitionProvider.Shazam,
        recognitionDate = Instant.now(),
        lyrics = null,
        isrc = "GBBKS1000335",
        artworkThumbUrl = null,
        artworkUrl = null,
        trackLinks = emptyMap(),
        properties = Track.Properties(
            isFavorite = false,
            isViewed = false,
            themeSeedColor = null
        ),
    )

    private val spotifyFetcher = SpotifyTrackLinksFetcher(
        appContext = InstrumentationRegistry.getInstrumentation().targetContext,
        ioDispatcher = Dispatchers.IO,
        httpClientLazy = {
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json {
                        explicitNulls = false
                        ignoreUnknownKeys = true
                    })
                }
            }
        },
    )

    @Test
    fun testWithIsrc(): Unit = runBlocking {
        val result = spotifyFetcher.fetch(track)

        result.shouldBeTypeOf<NetworkResult.Success<RemoteTrackLinks>>()
        with(result.data.trackLinks) {
            shouldHaveSize(1)
            shouldContain(MusicService.Spotify, "https://open.spotify.com/track/5ei5hxLl7yUk2WplfA42kf")
        }
    }

    @Test
    fun testWithoutIsrc(): Unit = runBlocking {
        val track = track.copy(isrc = null)
        val result = spotifyFetcher.fetch(track)

        result.shouldBeTypeOf<NetworkResult.Success<RemoteTrackLinks>>()
        with(result.data.trackLinks) {
            shouldHaveSize(1)
            shouldContain(MusicService.Spotify, "https://open.spotify.com/track/4OSBTYWVwsQhGLF9NHvIbR")
        }
    }
}
