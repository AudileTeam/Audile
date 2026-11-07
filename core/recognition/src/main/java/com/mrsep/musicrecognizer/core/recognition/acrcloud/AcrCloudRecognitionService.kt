package com.mrsep.musicrecognizer.core.recognition.acrcloud

import android.util.Log
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.domain.recognition.AudioSample
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecordingScheme
import com.mrsep.musicrecognizer.core.domain.recognition.model.RemoteRecognitionResult
import com.mrsep.musicrecognizer.core.recognition.BaseRemoteRecognitionService
import com.mrsep.musicrecognizer.core.recognition.acrcloud.json.AcrCloudResponseJson
import com.mrsep.musicrecognizer.core.recognition.acrcloud.json.toRecognitionResult
import com.mrsep.musicrecognizer.core.metadata.artwork.ArtworkFetcher
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
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AcrCloudRecognitionService"

internal class AcrCloudRecognitionService @AssistedInject constructor(
    @Assisted private val config: AcrCloudConfig,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val httpClientLazy: dagger.Lazy<HttpClient>,
    private val artworkFetcher: ArtworkFetcher,
) : BaseRemoteRecognitionService(ioDispatcher) {

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
        if (sample.duration > SAMPLE_DURATION_LIMIT) {
            // It's strange, but tests show that samples are truncated from the beginning, not the end
            Log.w(TAG, "It is discouraged to send samples longer than $SAMPLE_DURATION_LIMIT;" +
                    " the beginning will be truncated on the server side.")
        }
        val httpClient = httpClientLazy.get()
        val timestamp = Instant.now().epochSecond.toString()
        val signature = createSignature(timestamp, config)

        val response = try {
            val sampleSize = sample.file.length()
            httpClient.submitFormWithBinaryData(
                url = "https://${config.host}$ENDPOINT",
                formData = formData {
                    append("timestamp", timestamp)
                    append("access_key", config.accessKey)
                    append("signature_version", SIGNATURE_VERSION)
                    append("signature", signature)
                    append("data_type", DATA_TYPE)
                    append("sample_bytes", sampleSize)
                    append(
                        key = "sample",
                        value = InputProvider(sampleSize) {
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
            val remoteResult = try {
                response.body<AcrCloudResponseJson>().toRecognitionResult(sample.timestamp, sample.duration)
            } catch (e: Exception) {
                ensureActive()
                RemoteRecognitionResult.Error.UnhandledError(message = e.message ?: "", cause = e)
            }
            if (remoteResult is RemoteRecognitionResult.Success) {
                val track = remoteResult.track
                val artworkDeferred = async { artworkFetcher.fetchUrl(track) }
                val artwork = (artworkDeferred.await() as? NetworkResult.Success)?.data
                val finalTrack = track.copy(
                    artworkThumbUrl = track.artworkThumbUrl ?: artwork?.thumbUrl,
                    artworkUrl = track.artworkUrl ?: artwork?.url,
                )
                RemoteRecognitionResult.Success(finalTrack)
            } else {
                remoteResult
            }
        } else {
            RemoteRecognitionResult.Error.HttpError(
                code = response.status.value,
                message = response.status.description
            )
        }
    }

    private fun createSignature(timestamp: String, config: AcrCloudConfig): String {
        val sigString = METHOD + "\n" + ENDPOINT + "\n" + config.accessKey +
                "\n" + DATA_TYPE + "\n" + SIGNATURE_VERSION + "\n" + timestamp
        return encryptByHMACSHA1(sigString.toByteArray(), config.accessSecret.toByteArray())
    }

    @Suppress("SpellCheckingInspection")
    private fun encryptByHMACSHA1(data: ByteArray, key: ByteArray): String {
        return try {
            val signingKey = SecretKeySpec(key, "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(signingKey)
            val rawHmac = mac.doFinal(data)
            Base64.encode(rawHmac)
        } catch (e: Exception) {
            Log.e(this::class.simpleName, "Error during signature encryption", e)
            ""
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(config: AcrCloudConfig): AcrCloudRecognitionService
    }

    companion object {
        private const val METHOD = "POST"
        private const val ENDPOINT = "/v1/identify"
        private const val DATA_TYPE = "audio"
        private const val SIGNATURE_VERSION = "1"

        val SAMPLE_DURATION_LIMIT = 12.seconds
    }
}
