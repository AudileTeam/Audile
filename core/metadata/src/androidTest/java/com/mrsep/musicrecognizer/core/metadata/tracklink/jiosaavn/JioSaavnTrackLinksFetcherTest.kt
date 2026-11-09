package com.mrsep.musicrecognizer.core.metadata.tracklink.jiosaavn

import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class JioSaavnTrackLinksFetcherTest {

    private val track = Track(
        id = "id",
        title = "Tum Hi Ho",
        artist = "Arijit Singh",
        album = "Aashiqui 2",
        releaseDate = null,
        duration = null,
        recognizedAt = null,
        recognizedBy = RecognitionProvider.Shazam,
        recognitionDate = Instant.now(),
        lyrics = null,
        isrc = null,
        artworkThumbUrl = null,
        artworkUrl = null,
        trackLinks = emptyMap(),
        properties = Track.Properties(
            isFavorite = false,
            isViewed = false,
            themeSeedColor = null
        ),
    )

    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private val linkFetcher = JioSaavnTrackLinksFetcher(
        ioDispatcher = Dispatchers.IO,
        httpClientLazy = {
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(json)
                }
            }
        },
        json = json,
    )

    @Test
    fun simpleTest(): Unit = runBlocking {
        val result = linkFetcher.fetch(track)

        result.shouldBeTypeOf<NetworkResult.Success<RemoteTrackLinks>>()
        with(result.data.trackLinks) {
            shouldHaveSize(1)
            shouldContain(MusicService.JioSaavn, "https://www.jiosaavn.com/song/tum-hi-ho/EToxUyFpcwQ")
        }
    }
}
