package com.mrsep.musicrecognizer.core.datastore

import android.os.Build
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.fallbackPolicyProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.hapticFeedbackProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.lyricsStyleProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.trackFilterProto
import java.io.InputStream
import java.io.OutputStream

internal object UserPreferencesProtoSerializer : Serializer<UserPreferencesProto> {

    override val defaultValue: UserPreferencesProto
        get() = userPreferencesProto {
            onboardingCompleted = true
            currentRecognitionProvider = RecognitionProviderProto.Shazam
            apiToken = BuildConfig.AUDD_TOKEN
            acrCloudConfig = acrCloudConfigProto {
                host = BuildConfig.ACR_CLOUD_HOST
                accessKey = BuildConfig.ACR_CLOUD_ACCESS_KEY
                accessSecret = BuildConfig.ACR_CLOUD_ACCESS_SECRET
            }
            defaultAudioCaptureMode = AudioCaptureModeProto.Microphone
            mainButtonLongPressAudioCaptureMode =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    AudioCaptureModeProto.Microphone
                } else {
                    AudioCaptureModeProto.Auto
                }
            lastUsedAudioCaptureMode = AudioCaptureModeProto.Microphone
            useAltDeviceSoundSource = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            fallbackPolicy = fallbackPolicyProto {
                noMatches = UserPreferencesProto.FallbackActionProto.IGNORE
                badConnection = UserPreferencesProto.FallbackActionProto.SAVE_AND_LAUNCH
                anotherFailure = UserPreferencesProto.FallbackActionProto.SAVE
            }
            autoDeleteSavedRecordingOnMatch = true
            usePrerecording = true
            recognizeOnStartup = false
            requiredMusicServices.addAll(
                // Ordered by popularity
                // Disable services that cannot be resolved using only the Odesli API and require additional calls
                listOf(
                    MusicServiceProto.Spotify,
                    MusicServiceProto.AppleMusic,
                    MusicServiceProto.Youtube,
                    MusicServiceProto.YoutubeMusic,
                    MusicServiceProto.AmazonMusic,
                    MusicServiceProto.Deezer,
                    MusicServiceProto.Tidal,
//                    MusicServiceProto.Qobuz,
//                    MusicServiceProto.JioSaavn,
                    MusicServiceProto.Soundcloud,
                    MusicServiceProto.YandexMusic,
                    MusicServiceProto.Napster,
                    MusicServiceProto.Pandora,
                    MusicServiceProto.Anghami,
                    MusicServiceProto.Audiomack,
                    MusicServiceProto.Audius,
                    MusicServiceProto.Boomplay,
//                    MusicServiceProto.MusicBrainz,
                )
            )
            notificationServiceEnabled = false
            floatingButtonEnabled = false
            floatingButtonPosition = snappedWindowPositionProto {
                side = ScreenSideProto.Right
                fractionY = 0.4f
            }
            dynamicColorsEnabled = true
            artworkBasedThemeEnabled = true
            useGridForLibrary = false
            useGridForRecognitionQueue = false
            showRecognitionDateInLibrary = false
            showCreationDateInQueue = false
            lyricsStyle = lyricsStyleProto {
                fontSize = UserPreferencesProto.FontSizeProto.LARGE
                isBold = true
                isHighContrast = false
                alignToStart = false
            }
            trackFilter = trackFilterProto {
                favoritesMode = UserPreferencesProto.FavoritesModeProto.ALL
                sortBy = UserPreferencesProto.SortByProto.RECOGNITION_DATE
                orderBy = UserPreferencesProto.OrderByProto.DESC
                startDate = Long.MIN_VALUE
                endDate = Long.MAX_VALUE
            }
            hapticFeedback = hapticFeedbackProto {
                vibrateOnTap = true
                vibrateOnResult = true
            }
            themeMode = UserPreferencesProto.ThemeModeProto.FOLLOW_SYSTEM
            usePureBlackForDarkTheme = false
            hasDoneRequiredMusicServicesMigration = true
            autoBackup = autoBackupPreferencesProto {
                enabled = false
                treeUri = ""
                intervalDays = 7
                keepCount = 3
            }
            autoBackupConsecutiveFailures = 0
            allowQueueOnLowBattery = false
        }

    override suspend fun readFrom(input: InputStream): UserPreferencesProto {
        try {
            return UserPreferencesProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: UserPreferencesProto, output: OutputStream) {
        t.writeTo(output)
    }
}
