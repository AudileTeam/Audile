package com.mrsep.musicrecognizer.core.data.preferences

import android.os.Build
import com.mrsep.musicrecognizer.core.datastore.AudioCaptureModeProto
import com.mrsep.musicrecognizer.core.datastore.AutoBackupErrorTypeProto
import com.mrsep.musicrecognizer.core.datastore.AutoBackupResultProto
import com.mrsep.musicrecognizer.core.datastore.MusicServiceProto
import com.mrsep.musicrecognizer.core.datastore.RecognitionProviderProto
import com.mrsep.musicrecognizer.core.datastore.ScreenSideProto
import com.mrsep.musicrecognizer.core.datastore.SnappedWindowPositionProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto
import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.domain.preferences.AuddConfig
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
import com.mrsep.musicrecognizer.core.domain.preferences.AutoBackupPreferences
import com.mrsep.musicrecognizer.core.domain.preferences.AutoBackupError
import com.mrsep.musicrecognizer.core.domain.preferences.AutoBackupResult
import com.mrsep.musicrecognizer.core.domain.preferences.FallbackAction
import com.mrsep.musicrecognizer.core.domain.preferences.FallbackPolicy
import com.mrsep.musicrecognizer.core.domain.preferences.FavoritesMode
import com.mrsep.musicrecognizer.core.domain.preferences.FontSize
import com.mrsep.musicrecognizer.core.domain.preferences.HapticFeedback
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsStyle
import com.mrsep.musicrecognizer.core.domain.preferences.OrderBy
import com.mrsep.musicrecognizer.core.domain.preferences.ScreenSide
import com.mrsep.musicrecognizer.core.domain.preferences.SnappedWindowPosition
import com.mrsep.musicrecognizer.core.domain.preferences.SortBy
import com.mrsep.musicrecognizer.core.domain.preferences.ThemeMode
import com.mrsep.musicrecognizer.core.domain.preferences.TrackFilter
import com.mrsep.musicrecognizer.core.domain.preferences.UserPreferences
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import java.time.Instant

