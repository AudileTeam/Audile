package com.mrsep.musicrecognizer.core.audio.audiorecord.encoder

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.MuxerException
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.muxer.WavMuxer
import com.mrsep.musicrecognizer.core.audio.audiorecord.AudioEncoderDispatcher
import com.mrsep.musicrecognizer.core.audio.audiorecord.AudioRecordingSessionFactory
import com.mrsep.musicrecognizer.core.audio.audiorecord.prerecording.PrerecordingSoundSource
import com.mrsep.musicrecognizer.core.audio.audiorecord.soundsource.SoundSourceConfig
import com.mrsep.musicrecognizer.core.domain.recognition.AudioRecording
import com.mrsep.musicrecognizer.core.domain.recognition.AudioRecordingSession
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecordingScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@OptIn(UnstableApi::class)
internal class WavRecordingController(
    private val soundSource: PrerecordingSoundSource,
    private val audioRecordingDataSource: AudioRecordingDataSource,
) : AudioRecordingSessionFactory {

    context(scope: CoroutineScope)
    override fun startRecordingSession(scheme: RecordingScheme, includeBuffered: Boolean) = object : AudioRecordingSession {
        private val sessionId = UUID.randomUUID()
        override val recordings = Channel<AudioRecording>(Channel.UNLIMITED)
        private val job = scope.produceRecordingsToChannel(sessionId, scheme, includeBuffered, recordings)

        override suspend fun cancelAndDeleteSessionFiles() {
            job.cancelAndJoin()
            audioRecordingDataSource.deleteSessionFiles(sessionId)
        }
    }

    private fun CoroutineScope.produceRecordingsToChannel(
        sessionId: UUID,
        scheme: RecordingScheme,
        includeBuffered: Boolean,
        channel: SendChannel<AudioRecording>,
    ): Job = launch(AudioEncoderDispatcher) {
        val muxers: MutableMap<ScheduledRecording, WavMuxerWrapper> = mutableMapOf()
        try {
            val soundSourceParams = checkNotNull(soundSource.params)

            val silenceTracker = UnsafeSilenceTracker()
            launch {
                soundSource.soundLevel
                    .map { it == 0f }
                    .distinctUntilChanged()
                    .collect(silenceTracker::onSilenceStateChanged)
            }

            val scheduledRecordings = scheme.flatten()
            var emittedRecordingCount = 0
            var schemeCompleted = false

            var streamStartTimestamp: Instant? = null

            soundSource.captureFlow(includeBuffered)
                .buffer(Channel.UNLIMITED)
                .transformWhile { chunk ->
                    emit(chunk)
                    !schemeCompleted
                }
                .collect { pcmChunkResult ->
                    val pcmChunk = pcmChunkResult.getOrElse { cause ->
                        channel.close(cause)
                        schemeCompleted = true
                        this@launch.cancel()
                        return@collect
                    }
                    if (streamStartTimestamp == null) {
                        streamStartTimestamp = pcmChunk.startInstant
                    }
                    val chunkOffset = pcmChunk.startInstant - streamStartTimestamp
                    for (scheduledRecording in scheduledRecordings) {
                        if (chunkOffset < scheduledRecording.presentationOffset) continue

                        val muxer = muxers.getOrPut(scheduledRecording) {
                            val startTimestamp = streamStartTimestamp + chunkOffset
                            val outputFile = runBlocking {
                                audioRecordingDataSource.createNewFile(sessionId, RECORDING_FILE_EXT)
                            }.getOrElse { cause ->
                                channel.close(cause)
                                schemeCompleted = true
                                this@launch.cancel()
                                return@collect
                            }
                            WavMuxerWrapper(
                                outputFile = outputFile,
                                startTimestamp = startTimestamp,
                                soundSourceConfig = soundSourceParams
                            )
                        }
                        if (muxer.isReleased) continue

                        val currentFileDuration = muxer.currentDuration
                        if (currentFileDuration < scheduledRecording.minDuration) {
                            try {
                                muxer.writeChunk(pcmChunk.data, pcmChunk.duration)
                            } catch (e: MuxerException) {
                                channel.close(e)
                                schemeCompleted = true
                                this@launch.cancel()
                                return@collect
                            }
                        } else {
                            val file = muxer.release()
                            val silenceDuration = silenceTracker.querySilenceDuration(
                                startTime = muxer.startTimestamp,
                                endTime = muxer.startTimestamp + currentFileDuration,
                            )
                            val recording = AudioRecording(
                                file = file,
                                timestamp = muxer.startTimestamp.toJavaInstant(),
                                source = soundSource.audioSource,
                                duration = currentFileDuration,
                                nonSilenceDuration = currentFileDuration - silenceDuration,
                                sampleRate = soundSourceParams.audioFormat.sampleRate,
                                mimeType = MIME_TYPE,
                                isFallback = scheduledRecording.isFallback
                            )
                            channel.trySendBlocking(recording)
                            emittedRecordingCount++
                        }
                    }

                    if (scheduledRecordings.size == emittedRecordingCount) {
                        channel.close()
                        schemeCompleted = true
                    }
                }

        } catch (e: Exception) {
            ensureActive()
            channel.close(e)
        } finally {
            coroutineContext.cancelChildren()
            muxers.forEach { (_, muxer) -> muxer.release() }
            channel.close()
        }
    }

    companion object {
        private const val MIME_TYPE = "audio/x-wav"
        private const val RECORDING_FILE_EXT = "wav"
    }
}

@OptIn(UnstableApi::class)
private class WavMuxerWrapper(
    private val outputFile: File,
    val startTimestamp: Instant,
    val soundSourceConfig: SoundSourceConfig,
) {
    var isReleased = false
        private set

    var currentDuration: Duration = Duration.ZERO
        private set

    private var muxer: WavMuxer? = null

    @Throws(MuxerException::class)
    fun writeChunk(chunk: ByteArray, chunkDuration: Duration) {
        check(!isReleased)
        val muxer = muxer ?: WavMuxer(SeekableMuxerOutput.of(outputFile.absolutePath)).apply {
            addTrack(
                @SuppressLint("WrongConstant")
                Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(soundSourceConfig.audioFormat.encoding)
                    .setChannelCount(soundSourceConfig.audioFormat.channelCount)
                    .setSampleRate(soundSourceConfig.audioFormat.sampleRate)
                    .build()
            )
            muxer = this
        }
        muxer.writeSampleData(
            0,
            ByteBuffer.wrap(chunk),
            BufferInfo(currentDuration.inWholeMicroseconds, chunk.size, 0)
        )
        currentDuration += chunkDuration
    }

    fun release(): File {
        try {
            muxer?.close()
        } finally {
            muxer = null
            isReleased = true
        }
        return outputFile
    }
}
