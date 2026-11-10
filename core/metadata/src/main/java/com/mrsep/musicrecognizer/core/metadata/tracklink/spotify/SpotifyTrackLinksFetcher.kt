package com.mrsep.musicrecognizer.core.metadata.tracklink.spotify

import android.content.Context
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkError
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.metadata.tracklink.RemoteTrackLinks
import com.mrsep.musicrecognizer.core.metadata.tracklink.ScoredTrackCandidate
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackCandidate
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksFetcher
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackLinksSource
import com.mrsep.musicrecognizer.core.metadata.tracklink.TrackMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Anonymous web-player session, same approach as https://github.com/sjdonado/idonthavespotify/
// The Web API rejects these tokens, so search goes through the partner GraphQL API.
// `isrc:` is an exact catalog filter, a title/artist query is a fallback.
// Catalog and result order depend on the caller IP.
@Singleton
class SpotifyTrackLinksFetcher @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val httpClientLazy: dagger.Lazy<HttpClient>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TrackLinksFetcher {

    private val sessionMutex = Mutex()
    private var cachedSecret: TotpSecret? = null
    private var cachedToken: CachedToken? = null

    // Bad player bundle. Skip secret reloads for SCRIPT_PARSE_ERROR_TTL.
    private var scriptParseError: NetworkError.UnhandledError? = null
    private var scriptParseErrorAt: Instant? = null

    override val source = TrackLinksSource.Spotify
    override val supportedServices: Set<MusicService> = setOf(MusicService.Spotify)

    override suspend fun fetch(
        track: Track
    ): NetworkResult<RemoteTrackLinks> = withContext(ioDispatcher) {
        if (MusicService.Spotify in track.trackLinks) {
            return@withContext NetworkResult.Success(RemoteTrackLinks())
        }

        val queries = buildSearchQueries(track).ifEmpty {
            return@withContext NetworkResult.Success(RemoteTrackLinks())
        }

        val tokenResult = accessToken()
        if (tokenResult is NetworkError) return@withContext tokenResult

        var lastError: NetworkError? = null
        val trackMatcher = TrackMatcher(track, enableCache = true)
        var best: ScoredTrackCandidate? = null

        for (query in queries) {
            when (val result = search(query)) {
                is NetworkResult.Success -> {
                    val current = trackMatcher.firstDesiredOrBestAcceptableOf(
                        candidates = result.data.asCandidateSequence().take(SEARCH_LIMIT),
                        minAcceptScore = MIN_ACCEPTED_TRACK_SCORE,
                        desiredAcceptScore = DESIRED_ACCEPT_TRACK_SCORE,
                    ) ?: continue

                    val previous = best
                    val selected = if (previous == null || current.score > previous.score) {
                        current
                    } else {
                        previous
                    }
                    best = selected
                    // Don't spend another request.
                    if (query.startsWith(ISRC_QUERY_PREFIX) || selected.score >= DESIRED_ACCEPT_TRACK_SCORE) {
                        break
                    }
                }

                is NetworkError -> {
                    lastError = result
                    // The hash is shared, so the other query cannot succeed either.
                    if (result is NetworkError.UnhandledError &&
                        result.message == "PersistedQueryNotFound"
                    ) {
                        break
                    }
                }
            }
        }

        if (lastError != null && best == null) {
            lastError
        } else {
            NetworkResult.Success(
                RemoteTrackLinks(
                    trackLinks = buildMap {
                        best?.candidate?.let { put(it.service, it.trackUrl) }
                    }
                )
            )
        }
    }

    private suspend fun search(query: String): NetworkResult<SpotifySearchResponseJson> {
        val token = when (val result = accessToken()) {
            is NetworkResult.Success -> result.data
            is NetworkError -> return result
        }
        val first = executeSearch(query, token)
        if (first !is NetworkError.HttpError || first.code != 401) return first

        sessionMutex.withLock { cachedToken = null }
        val refreshed = when (val result = accessToken()) {
            is NetworkResult.Success -> result.data
            is NetworkError -> return result
        }
        return executeSearch(query, refreshed)
    }