internal fun UserPreferencesProto.toDomain() = UserPreferences(
    onboardingCompleted = onboardingCompleted,
    currentRecognitionProvider = when (currentRecognitionProvider) {
        RecognitionProviderProto.Audd -> RecognitionProvider.Audd
        RecognitionProviderProto.AcrCloud -> RecognitionProvider.AcrCloud
        RecognitionProviderProto.Shazam -> RecognitionProvider.Shazam
        RecognitionProviderProto.UNRECOGNIZED -> error("Unexpected proto value")
    },
    auddConfig = AuddConfig(apiToken = apiToken),
    acrCloudConfig = AcrCloudConfig(
        host = acrCloudConfig.host,
        accessKey = acrCloudConfig.accessKey,
        accessSecret = acrCloudConfig.accessSecret
    ),
    defaultAudioCaptureMode = defaultAudioCaptureMode.toDomain(),
    mainButtonLongPressAudioCaptureMode = mainButtonLongPressAudioCaptureMode.toDomain(),
    lastUsedAudioCaptureMode = lastUsedAudioCaptureMode.toDomain(),
    usePrerecording = usePrerecording
        .takeIf { hasUsePrerecording() }
        ?: true,
    useAltDeviceSoundSource = useAltDeviceSoundSource
        .takeIf { hasUseAltDeviceSoundSource() }
        ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q),
    fallbackPolicy = FallbackPolicy(
        noMatches = fallbackPolicy.noMatches.toDomain(),
        badConnection = fallbackPolicy.badConnection.toDomain(),
        anotherFailure = fallbackPolicy.anotherFailure.toDomain()
    ),
    autoDeleteSavedRecordingOnMatch = autoDeleteSavedRecordingOnMatch
        .takeIf { hasAutoDeleteSavedRecordingOnMatch() }
        ?: true,
    recognizeOnStartup = recognizeOnStartup,
    requiredMusicServices = requiredMusicServicesList.map { service ->
        when (service) {
            MusicServiceProto.AmazonMusic -> MusicService.AmazonMusic
            MusicServiceProto.Anghami -> MusicService.Anghami
            MusicServiceProto.AppleMusic -> MusicService.AppleMusic
            MusicServiceProto.Audiomack -> MusicService.Audiomack
            MusicServiceProto.Audius -> MusicService.Audius
            MusicServiceProto.Boomplay -> MusicService.Boomplay
            MusicServiceProto.Deezer -> MusicService.Deezer
            MusicServiceProto.JioSaavn -> MusicService.JioSaavn
            MusicServiceProto.MusicBrainz -> MusicService.MusicBrainz
            MusicServiceProto.Napster -> MusicService.Napster
            MusicServiceProto.Pandora -> MusicService.Pandora
            MusicServiceProto.Soundcloud -> MusicService.Soundcloud
            MusicServiceProto.Spotify -> MusicService.Spotify
            MusicServiceProto.Tidal -> MusicService.Tidal
            MusicServiceProto.YandexMusic -> MusicService.YandexMusic
            MusicServiceProto.Youtube -> MusicService.Youtube
            MusicServiceProto.YoutubeMusic -> MusicService.YoutubeMusic
            MusicServiceProto.Qobuz -> MusicService.Qobuz
            MusicServiceProto.UNRECOGNIZED -> error("Unexpected proto value")
        }
    },
    notificationServiceEnabled = notificationServiceEnabled,
    floatingButtonEnabled = floatingButtonEnabled,
    floatingButtonPosition = if (hasFloatingButtonPosition()) {
        floatingButtonPosition.toDomain()
    } else {
        SnappedWindowPosition.getDefault()
    },
    dynamicColorsEnabled = dynamicColorsEnabled,
    artworkBasedThemeEnabled = artworkBasedThemeEnabled,
    lyricsStyle = LyricsStyle(
        fontSize = when (lyricsStyle.fontSize) {
            UserPreferencesProto.FontSizeProto.SMALL -> FontSize.Small
            UserPreferencesProto.FontSizeProto.NORMAL -> FontSize.Normal
            UserPreferencesProto.FontSizeProto.LARGE -> FontSize.Large
            UserPreferencesProto.FontSizeProto.HUGE -> FontSize.Huge
            UserPreferencesProto.FontSizeProto.UNRECOGNIZED -> error("Unexpected proto value")
        },
        isBold = lyricsStyle.isBold,
        isHighContrast = lyricsStyle.isHighContrast,
        alignToStart = lyricsStyle.alignToStart
    ),
    trackFilter = TrackFilter(
        favoritesMode = when (trackFilter.favoritesMode) {
            UserPreferencesProto.FavoritesModeProto.ALL -> FavoritesMode.All
            UserPreferencesProto.FavoritesModeProto.ONLY_FAVORITES -> FavoritesMode.OnlyFavorites
            UserPreferencesProto.FavoritesModeProto.EXCLUDE_FAVORITES -> FavoritesMode.ExcludeFavorites
            UserPreferencesProto.FavoritesModeProto.UNRECOGNIZED -> error("Unexpected proto value")
        },
        sortBy = when (trackFilter.sortBy) {
            UserPreferencesProto.SortByProto.RECOGNITION_DATE -> SortBy.RecognitionDate
            UserPreferencesProto.SortByProto.TITLE -> SortBy.Title
            UserPreferencesProto.SortByProto.ARTIST -> SortBy.Artist
            UserPreferencesProto.SortByProto.RELEASE_DATE -> SortBy.ReleaseDate
            UserPreferencesProto.SortByProto.UNRECOGNIZED -> error("Unexpected proto value")
        },
        orderBy = when (trackFilter.orderBy) {
            UserPreferencesProto.OrderByProto.ASC -> OrderBy.Asc
            UserPreferencesProto.OrderByProto.DESC -> OrderBy.Desc
            UserPreferencesProto.OrderByProto.UNRECOGNIZED -> error("Unexpected proto value")
        },
        dateRange = trackFilter.startDate..trackFilter.endDate
    ),
    hapticFeedback = HapticFeedback(
        vibrateOnTap = hapticFeedback.vibrateOnTap,
        vibrateOnResult = hapticFeedback.vibrateOnResult
    ),
    useGridForLibrary = useGridForLibrary,
    useGridForRecognitionQueue = useGridForRecognitionQueue,
    showRecognitionDateInLibrary = showRecognitionDateInLibrary,
    showCreationDateInQueue = showCreationDateInQueue,
    themeMode = when (themeMode) {
        UserPreferencesProto.ThemeModeProto.FOLLOW_SYSTEM -> ThemeMode.FollowSystem
        UserPreferencesProto.ThemeModeProto.ALWAYS_LIGHT -> ThemeMode.AlwaysLight
        UserPreferencesProto.ThemeModeProto.ALWAYS_DARK -> ThemeMode.AlwaysDark
        UserPreferencesProto.ThemeModeProto.UNRECOGNIZED -> error("Unexpected proto value")
    },
    usePureBlackForDarkTheme = usePureBlackForDarkTheme,
    autoBackup = if (hasAutoBackup()) {
        AutoBackupPreferences(
            enabled = autoBackup.enabled,
            treeUri = autoBackup.treeUri,
            intervalDays = autoBackup.intervalDays,
            keepCount = autoBackup.keepCount,
        )
    } else {
        AutoBackupPreferences(
            enabled = false,
            treeUri = "",
            intervalDays = AutoBackupPreferences.DEFAULT_INTERVAL_DAYS,
            keepCount = AutoBackupPreferences.DEFAULT_KEEP_COUNT,
        )
    },
    autoBackupLastResult = if (hasLastAutoBackupResult()) {
        lastAutoBackupResult.toDomain()
    } else {
        null
    },
    autoBackupConsecutiveFailures = autoBackupConsecutiveFailures,
    allowQueueOnLowBattery = allowQueueOnLowBattery,
)

