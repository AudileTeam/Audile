package com.mrsep.musicrecognizer.core.metadata.tracklink.odesli

import com.mrsep.musicrecognizer.core.common.LocaleProvider
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkError
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.MusicServiceUtils
import com.mrsep.musicrecognizer.core.metadata.tracklink.RemoteTrackLinks
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import javax.inject.Inject

class OdesliTrackLinksFetcher @Inject constructor(
    private val httpClientLazy: dagger.Lazy<HttpClient>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val localeProvider: LocaleProvider,
    private val json: Json,
) : TrackLinksFetcher {

    override val source = TrackLinksSource.Odesli

    override val supportedServices = setOf(
        MusicService.AmazonMusic,
        MusicService.Anghami,
        MusicService.AppleMusic,
        MusicService.Audiomack,
        MusicService.Audius,
        MusicService.Boomplay,
        MusicService.Deezer,
        MusicService.Napster,
        MusicService.Pandora,
        MusicService.Soundcloud,
        MusicService.Spotify,
        MusicService.Tidal,
        MusicService.YandexMusic,
        MusicService.Youtube,
        MusicService.YoutubeMusic,
    )

    override suspend fun fetch(track: Track): NetworkResult<RemoteTrackLinks> {
        val (service, queryUrl) = getPriorityLinkForQuery(track.trackLinks)
            ?: return NetworkResult.Success(RemoteTrackLinks())
        val hasAllLinks = supportedServices.all(track.trackLinks::contains)
        if (hasAllLinks) return NetworkResult.Success(RemoteTrackLinks())

        return if (API_KEY.isNotBlank()) {
            fetchFromApi(track, queryUrl)
        } else {
            fetchFromHtml(track, service, queryUrl)
        }
    }

    private suspend fun fetchFromApi(track: Track, queryUrl: String): NetworkResult<RemoteTrackLinks> {
        return withContext(ioDispatcher) {
            val httpClient = httpClientLazy.get()
            val response = try {
                httpClient.get("https://api.odesli.co/v1-alpha.1/links") {
                    parameter("key", API_KEY)
                    parameter("url", queryUrl)
                    // Link count and IDs depend on requested country
                    parameter("userCountry", localeProvider.get().country.ifEmpty { "us" })
                    parameter("songIfSingle", "true")
                }
            } catch (e: IOException) {
                return@withContext NetworkError.BadConnection(e.message)
            }
            try {
                if (response.status.isSuccess()) {
                    val successDto: OdesliResponseJson = response.body()
                    val trackLinks = successDto.toTrackLinks()
                    val artworkUrl = track.artworkUrl ?: successDto.toArtworkUrl()
                    NetworkResult.Success(
                        RemoteTrackLinks(
                            artworkThumbUrl = null,
                            artworkUrl = artworkUrl,
                            trackLinks = trackLinks
                        )
                    )
                } else {
                    val errorDto: OdesliErrorResponseJson = response.body()
                    NetworkError.HttpError(
                        code = errorDto.code ?: response.status.value,
                        message = errorDto.message ?: response.status.description
                    )
                }
            } catch (e: Exception) {
                ensureActive()
                NetworkError.UnhandledError(
                    message = e.message ?: "",
                    cause = e
                )
            }
        }
    }

    private suspend fun fetchFromHtml(
        track: Track,
        service: MusicService,
        queryUrl: String,
    ): NetworkResult<RemoteTrackLinks> {
        return withContext(ioDispatcher) {
            val httpClient = httpClientLazy.get()
            val response = try {
                // Link count and IDs depend on country from requester's IP
                httpClient.get(odesliPageUrl(service, queryUrl)) {
                    header(HttpHeaders.UserAgent, USER_AGENT_WEB)
                    header(HttpHeaders.Accept, ACCEPT_HTML)
                    header(HttpHeaders.AcceptLanguage, ACCEPT_LANGUAGE)
                }
            } catch (e: IOException) {
                return@withContext NetworkError.BadConnection(e.message)
            }
            try {
                if (response.status.isSuccess()) {
                    val html = response.bodyAsText()
                    val document = Jsoup.parse(html)

                    val nextData = document.parseNextData()
                    val trackLinks = nextData?.toTrackLinks() ?: document.toTrackLinks()
                    val artworkUrl = track.artworkUrl ?: nextData?.toArtworkLink() ?: document.toArtworkLink()

                    NetworkResult.Success(
                        RemoteTrackLinks(
                            artworkThumbUrl = null,
                            artworkUrl = artworkUrl,
                            trackLinks = trackLinks
                        )
                    )
                } else {
                    NetworkError.HttpError(
                        code = response.status.value,
                        message = response.status.description
                    )
                }
            } catch (e: Exception) {
                ensureActive()
                NetworkError.UnhandledError(
                    message = e.message ?: "",
                    cause = e
                )
            }
        }
    }

    private fun odesliPageUrl(service: MusicService, sourceUrl: String): String {
        val id = MusicServiceUtils.parseTrackId(sourceUrl, service)
        val prefix = odesliShortUrlPrefix(service)
        return if (id != null && prefix != null) {
            "https://song.link/$prefix/$id"
        } else {
            "https://song.link/${sourceUrl.encodeURLPathPart()}"
        }
    }

    private fun odesliShortUrlPrefix(service: MusicService): String? = when (service) {
        MusicService.AmazonMusic -> "a"
        MusicService.Anghami -> "an"
        MusicService.AppleMusic -> "i"
        MusicService.Audiomack -> "am"
        MusicService.Audius -> "au"
        MusicService.Boomplay -> "bp"
        MusicService.Deezer -> "d"
        MusicService.Napster -> "n"
        MusicService.Pandora -> "p"
        MusicService.Soundcloud -> "sc"
        MusicService.Spotify -> "s"
        MusicService.Tidal -> "t"
        MusicService.YandexMusic -> "ya"
        MusicService.Youtube, MusicService.YoutubeMusic -> "y"
        MusicService.MusicBrainz, MusicService.Qobuz -> null
    }

    // ========== JSON Parsing ==========

    private fun Document.parseNextData(): OdesliNextDataJson? {
        val script = selectFirst("script#__NEXT_DATA__[type=application/json]")
            ?: return null
        val payload = script.data().takeIf { it.isNotBlank() }
            ?: return null
        return try {
            check(json.configuration.ignoreUnknownKeys)
            json.decodeFromString<OdesliNextDataJson>(payload)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun OdesliNextDataJson.toTrackLinks(): Map<MusicService, String> {
        val result = mutableMapOf<MusicService, String>()
        val sections = props?.pageProps?.pageData?.sections.orEmpty()
        sections.forEach { section ->
            section.links.orEmpty().forEach { link ->
//                if (link.show == false) return@forEach
                if (link.platform == null) return@forEach
                val url = link.url?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                val service = musicServiceFromJson(link.platform) ?: return@forEach
                if (!result.containsKey(service)) {
                    result[service] = url
                }
            }
        }
        return result
    }

    private fun OdesliNextDataJson.toArtworkLink(): String? {
        val entity = props?.pageProps?.pageData?.entityData ?: return null
        val url = entity.thumbnailUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val width = entity.thumbnailWidth
        val height = entity.thumbnailHeight
        return if (width != null && height != null && width >= 500 && height >= 500) {
            url
        } else {
            null
        }
    }

    private fun musicServiceFromJson(platform: String): MusicService? {
        return when (platform.lowercase()) {
            "amazonmusic" -> MusicService.AmazonMusic
            "anghami" -> MusicService.Anghami
            "applemusic" -> MusicService.AppleMusic
            "audiomack" -> MusicService.Audiomack
            "audius" -> MusicService.Audius
            "boomplay" -> MusicService.Boomplay
            "deezer" -> MusicService.Deezer
            "napster" -> MusicService.Napster
            "pandora" -> MusicService.Pandora
            "soundcloud" -> MusicService.Soundcloud
            "spotify" -> MusicService.Spotify
            "tidal" -> MusicService.Tidal
            "yandex" -> MusicService.YandexMusic
            "youtube" -> MusicService.Youtube
            "youtubemusic" -> MusicService.YoutubeMusic
            else -> null
        }
    }

    // ========== HTML Parsing ==========

    private fun Document.toTrackLinks(): Map<MusicService, String> {
        val result = mutableMapOf<MusicService, String>()
        val linkElements = select("a[data-test-id=link]")
        for (element in linkElements) {
            val ariaLabel = element.attr("aria-label")
            if (!ariaLabel.contains("Listen", ignoreCase = true)) {
                continue
            }
            val url = element.absUrl("href").ifBlank { element.attr("href") }
                .trim()
                .takeIf { it.isNotBlank() }
                ?: continue
            val service = musicServiceFromAriaLabel(ariaLabel) ?: continue
            if (!result.containsKey(service)) {
                result[service] = url
            }
        }

        return result
    }

    private fun musicServiceFromAriaLabel(label: String): MusicService? {
        return when {
            label.contains("apple music", ignoreCase = true) -> MusicService.AppleMusic
            label.contains("spotify", ignoreCase = true) -> MusicService.Spotify
            label.contains("amazon music", ignoreCase = true) -> MusicService.AmazonMusic
            // Parse YouTube Music before YouTube
            label.contains("youtube music", ignoreCase = true) -> MusicService.YoutubeMusic
            label.contains("youtube", ignoreCase = true) -> MusicService.Youtube
            label.contains("deezer", ignoreCase = true) -> MusicService.Deezer
            label.contains("soundcloud", ignoreCase = true) -> MusicService.Soundcloud
            label.contains("yandex", ignoreCase = true) -> MusicService.YandexMusic
            label.contains("napster", ignoreCase = true) -> MusicService.Napster
            label.contains("tidal", ignoreCase = true) -> MusicService.Tidal
            label.contains("pandora", ignoreCase = true) -> MusicService.Pandora
            label.contains("audiomack", ignoreCase = true) -> MusicService.Audiomack
            label.contains("audius", ignoreCase = true) -> MusicService.Audius
            label.contains("boomplay", ignoreCase = true) -> MusicService.Boomplay
            label.contains("anghami", ignoreCase = true) -> MusicService.Anghami
            else -> null
        }
    }

    private fun Document.toArtworkLink(): String? {
        return selectFirst("meta[property=og:image]")
            ?.attr("content")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun getPriorityLinkForQuery(links: Map<MusicService, String>): Pair<MusicService, String>? {
        fun pick(service: MusicService) = links[service]?.let { service to it }
        return pick(MusicService.AppleMusic)
            ?: pick(MusicService.Spotify)
            ?: pick(MusicService.AmazonMusic)
            ?: pick(MusicService.YoutubeMusic)
            ?: pick(MusicService.Tidal)
            ?: pick(MusicService.Youtube)
            ?: pick(MusicService.Deezer) // It doesn't work reliably with Deezer for some reason
            ?: pick(MusicService.Soundcloud)
            ?: pick(MusicService.YandexMusic)
            ?: pick(MusicService.Pandora)
            ?: pick(MusicService.MusicBrainz)
            ?: pick(MusicService.Audiomack)
            ?: pick(MusicService.Audius)
            ?: pick(MusicService.Boomplay)
            ?: pick(MusicService.Anghami)
            ?: pick(MusicService.Napster)
    }

    companion object {
        private const val API_KEY = ""

        private const val USER_AGENT_WEB = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.5"
    }
}