    private suspend fun executeSearch(
        query: String,
        accessToken: String,
    ): NetworkResult<SpotifySearchResponseJson> {
        val variables = buildJsonObject {
            put("searchTerm", query)
            put("offset", 0)
            put("limit", SEARCH_LIMIT)
        }.toString()

        return get(SEARCH_URL) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            header(APP_PLATFORM_HEADER, APP_PLATFORM_WEB_PLAYER)
            parameter("operationName", SEARCH_OPERATION)
            parameter("variables", variables)
            parameter("extensions", SEARCH_EXTENSIONS)
        }.mapBody { response ->
            val body = response.body<SpotifySearchResponseJson>()
            if (body.resolvedSearch() == null) {
                val message = body.errors.orEmpty()
                    .firstNotNullOfOrNull { it?.message?.trim()?.takeIf(String::isNotBlank) }
                if (message != null) {
                    NetworkError.UnhandledError(message)
                } else {
                    NetworkResult.Success(body)
                }
            } else {
                NetworkResult.Success(body)
            }
        }
    }

    private suspend fun accessToken(): NetworkResult<String> = sessionMutex.withLock {
        val parseErrorAt = scriptParseErrorAt
        if (parseErrorAt != null && Clock.System.now() - parseErrorAt < SCRIPT_PARSE_ERROR_TTL) {
            scriptParseError?.let { return@withLock it }
        }

        val now = Clock.System.now()
        cachedToken?.takeIf { it.expiration - TOKEN_REFRESH_MARGIN > now }?.let {
            return@withLock NetworkResult.Success(it.value)
        }

        // A secret from disk counts as cached: rejection must refresh it, not fail closed.
        if (cachedSecret == null) {
            cachedSecret = readStoredSecret()
        }
        val hadCachedSecret = cachedSecret != null
        val secret = cachedSecret ?: when (val loaded = loadSecret()) {
            is NetworkResult.Success -> loaded.data.also { cachedSecret = it }
            is NetworkError -> return@withLock loaded
        }

        when (val issued = requestToken(secret)) {
            is NetworkResult.Success -> {
                val token = storeToken(issued.data)
                when {
                    token != null -> {
                        if (!hadCachedSecret) {
                            writeStoredSecret(secret)
                        }
                        NetworkResult.Success(token)
                    }
                    hadCachedSecret -> {
                        clearStoredSecret()
                        refreshSecretAndToken()
                    }
                    else -> NetworkError.UnhandledError("Spotify token rejected")
                }
            }

            is NetworkError.HttpError -> {
                if (hadCachedSecret && (issued.code == 400 || issued.code == 401)) {
                    clearStoredSecret()
                    refreshSecretAndToken()
                } else {
                    issued
                }
            }

            is NetworkError -> issued
        }
    }

    // Called only while sessionMutex is held.
    private fun storeToken(body: SpotifyTokenResponseJson): String? {
        val token = body.accessToken?.takeIf { it.isNotBlank() } ?: return null
        val expiration = body.accessTokenExpirationTimestampMs ?: return null
        // "warning" still returns a usable token. Only "error" means this secret is dead.
        if (body.totpVerExpired == "error") return null
        cachedToken = CachedToken(token, Instant.fromEpochMilliseconds(expiration))
        return token
    }

    // Called only while sessionMutex is held.
    private suspend fun refreshSecretAndToken(): NetworkResult<String> {
        val secret = when (val loaded = loadSecret()) {
            is NetworkResult.Success -> loaded.data.also { cachedSecret = it }
            is NetworkError -> return loaded
        }
        return when (val issued = requestToken(secret)) {
            is NetworkResult.Success -> {
                val token = storeToken(issued.data)
                if (token != null) {
                    writeStoredSecret(secret)
                    NetworkResult.Success(token)
                } else {
                    NetworkError.UnhandledError("Spotify token rejected")
                }
            }
            is NetworkError -> issued
        }
    }

    // Called only while sessionMutex is held.
    private suspend fun clearStoredSecret() {
        cachedSecret = null
        cachedToken = null
        withContext(ioDispatcher) {
            secretFile().delete()
            secretTempFile().delete()
        }
    }

    // Called only while sessionMutex is held.
    // Layout: "<version>\n<secret>". The secret is stored verbatim.
    private suspend fun readStoredSecret(): TotpSecret? = withContext(ioDispatcher) {
        val file = secretFile()
        if (!file.isFile) return@withContext null
        try {
            val text = file.readText(Charsets.UTF_8)
            val lineBreak = text.indexOf('\n')
            val version = if (lineBreak > 0) text.substring(0, lineBreak).toIntOrNull() else null
            val value = if (lineBreak >= 0) text.substring(lineBreak + 1) else ""
            if (version == null || version <= 0 || value.isEmpty()) {
                file.delete()
                null
            } else {
                TotpSecret(value, version)
            }
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    // Called only while sessionMutex is held.
    private suspend fun writeStoredSecret(secret: TotpSecret) = withContext(ioDispatcher) {
        val file = secretFile()
        val temporary = secretTempFile()
        try {
            temporary.writeText(secret.version.toString() + "\n" + secret.value, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        } catch (_: IOException) {
            temporary.delete()
        }
    }

    private fun secretFile(): File =
        File(appContext.noBackupFilesDir, SECRET_FILE_NAME)

    private fun secretTempFile(): File =
        File(appContext.noBackupFilesDir, "$SECRET_FILE_NAME.tmp")

    private suspend fun requestToken(secret: TotpSecret): NetworkResult<SpotifyTokenResponseJson> {
        val serverTime = when (val result = get("$ORIGIN/api/server-time") {
            header(HttpHeaders.Accept, "application/json")
        }.mapBody { NetworkResult.Success(it.body<SpotifyServerTimeJson>()) }) {
            is NetworkResult.Success -> result.data.serverTime
            is NetworkError -> return result
        } ?: return NetworkError.UnhandledError("Spotify server time missing")

        val code = totp(serverTime * 1000, secret.value)
        return get("$ORIGIN/api/token") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.Referrer, "$ORIGIN/")
            header(HttpHeaders.Origin, ORIGIN)
            parameter("reason", "init")
            parameter("productType", "web-player")
            parameter("totp", code)
            parameter("totpServer", code)
            parameter("totpVer", secret.version.toString())
        }.mapBody { NetworkResult.Success(it.body<SpotifyTokenResponseJson>()) }
    }

    // Called only while sessionMutex is held.
    private fun rememberScriptParseError(message: String): NetworkError.UnhandledError {
        scriptParseErrorAt = Clock.System.now()
        return NetworkError.UnhandledError(message).also { scriptParseError = it }
    }

    private suspend fun loadSecret(): NetworkResult<TotpSecret> {
        val homepage = when (val result = getText(ORIGIN)) {
            is NetworkResult.Success -> result.data
            is NetworkError -> return result
        }
        val scriptUrl = WEB_PLAYER_JS_REGEX.find(homepage)?.groupValues?.getOrNull(1)
            ?: return rememberScriptParseError("Spotify web player script not found")

        val script = when (val result = getText(scriptUrl)) {
            is NetworkResult.Success -> result.data
            is NetworkError -> return result
        }
        // The player uses the first entry of this list, not the highest version.
        val match = SECRET_ENTRY_REGEX.find(script)
            ?: return rememberScriptParseError("Spotify TOTP secret not found")
        val rawSecret = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val version = match.groupValues[3].toIntOrNull()
            ?: return rememberScriptParseError("Spotify TOTP version not found")
        if (rawSecret.isEmpty()) {
            return rememberScriptParseError("Spotify TOTP secret not found")
        }
        return NetworkResult.Success(TotpSecret(unescapeJsString(rawSecret), version))
    }

    private suspend fun getText(url: String): NetworkResult<String> {
        return get(url).mapBody { NetworkResult.Success(it.bodyAsText()) }
    }

    private suspend fun get(
        url: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<HttpResponse> {
        val response = try {
            val httpClient = httpClientLazy.get()
            httpClient.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                configure()
            }
        } catch (e: IOException) {
            return NetworkError.BadConnection(e.message)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            return NetworkError.UnhandledError(message = e.message.orEmpty(), cause = e)
        }
        if (!response.status.isSuccess()) {
            return NetworkError.HttpError(
                code = response.status.value,
                message = response.status.description,
            )
        }
        return NetworkResult.Success(response)
    }

    private suspend inline fun <T> NetworkResult<HttpResponse>.mapBody(
        parse: suspend (HttpResponse) -> NetworkResult<T>,
    ): NetworkResult<T> {
        return when (this) {
            is NetworkResult.Success -> try {
                parse(data)
            } catch (e: IOException) {
                NetworkError.BadConnection(e.message)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                NetworkError.UnhandledError(message = e.message.orEmpty(), cause = e)
            }
            is NetworkError -> this
        }
    }

    private fun buildSearchQueries(track: Track): List<String> {
        val isrcQuery = track.isrc?.normalizedIsrcOrNull()?.let { isrc -> "$ISRC_QUERY_PREFIX$isrc" }
        val metadataQuery = listOf(track.title, track.artist)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .takeIf { it.isNotBlank() }
        return listOfNotNull(isrcQuery, metadataQuery).distinct()
    }

    private fun String.normalizedIsrcOrNull() =
        trim()
            .takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            ?.replace(Regex("[^A-Z0-9]"), "")
            ?.takeIf { Regex("^[A-Z]{2}[A-Z0-9]{3}\\d{7}$").matches(it) }

    private fun SpotifySearchResponseJson.asCandidateSequence(): Sequence<TrackCandidate> {
        val search = resolvedSearch() ?: return emptySequence()
        val tracks = search.tracks?.items.orEmpty().mapNotNull { it?.track }
        val playable = tracks.filter { it.playability?.playable != false }
        return (playable.ifEmpty { tracks }).asSequence().mapNotNull { it.toCandidate() }
    }

    private fun SpotifySearchResponseJson.resolvedSearch(): SpotifySearchResponseJson.SearchJson? {
        return data?.searchV2 ?: data?.search
    }

    private fun SpotifySearchResponseJson.TrackJson.toCandidate(): TrackCandidate? {
        val trackUri = uri?.trim()?.takeIf { it.startsWith(SPOTIFY_TRACK_URI_PREFIX) }
            ?: return null
        val trackId = trackUri.substringAfterLast(':').takeIf(::isSpotifyTrackId) ?: return null
        val titleValue = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val artistValue = artists?.items.orEmpty()
            .mapNotNull { item -> item?.profile?.name?.trim()?.takeIf { it.isNotBlank() } }
            .distinct()
            .joinToString(separator = ", ")
            .takeIf { it.isNotBlank() }

        return TrackCandidate(
            title = titleValue,
            artist = artistValue,
            album = null,
            duration = null,
            artworkThumbUrl = null,
            artworkUrl = null,
            service = MusicService.Spotify,
            trackUrl = "$ORIGIN/track/$trackId",
        )
    }

    private fun isSpotifyTrackId(value: String): Boolean {
        return value.length == SPOTIFY_TRACK_ID_LENGTH && value.all { char ->
            char in '0'..'9' || char in 'A'..'Z' || char in 'a'..'z'
        }
    }

    private fun unescapeJsString(raw: String): String = buildString(raw.length) {
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                append(
                    when (val next = raw[index + 1]) {
                        '\\', '\'', '"' -> next
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> next
                    }
                )
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }

    // Mirrors the web player: XOR each char, join the decimals, HMAC-SHA1 over that UTF-8 text.
    private fun totp(timestampMillis: Long, secret: String): String {
        val keyBytes = secret
            .mapIndexed { index, char -> char.code xor ((index % 33) + 9) }
            .joinToString(separator = "")
            .encodeToByteArray()
        val counter = timestampMillis / 1_000 / 30
        val counterBytes = ByteArray(8)
        var value = counter
        for (index in counterBytes.lastIndex downTo 0) {
            counterBytes[index] = value.toByte()
            value = value ushr 8
        }
        val mac = Mac.getInstance(HMAC_SHA1)
        mac.init(SecretKeySpec(keyBytes, HMAC_SHA1))
        val hmac = mac.doFinal(counterBytes)
        val offset = hmac.last().toInt() and 0x0f
        val binary = ((hmac[offset].toInt() and 0x7f) shl 24) or
            ((hmac[offset + 1].toInt() and 0xff) shl 16) or
            ((hmac[offset + 2].toInt() and 0xff) shl 8) or
            (hmac[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    private data class TotpSecret(val value: String, val version: Int)

    private data class CachedToken(val value: String, val expiration: Instant)

    private companion object {
        private const val ORIGIN = "https://open.spotify.com"
        private const val SECRET_FILE_NAME = "spotify_totp_secret"
        private const val SEARCH_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
        private const val SEARCH_OPERATION = "searchDesktop"

        private const val SEARCH_HASH =
            "75bbf6bfcfdf85b8fc828417bfad92b7cd66bf7f556d85670f4da8292373ebec"
        private const val SEARCH_EXTENSIONS =
            """{"persistedQuery":{"version":1,"sha256Hash":"$SEARCH_HASH"}}"""

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val APP_PLATFORM_HEADER = "app-platform"
        private const val APP_PLATFORM_WEB_PLAYER = "WebPlayer"
        private const val HMAC_SHA1 = "HmacSHA1"

        private const val ISRC_QUERY_PREFIX = "isrc:"
        private const val SEARCH_LIMIT = 10
        private const val SPOTIFY_TRACK_URI_PREFIX = "spotify:track:"
        private const val SPOTIFY_TRACK_ID_LENGTH = 22
        private val TOKEN_REFRESH_MARGIN = 1.minutes
        private val SCRIPT_PARSE_ERROR_TTL = 12.hours

        private const val DESIRED_ACCEPT_TRACK_SCORE = 0.95
        private const val MIN_ACCEPTED_TRACK_SCORE = 0.75

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val WEB_PLAYER_JS_REGEX =
            Regex(""""(https://[^"\s]+/(?:mobile-)?web-player\.[0-9a-f]+\.js)"""")
        private val SECRET_ENTRY_REGEX =
            Regex("""\{\s*secret\s*:\s*(?:"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)')\s*,\s*version\s*:\s*(\d+)\s*\}""")
    }
}
