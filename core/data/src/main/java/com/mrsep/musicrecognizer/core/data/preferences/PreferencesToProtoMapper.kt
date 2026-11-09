package com.mrsep.musicrecognizer.core.data.preferences

import com.mrsep.musicrecognizer.core.datastore.AudioCaptureModeProto
import com.mrsep.musicrecognizer.core.datastore.AutoBackupErrorTypeProto
import com.mrsep.musicrecognizer.core.datastore.autoBackupFailureProto
import com.mrsep.musicrecognizer.core.datastore.autoBackupResultProto
import com.mrsep.musicrecognizer.core.datastore.autoBackupSuccessProto
import com.mrsep.musicrecognizer.core.datastore.MusicServiceProto
import com.mrsep.musicrecognizer.core.datastore.RecognitionProviderProto
import com.mrsep.musicrecognizer.core.datastore.ScreenSideProto
import com.mrsep.musicrecognizer.core.datastore.snappedWindowPositionProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto.FallbackActionProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto.FavoritesModeProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto.FontSizeProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto.OrderByProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto.SortByProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProto.ThemeModeProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.fallbackPolicyProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.hapticFeedbackProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.lyricsStyleProto
import com.mrsep.musicrecognizer.core.datastore.UserPreferencesProtoKt.trackFilterProto
import com.mrsep.musicrecognizer.core.datastore.acrCloudConfigProto
import com.mrsep.musicrecognizer.core.domain.preferences.AcrCloudConfig
import com.mrsep.musicrecognizer.core.domain.preferences.AudioCaptureMode
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
import com.mrsep.musicrecognizer.core.domain.recognition.model.RecognitionProvider
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService

internal fun RecognitionProvider.toProto() = when (this) {
    RecognitionProvider.Audd -> RecognitionProviderProto.Audd
    RecognitionProvider.AcrCloud -> RecognitionProviderProto.AcrCloud
    RecognitionProvider.Shazam -> RecognitionProviderProto.Shazam
}

internal fun AcrCloudConfig.toProto() = acrCloudConfigProto {
    host = this@toProto.host
    accessKey = this@toProto.accessKey
    accessSecret = this@toProto.accessSecret
}

internal fun AudioCaptureMode.toProto() = when (this) {
    AudioCaptureMode.Microphone -> AudioCaptureModeProto.Microphone
    AudioCaptureMode.Device -> AudioCaptureModeProto.Device
    AudioCaptureMode.Auto -> AudioCaptureModeProto.Auto
}

internal fun FallbackAction.toProto() = when (this) {
    FallbackAction.Ignore -> FallbackActionProto.IGNORE
    FallbackAction.Save -> FallbackActionProto.SAVE
    FallbackAction.SaveAndLaunch -> FallbackActionProto.SAVE_AND_LAUNCH
}

internal fun FallbackPolicy.toProto() = fallbackPolicyProto {
    noMatches = this@toProto.noMatches.toProto()
    badConnection = this@toProto.badConnection.toProto()
    anotherFailure = this@toProto.anotherFailure.toProto()
}

internal fun MusicService.toProto() = when (this) {
    MusicService.AmazonMusic -> MusicServiceProto.AmazonMusic
    MusicService.Anghami -> MusicServiceProto.Anghami
    MusicService.AppleMusic -> MusicServiceProto.AppleMusic
    MusicService.Audiomack -> MusicServiceProto.Audiomack
    MusicService.Audius -> MusicServiceProto.Audius
    MusicService.Boomplay -> MusicServiceProto.Boomplay
    MusicService.Deezer -> MusicServiceProto.Deezer
    MusicService.JioSaavn -> MusicServiceProto.JioSaavn
    MusicService.MusicBrainz -> MusicServiceProto.MusicBrainz
    MusicService.Napster -> MusicServiceProto.Napster
    MusicService.Pandora -> MusicServiceProto.Pandora
    MusicService.Qobuz -> MusicServiceProto.Qobuz
    MusicService.Soundcloud -> MusicServiceProto.Soundcloud
    MusicService.Spotify -> MusicServiceProto.Spotify
    MusicService.Tidal -> MusicServiceProto.Tidal
    MusicService.YandexMusic -> MusicServiceProto.YandexMusic
    MusicService.Youtube -> MusicServiceProto.Youtube
    MusicService.YoutubeMusic -> MusicServiceProto.YoutubeMusic
}

internal fun LyricsStyle.toProto() = lyricsStyleProto {
    fontSize = this@toProto.fontSize.toProto()
    isBold = this@toProto.isBold
    isHighContrast = this@toProto.isHighContrast
    alignToStart = this@toProto.alignToStart
}

internal fun FontSize.toProto() = when (this) {
    FontSize.Small -> FontSizeProto.SMALL
    FontSize.Normal -> FontSizeProto.NORMAL
    FontSize.Large -> FontSizeProto.LARGE
    FontSize.Huge -> FontSizeProto.HUGE
}

internal fun ThemeMode.toProto() = when (this) {
    ThemeMode.FollowSystem -> ThemeModeProto.FOLLOW_SYSTEM
    ThemeMode.AlwaysLight -> ThemeModeProto.ALWAYS_LIGHT
    ThemeMode.AlwaysDark -> ThemeModeProto.ALWAYS_DARK
}

internal fun TrackFilter.toProto() = trackFilterProto {
    favoritesMode = when (this@toProto.favoritesMode) {
        FavoritesMode.All -> FavoritesModeProto.ALL
        FavoritesMode.OnlyFavorites -> FavoritesModeProto.ONLY_FAVORITES
        FavoritesMode.ExcludeFavorites -> FavoritesModeProto.EXCLUDE_FAVORITES
    }
    sortBy = when (this@toProto.sortBy) {
        SortBy.RecognitionDate -> SortByProto.RECOGNITION_DATE
        SortBy.Title -> SortByProto.TITLE
        SortBy.Artist -> SortByProto.ARTIST
        SortBy.ReleaseDate -> SortByProto.RELEASE_DATE
    }
    orderBy = when (this@toProto.orderBy) {
        OrderBy.Asc -> OrderByProto.ASC
        OrderBy.Desc -> OrderByProto.DESC
    }
    startDate = this@toProto.dateRange.first
    endDate = this@toProto.dateRange.last
}

internal fun HapticFeedback.toProto() = hapticFeedbackProto {
    vibrateOnTap = this@toProto.vibrateOnTap
    vibrateOnResult = this@toProto.vibrateOnResult
}

internal fun AutoBackupResult.toProto() = autoBackupResultProto {
    timestampEpochMilli = timestamp.toEpochMilli()
    when (val result = this@toProto) {
        is AutoBackupResult.Success -> {
            success = autoBackupSuccessProto {}
        }
        is AutoBackupResult.Failure -> {
            failure = autoBackupFailureProto {
                kind = result.reason.toProto()
                message = result.message.orEmpty()
            }
        }
    }
}

internal fun AutoBackupError.toProto() = when (this) {
    AutoBackupError.AccessDenied -> AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_ACCESS_DENIED
    AutoBackupError.FileNotFound -> AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_FILE_NOT_FOUND
    AutoBackupError.Unhandled -> AutoBackupErrorTypeProto.AUTO_BACKUP_ERROR_UNHANDLED
}

internal fun SnappedWindowPosition.toProto() = snappedWindowPositionProto {
    side = this@toProto.side.toProto()
    fractionY = this@toProto.fractionY
}

internal fun ScreenSide.toProto() = when (this) {
    ScreenSide.Left -> ScreenSideProto.Left
    ScreenSide.Right -> ScreenSideProto.Right
}
