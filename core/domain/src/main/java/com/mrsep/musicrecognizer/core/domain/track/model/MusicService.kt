package com.mrsep.musicrecognizer.core.domain.track.model

/** Entries are declared in alphabetical order. */
enum class MusicService {
    AmazonMusic,
    Anghami,
    AppleMusic,
    Audiomack,
    Audius,
    Boomplay,
    Deezer,
    JioSaavn,
    MusicBrainz,
    Napster,
    Pandora,
    Qobuz,
    Soundcloud,
    Spotify,
    Tidal,
    YandexMusic,
    Youtube,
    YoutubeMusic;

    companion object {

        init {
            check(entries.zipWithNext().all { (prev, next) -> prev.name < next.name }) {
                "Entries must be in alphabetical order"
            }
        }

        /** Rough popularity, most popular first. */
        val entriesByPopularity: List<MusicService> = listOf(
            Spotify,
            AppleMusic,
            Youtube,
            YoutubeMusic,
            AmazonMusic,
            Deezer,
            Tidal,
            Qobuz,
            JioSaavn,
            Soundcloud,
            YandexMusic,
            Pandora,
            Anghami,
            Audiomack,
            Audius,
            Boomplay,
            Napster,
            MusicBrainz,
        ).also { check(it.distinct().size == entries.size) }
    }
}
