package com.mrsep.musicrecognizer.core.ui.resources

import androidx.compose.runtime.Stable
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.strings.R as StringsR
import com.mrsep.musicrecognizer.core.ui.R as UiR

@Stable
fun MusicService.titleId() = when (this) {
    MusicService.AmazonMusic -> StringsR.string.amazon_music
    MusicService.Anghami -> StringsR.string.anghami
    MusicService.AppleMusic -> StringsR.string.apple_music
    MusicService.Audiomack -> StringsR.string.audiomack
    MusicService.Audius -> StringsR.string.audius
    MusicService.Boomplay -> StringsR.string.boomplay
    MusicService.Deezer -> StringsR.string.deezer
    MusicService.JioSaavn -> StringsR.string.jiosaavn
    MusicService.MusicBrainz -> StringsR.string.musicbrainz
    MusicService.Napster -> StringsR.string.napster
    MusicService.Pandora -> StringsR.string.pandora
    MusicService.Qobuz -> StringsR.string.qobuz
    MusicService.Soundcloud -> StringsR.string.soundcloud
    MusicService.Spotify -> StringsR.string.spotify
    MusicService.Tidal -> StringsR.string.tidal
    MusicService.YandexMusic -> StringsR.string.yandex_music
    MusicService.Youtube -> StringsR.string.youtube
    MusicService.YoutubeMusic -> StringsR.string.youtubeMusic
}

@Stable
fun MusicService.iconId() = when (this) {
    MusicService.AmazonMusic -> UiR.drawable.ic_amazon_24
    MusicService.Anghami -> UiR.drawable.ic_anghami_24
    MusicService.AppleMusic -> UiR.drawable.ic_apple_24
    MusicService.Audiomack -> UiR.drawable.ic_audiomack_24
    MusicService.Audius -> UiR.drawable.ic_audius_24
    MusicService.Boomplay -> UiR.drawable.ic_boomplay_24
    MusicService.Deezer -> UiR.drawable.ic_deezer_24
    MusicService.JioSaavn -> UiR.drawable.ic_jiosaavn_24
    MusicService.MusicBrainz -> UiR.drawable.ic_musicbrainz_24
    MusicService.Napster -> UiR.drawable.ic_napster_24
    MusicService.Pandora -> UiR.drawable.ic_pandora_24
    MusicService.Qobuz -> UiR.drawable.ic_qobuz_24
    MusicService.Soundcloud -> UiR.drawable.ic_soundcloud_24
    MusicService.Spotify -> UiR.drawable.ic_spotify_24
    MusicService.Tidal -> UiR.drawable.ic_tidal_24
    MusicService.YandexMusic -> UiR.drawable.ic_yandex_music_24
    MusicService.Youtube -> UiR.drawable.ic_youtube_24
    MusicService.YoutubeMusic -> UiR.drawable.ic_youtube_music_24
}
