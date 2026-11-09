package com.mrsep.musicrecognizer.core.metadata

import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

@Suppress("SpellCheckingInspection")
internal class MusicServiceUtilsTest {

    @Test
    fun extractsIds() {
        val cases = listOf(
            Case(MusicService.Spotify, "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b", "0VjIjW4GlUZAMYd2vXMi3b"),
            Case(MusicService.Spotify, "https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b?si=abcd1234", "0VjIjW4GlUZAMYd2vXMi3b"),
            Case(MusicService.Spotify, "https://open.spotify.com/intl-en/track/0VjIjW4GlUZAMYd2vXMi3b", "0VjIjW4GlUZAMYd2vXMi3b"),
            Case(MusicService.AppleMusic, "https://music.apple.com/us/album/up-on-the-ladder/1160370310?i=1160370565", "1160370565"),
            Case(MusicService.AppleMusic, "https://geo.music.apple.com/us/album/_/1160370310?i=1160370565&mt=1&app=music&ls=1", "1160370565"),
            Case(MusicService.AppleMusic, "https://music.apple.com/us/song/blinding-lights/1499378607", "1499378607"),
            Case(MusicService.Youtube, "https://www.youtube.com/watch?v=4NRXx6U8ABQ", "4NRXx6U8ABQ"),
            Case(MusicService.Youtube, "https://youtu.be/4NRXx6U8ABQ", "4NRXx6U8ABQ"),
            Case(MusicService.Youtube, "https://www.youtube.com/shorts/4NRXx6U8ABQ", "4NRXx6U8ABQ"),
            Case(MusicService.YoutubeMusic, "https://music.youtube.com/watch?v=4NRXx6U8ABQ", "4NRXx6U8ABQ"),
            Case(MusicService.Deezer, "https://www.deezer.com/track/908604612", "908604612"),
            Case(MusicService.Deezer, "https://www.deezer.com/en/track/908604612", "908604612"),
            Case(MusicService.Tidal, "https://listen.tidal.com/track/134858527", "134858527"),
            Case(MusicService.Tidal, "https://tidal.com/browse/track/134858527", "134858527"),
            Case(MusicService.Napster, "https://play.napster.com/track/tra.511284580", "tra.511284580"),
            Case(MusicService.YandexMusic, "https://music.yandex.ru/track/60292250", "60292250"),
            Case(MusicService.YandexMusic, "https://music.yandex.ru/album/12345/track/60292250", "60292250"),
            Case(MusicService.Pandora, "https://www.pandora.com/TR:30273217", "TR:30273217"),
            Case(MusicService.AmazonMusic, "https://music.amazon.com/albums/B086Q2QNLH?trackAsin=B086Q41M9C", "B086Q41M9C"),
            Case(MusicService.AmazonMusic, "https://music.amazon.com/tracks/B086Q41M9C", "B086Q41M9C"),
            Case(MusicService.AmazonMusic, "https://www.amazon.com/music/player/tracks/B086Q41M9C", "B086Q41M9C"),
            Case(MusicService.AmazonMusic, "https://amazon.com/dp/B086Q41M9C", "B086Q41M9C"),
            Case(MusicService.Audius, "https://audius.co/tracks/3JWpV", "3JWpV"),
            Case(MusicService.Boomplay, "https://www.boomplay.com/songs/22634110", "22634110"),
            Case(MusicService.Anghami, "https://play.anghami.com/song/71730948?refer=linktree", "71730948"),
            Case(MusicService.Soundcloud, "https://soundcloud.com/tracks/718846078", "718846078"),
            Case(MusicService.MusicBrainz, "https://musicbrainz.org/recording/f32fab67-77dd-4449-9c25-3b139e67eb19", "f32fab67-77dd-4449-9c25-3b139e67eb19"),
            Case(MusicService.Qobuz, "https://open.qobuz.com/track/3147371", "3147371"),
            Case(MusicService.JioSaavn, "https://www.jiosaavn.com/song/tum-hi-ho/EToxUyFpcwQ", "EToxUyFpcwQ"),
        )
        for ((service, url, expectedId) in cases) {
            MusicServiceUtils.parseTrackId(url, service) shouldBe expectedId
        }
    }

    @Test
    fun returnsNullWhenIdIsMissingOrAmbiguous() {
        val cases = listOf(
            Case(MusicService.Spotify, ""),
            Case(MusicService.Spotify, "   "),
            Case(MusicService.Spotify, "not a url"),
            Case(MusicService.AppleMusic, "https://music.apple.com/us/album/up-on-the-ladder/1160370310"),
            Case(MusicService.Soundcloud, "https://soundcloud.com/theweeknd/blinding-lights"),
            Case(MusicService.Audiomack, "https://audiomack.com/song/the-weeknd/blinding-lights"),
            Case(MusicService.Spotify, "https://open.spotify.com/album/4yP0hdKOZPNshxUOjY0cZj"),
            Case(MusicService.AmazonMusic, "https://music.amazon.com/albums/B086Q2QNLH"),
            Case(MusicService.MusicBrainz, "https://musicbrainz.org/release/f32fab67-77dd-4449-9c25-3b139e67eb19"),
            Case(MusicService.Qobuz, "https://open.qobuz.com/album/12345"),
        )
        for ((service, url) in cases) {
            MusicServiceUtils.parseTrackId(url, service).shouldBeNull()
        }
    }

    private data class Case(
        val service: MusicService,
        val url: String,
        val expectedId: String? = null,
    )
}
