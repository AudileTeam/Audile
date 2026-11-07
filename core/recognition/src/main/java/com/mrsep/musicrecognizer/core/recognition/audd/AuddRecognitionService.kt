package com.mrsep.musicrecognizer.core.recognition.audd

import android.util.Log
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.preferences.AuddConfig
import com.mrsep.musicrecognizer.core.domain.recognition.AudioSample
import com.mrsep.musicrecognizer.core.domain.recognition.RemoteRecognitionService
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecordingScheme
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.recognition.audd.json.AuddResponseJson
import com.mrsep.musicrecognizer.core.recognition.audd.json.toRecognitionResult
import com.mrsep.musicrecognizer.core.recognition.audd.websocket.WebSocketSession
import com.mrsep.musicrecognizer.core.recognition.audd.websocket.isRecoverableWebSocketFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AuddRecognitionService"

internal class AuddRecognitionService @AssistedInject constructor(
    @Assisted private val config: AuddConfig,
    private val webSocketSession: WebSocketSession,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val httpClientLazy: dagger.Lazy<HttpClient>,
) : RemoteRecognitionService {

    override val recordingScheme = RecordingScheme(
        steps = listOf(
            RecordingScheme.Step(recordings = listOf(4.seconds, 8.seconds)),
            RecordingScheme.Step(recordings = listOf(7.seconds))
        ),
        fallback = 10.seconds,
        encodeSteps = true,
    )

    override val autoRecognitionSampleDuration = 8.seconds

    override suspend fun recognize(sample: AudioSample) = withContext(ioDispatcher) {
        val httpClient = httpClientLazy.get()
        val response = try {
            httpClient.submitFormWithBinaryData(
                url = REST_BASE_URL,
                formData = formData {
                    append("api_token", config.apiToken)
                    append("return", RETURN_PARAM)
                    append(
                        key = "file",
                        value = InputProvider(sample.file.length()) {
                            sample.file.inputStream().asInput().buffered()
                        },
                        headers = Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=sample")
                            append(HttpHeaders.ContentType, sample.mimeType)
                        }
                    )
                }
            )
        } catch (_: IOException) {
            return@withContext RemoteRecognitionResult.Error.BadConnection
        }
        if (response.status.isSuccess()) {
            try {
                response.body<AuddResponseJson>().toRecognitionResult(sample.timestamp, sample.duration)
            } catch (e: Exception) {
                ensureActive()
                RemoteRecognitionResult.Error.UnhandledError(message = e.message ?: "", cause = e)
            }
        } else {
            RemoteRecognitionResult.Error.HttpError(
                code = response.status.value,
                message = response.status.description
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun recognizeUntilFirstMatch(samples: Flow<AudioSample>) = withContext(ioDispatcher) {
        val samplesChannel = Channel<AudioSample>(Channel.UNLIMITED)
        var emptySamplesFlow = false
        val receiveSamplesWithTimeout = launch {
            samples
                .onEmpty { emptySamplesFlow = true }
                .collect(samplesChannel::send)
            samplesChannel.close()
            if (!emptySamplesFlow) {
                delay(TIMEOUT_AFTER_LAST_SAMPLE_RECEIVED)
            }
        }

        // Initially bad connection
        // for case when after TIMEOUT_AFTER_LAST_SAMPLE_RECEIVED there is no any response yet
        var lastResult: RemoteRecognitionResult = RemoteRecognitionResult.Error.BadConnection
        val remoteResult = async {
            var unprocessedSample: AudioSample? = null
            webSocketSession.startReconnectingSession(WEB_SOCKET_RECONNECT_LIMIT) {
                url {
                    takeFrom(WEB_SOCKET_BASE_URL)
                    parameters.apply {
                        append("api_token", config.apiToken)
                        append("return", RETURN_PARAM)
                    }
                }
            }.mapLatest { wsConnectionResult ->
                val wsConnection = wsConnectionResult.getOrElse { cause ->
                    val httpClient = httpClientLazy.get()
                    lastResult = if (isRecoverableWebSocketFailure(cause, httpClient.engine)) {
                        RemoteRecognitionResult.Error.BadConnection
                    } else {
                        RemoteRecognitionResult.Error.UnhandledError(cause = cause)
                    }
                    return@mapLatest lastResult
                }
                do {
                    val sampleToSend = unprocessedSample
                        ?: samplesChannel.receiveCatching().getOrNull()
                        ?: break // the channel is closed, no more samples
                    unprocessedSample = sampleToSend
                    // We don't expect any responses right now, but make sure the channel is empty
                    while (wsConnection.responseChannel.tryReceive().isSuccess) {
                        Log.w(TAG, "Unexpected response dropped")
                    }
                    val sampleData = try {
                        sampleToSend.file.readBytes()
                    } catch (e: IOException) {
                        lastResult = RemoteRecognitionResult.Error.BadRecording(
                            message = e.message ?: "",
                            cause = e
                        )
                        return@mapLatest lastResult
                    }
                    try {
                        wsConnection.sendSample(sampleData)
                    } catch (_: Exception) {
                        ensureActive()
                        Log.d(TAG, "WebSocket connection closed for sending")
                        awaitCancellation()
                    }
                    lastResult = try {
                        withTimeout(WEB_SOCKET_RESPONSE_TIMEOUT) {
                            wsConnection.responseChannel.receive().toRecognitionResult(
                                sampleToSend.timestamp, sampleToSend.duration
                            )
                        }
                    } catch (_: TimeoutCancellationException) {
                        RemoteRecognitionResult.Error.BadConnection
                    } catch (_: ClosedReceiveChannelException) {
                        awaitCancellation()
                    }
                    unprocessedSample = null
                } while (lastResult == RemoteRecognitionResult.NoMatches)
                lastResult
            }.first()
        }

        select {
            remoteResult.onJoin { receiveSamplesWithTimeout.cancelAndJoin() }
            receiveSamplesWithTimeout.onJoin {
                remoteResult.cancelAndJoin()
                if (emptySamplesFlow) {
                    lastResult = RemoteRecognitionResult.NoSoundDetected
                }
            }
        }
        lastResult
    }

    @AssistedFactory
    interface Factory {
        fun create(config: AuddConfig): AuddRecognitionService
    }

    companion object {
        private const val REST_BASE_URL = "https://api.audd.io/"
        private const val WEB_SOCKET_BASE_URL = "wss://api.audd.io/ws/"
        private const val RETURN_PARAM = "lyrics,spotify,apple_music,deezer,napster,musicbrainz,isrc!"

        private val TIMEOUT_AFTER_LAST_SAMPLE_RECEIVED = 10.seconds
        private val WEB_SOCKET_RESPONSE_TIMEOUT = 15.seconds
        private const val WEB_SOCKET_RECONNECT_LIMIT = 2
    }
}
