package com.mrsep.musicrecognizer.core.metadata.tracklink.jiosaavn

import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkError
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.tracklink.RemoteTrackLinks
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackCandidate
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksSource
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackMatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jsoup.parser.Parser
import java.io.IOException
import javax.inject.Inject

class JioSaavnTrackLinksFetcher @Inject constructor(
    private val httpClientLazy: dagger.Lazy<HttpClient>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val json: Json,
) : TrackLinksFetcher {

    override val source = TrackLinksSource.JioSaavn
    override val supportedServices: Set<MusicService> = setOf(MusicService.JioSaavn)

    override suspend fun fetch(
        track: Track
    ): NetworkResult<RemoteTrackLinks> = withContext(ioDispatcher) {
        if (MusicService.JioSaavn in track.trackLinks) {
            return@withContext NetworkResult.Success(RemoteTrackLinks())
        }

        val query = buildSearchQuery(track).ifBlank {
            return@withContext NetworkResult.Success(RemoteTrackLinks())
        }

        val httpClient = httpClientLazy.get()
        val response = try {
            httpClient.get("https://www.jiosaavn.com/api.php") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                parameter("__call", "search.getResults")
                parameter("_format", "json")
                parameter("_marker", "0")
                parameter("p", "1")
                parameter("n", SEARCH_LIMIT)
                parameter("q", query)
            }
        } catch (e: IOException) {
            return@withContext NetworkError.BadConnection(e.message)
        } catch (e: Exception) {
            ensureActive()
            return@withContext NetworkError.UnhandledError(message = e.message.orEmpty(), cause = e)
        }

        if (!response.status.isSuccess()) {
            return@withContext NetworkError.HttpError(
                code = response.status.value,
                message = response.status.description
            )
        }

        val parsed = try {
            json.decodeFromString<JioSaavnSearchResponseJson>(response.bodyAsText())
        } catch (e: Exception) {
            ensureActive()
            return@withContext NetworkError.UnhandledError(message = e.message.orEmpty(), cause = e)
        }

        val best = TrackMatcher(track)
            .firstDesiredOrBestAcceptableOf(
                candidates = parsed.asCandidateSequence(track).take(SEARCH_LIMIT),
                minAcceptScore = MIN_ACCEPTED_TRACK_SCORE,
                desiredAcceptScore = DESIRED_ACCEPT_TRACK_SCORE,
            )
            ?: return@withContext NetworkResult.Success(RemoteTrackLinks())

        NetworkResult.Success(RemoteTrackLinks(
            artworkThumbUrl = null,
            artworkUrl = null,
            trackLinks = mapOf(MusicService.JioSaavn to best.candidate.trackUrl),
        ))
    }

    private fun buildSearchQuery(track: Track): String =
        "${track.title.trim()} ${track.artist.trim()}"
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun JioSaavnSearchResponseJson.asCandidateSequence(track: Track) = sequence {
        results.orEmpty().forEach { result ->
            result?.toCandidate(track)?.let { yield(it) }
        }
    }

    private fun JioSaavnSearchResponseJson.Result.toCandidate(track: Track): TrackCandidate? {
        val titleValue = song
            ?.let { Parser.unescapeEntities(it, false) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val trackUrl = permaUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val albumValue = album
            ?.let { Parser.unescapeEntities(it, false) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return TrackCandidate(
            title = titleValue,
            artist = artistForCandidate(track, primaryArtists),
            album = albumValue,
            duration = null,
            artworkThumbUrl = null,
            artworkUrl = null,
            service = MusicService.JioSaavn,
            trackUrl = trackUrl,
        )
    }

    private fun artistForCandidate(track: Track, primaryArtists: String?): String? {
        val raw = primaryArtists
            ?.let { Parser.unescapeEntities(it, false) }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val exact = raw.split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.equals(track.artist.trim(), ignoreCase = true) }
        return exact ?: raw
    }

    private companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val SEARCH_LIMIT = 10

        private const val DESIRED_ACCEPT_TRACK_SCORE = 0.95
        private const val MIN_ACCEPTED_TRACK_SCORE = 0.75
    }
}