internal fun AudioCaptureModeProto.toDomain() = when (this) {
    AudioCaptureModeProto.Unspecified -> AudioCaptureMode.Microphone
    AudioCaptureModeProto.Microphone -> AudioCaptureMode.Microphone
    AudioCaptureModeProto.Device -> AudioCaptureMode.Device
    AudioCaptureModeProto.Auto -> AudioCaptureMode.Auto
    AudioCaptureModeProto.UNRECOGNIZED -> error("Unexpected proto value")
}

internal fun UserPreferencesProto.FallbackActionProto.toDomain() = when (this) {
    UserPreferencesProto.FallbackActionProto.IGNORE -> FallbackAction.Ignore
    UserPreferencesProto.FallbackActionProto.SAVE -> FallbackAction.Save
    UserPreferencesProto.FallbackActionProto.SAVE_AND_LAUNCH -> FallbackAction.SaveAndLaunch
    UserPreferencesProto.FallbackActionProto.UNRECOGNIZED -> error("Unexpected proto value")
}

internal fun AutoBackupResultProto.toDomain(): AutoBackupResult {
    val timestamp = Instant.ofEpochMilli(timestampEpochMilli)
    return when (statusCase) {
        AutoBackupResultProto.StatusCase.SUCCESS -> {
            AutoBackupResult.Success(timestamp)
        }
        AutoBackupResultProto.StatusCase.FAILURE -> {
            AutoBackupResult.Failure(
                timestamp = timestamp,
                reason = failure.kind.toDomain(),
                message = failure.message.ifBlank { null },
            )
        }
        AutoBackupResultProto.StatusCase.STATUS_NOT_SET,
        null -> AutoBackupResult.Failure(timestamp, AutoBackupError.Unhandled)
    }
}

internal fun AutoBackupErrorTypeProto.toDomain() = when (this) {
    AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_ACCESS_DENIED -> AutoBackupError.AccessDenied
    AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_FILE_NOT_FOUND -> AutoBackupError.FileNotFound
    AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_UNHANDLED,
    AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_UNSPECIFIED,
    AutoBackupErrorTypeProto.UNRECOGNIZED -> AutoBackupError.Unhandled
}

internal fun SnappedWindowPositionProto.toDomain() = SnappedWindowPosition(
    side = when (side) {
        ScreenSideProto.Left -> ScreenSide.Left
        ScreenSideProto.Right -> ScreenSide.Right
        ScreenSideProto.UNRECOGNIZED -> error("Unexpected proto value")
    },
    fractionY = fractionY.coerceIn(0f, 1f)
)
