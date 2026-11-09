package com.mrsep.musicrecognizer.core.metadata

import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import io.ktor.http.Url
import io.ktor.http.parseUrl
import kotlin.uuid.Uuid

object MusicServiceUtils {

    fun parseTrackId(url: String, service: MusicService): String? {
        val parsed = parseUrl(url.trim()) ?: return null
        return when (service) {
            MusicService.Spotify -> parsed.segmentAfter("track", ::isSpotifyTrackId)
            MusicService.AppleMusic -> parseAppleMusic(parsed)
            MusicService.Youtube, MusicService.YoutubeMusic -> parseYoutube(parsed)
            MusicService.Deezer -> parsed.segmentAfter("track", ::isDigits)
            MusicService.JioSaavn -> parseJioSaavn(parsed)
            MusicService.Tidal -> parsed.segmentAfter("track", ::isDigits)
            MusicService.YandexMusic -> parsed.segmentAfter("track", ::isDigits)
            MusicService.Napster -> parsed.segmentAfter("track", ::isNapsterTrackId)
            MusicService.Pandora -> parsePandora(parsed)
            MusicService.AmazonMusic -> parseAmazon(parsed)
            MusicService.Boomplay -> parsed.segmentAfter("songs", ::isDigits)
            MusicService.Anghami -> parsed.segmentAfter("song", ::isDigits)
            MusicService.Audius -> parseAudius(parsed)
            MusicService.Soundcloud -> parsed.segmentAfter("tracks", ::isDigits)
            MusicService.Audiomack -> parseAudiomack(parsed)
            MusicService.MusicBrainz -> parsed.segmentAfter("recording", ::isMbid)
            MusicService.Qobuz -> parsed.segmentAfter("track", ::isDigits)
        }
    }

    private fun parseAppleMusic(url: Url): String? {
        url.queryValue("i")?.takeIf(::isDigits)?.let { return it }
        val songIndex = url.segments.indexOfLast { it.equals("song", ignoreCase = true) }
        if (songIndex < 0) return null
        return url.segments.drop(songIndex + 1).lastOrNull(::isDigits)
    }

    private fun parseYoutube(url: Url): String? {
        url.queryValue("v")?.takeIf(::isYoutubeVideoId)?.let { return it }
        @Suppress("SpellCheckingInspection")
        val isYoutuBeHost = url.host.equals("youtu.be", ignoreCase = true) ||
                url.host.endsWith(".youtu.be", ignoreCase = true)
        if (isYoutuBeHost) {
            url.segments.firstOrNull()?.takeIf(::isYoutubeVideoId)?.let { return it }
        }
        for (key in listOf("shorts", "embed", "live", "v")) {
            url.segmentAfter(key, ::isYoutubeVideoId)?.let { return it }
        }
        return null
    }

    private fun parsePandora(url: Url): String? =
        url.segments.firstNotNullOfOrNull { segment -> pandoraIdRegex.find(segment)?.value }

    private fun parseAmazon(url: Url): String? {
        url.queryValue("trackAsin")?.takeIf(::isAsin)?.let { return it }
        url.segmentAfter("songs", ::isAsin)?.let { return it }
        url.segmentAfter("tracks", ::isAsin)?.let { return it }
        url.segmentAfter("dp", ::isAsin)?.let { return it }
        val segments = url.segments
        val gpIndex = segments.indexOfFirst { it.equals("gp", ignoreCase = true) }
        if (
            gpIndex >= 0 &&
            gpIndex + 2 < segments.size &&
            segments[gpIndex + 1].equals("product", ignoreCase = true)
        ) {
            return segments[gpIndex + 2].takeIf(::isAsin)
        }
        return null
    }

    private fun parseAudius(url: Url): String? {
        url.segmentAfter("tracks", ::isAudiusId)?.let { return it }
        url.segmentAfter("t", ::isAudiusId)?.let { return it }
        return null
    }

    private fun parseAudiomack(url: Url): String? {
        url.segmentAfter("songs", ::isDigits)?.let { return it }
        val segments = url.segments
        val songIndex = segments.indexOfLast { it.equals("song", ignoreCase = true) }
        if (songIndex >= 0 && songIndex + 1 < segments.size) {
            val candidate = segments[songIndex + 1]
            val hasSlugTail = songIndex + 2 < segments.size
            if (isDigits(candidate) && !hasSlugTail) return candidate
        }
        return url.queryValue("id")?.takeIf(::isDigits)
    }

    private fun parseJioSaavn(url: Url): String? {
        val hostValid = url.host.equals("jiosaavn.com", ignoreCase = true) ||
                url.host.endsWith(".jiosaavn.com", ignoreCase = true)
        if (!hostValid) return null
        val segments = url.segments
        if (segments.none { it.equals("song", ignoreCase = true) }) return null
        if (segments.any { it.equals("search", ignoreCase = true) || it.equals("album", ignoreCase = true) }) {
            return null
        }
        return segments.lastOrNull()?.takeIf(jioSaavnTrackIdRegex::matches)
    }

    private fun Url.segmentAfter(key: String, predicate: (String) -> Boolean): String? {
        val index = segments.indexOfLast { it.equals(key, ignoreCase = true) }
        if (index < 0 || index + 1 >= segments.size) return null
        return segments[index + 1].takeIf(predicate)
    }

    private fun Url.queryValue(key: String): String? =
        parameters.entries()
            .firstOrNull { (name, _) -> name.equals(key, ignoreCase = true) }
            ?.value
            ?.firstOrNull { it.isNotEmpty() }

    private fun isDigits(value: String): Boolean =
        value.isNotEmpty() && value.all { it in '0'..'9' }

    private fun isSpotifyTrackId(value: String): Boolean =
        value.length == 22 && value.all { it.isBase62() }

    private fun isYoutubeVideoId(value: String): Boolean =
        value.length == 11 && value.all { it.isBase62() || it == '-' || it == '_' }

    private fun isNapsterTrackId(value: String): Boolean {
        if (!value.startsWith("tra.", ignoreCase = true)) return false
        val rest = value.substring(4)
        return rest.isNotEmpty() && rest.all { it in '0'..'9' }
    }

    private fun isAsin(value: String): Boolean =
        value.length == 10 && value.all { it.isBase62() }

    private fun isAudiusId(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isBase62() }

    private fun isMbid(value: String): Boolean = Uuid.parseOrNull(value) != null

    private fun Char.isBase62(): Boolean =
        this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'

    private val pandoraIdRegex = Regex("""TR:\d+""", RegexOption.IGNORE_CASE)
    private val jioSaavnTrackIdRegex = Regex("""[^/?#]+""")
}
