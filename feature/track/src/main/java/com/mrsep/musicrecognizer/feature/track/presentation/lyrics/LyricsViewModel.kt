package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsStyle
import com.mrsep.musicrecognizer.core.domain.preferences.ThemeMode
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.ResultNotificationManager
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.Lyrics
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration

@HiltViewModel(assistedFactory = LyricsViewModel.Factory::class)
internal class LyricsViewModel @AssistedInject constructor(
    @Assisted route: LyricsRoute,
    private val trackRepository: TrackRepository,
    private val preferencesRepository: PreferencesRepository,
    trackMetadataFetchManager: TrackMetadataFetchManager,
    private val resultNotificationManager: ResultNotificationManager,
) : ViewModel() {

    private val trackId = route.trackId

    @AssistedFactory
    interface Factory {
        fun create(route: LyricsRoute): LyricsViewModel
    }

    val uiStateStream = combine(
        flow = trackRepository.getTrackFlow(trackId),
        flow2 = preferencesRepository.userPreferencesFlow,
        flow3 = trackMetadataFetchManager.isLyricsFetcherRunning(trackId)
    ) { track, preferences, isLyricsFetcherRunning ->
        if (track == null) return@combine LyricsUiState.TrackNotFound
        if (isLyricsFetcherRunning) return@combine LyricsUiState.Loading
        track.lyrics?.let { lyrics ->
            LyricsUiState.Success(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artworkThumbUrl = track.artworkThumbUrl,
                artworkUrl = track.artworkUrl,
                lyrics = lyrics,
                trackDuration = track.duration,
                recognizedAt = track.recognizedAt,
                recognitionDate = track.recognitionDate,
                lyricsStyle = preferences.lyricsStyle,
                themeSeedColor = track.properties.themeSeedColor,
                artworkBasedThemeEnabled = preferences.artworkBasedThemeEnabled,
                themeMode = preferences.themeMode,
                usePureBlackForDarkTheme = preferences.usePureBlackForDarkTheme,
                isTrackViewed = track.properties.isViewed,
            )
        } ?: LyricsUiState.LyricsNotFound
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = LyricsUiState.Loading
        )

    fun setLyricsStyle(newStyle: LyricsStyle) {
        viewModelScope.launch {
            preferencesRepository.setLyricsStyle(newStyle)
        }
    }

    fun setTrackAsViewed(trackId: String) {
        viewModelScope.launch {
            trackRepository.setViewed(trackId, true)
            resultNotificationManager.cancelBackgroundMatches(setOf(trackId))
        }
    }

    fun setThemeSeedColor(trackId: String, color: Int?) {
        viewModelScope.launch {
            trackRepository.setThemeSeedColor(trackId, color)
        }
    }
}

@Immutable
internal sealed class LyricsUiState {

    data object Loading : LyricsUiState()

    data object TrackNotFound : LyricsUiState()

    data object LyricsNotFound : LyricsUiState()

    data class Success(
        val trackId: String,
        val title: String,
        val artist: String,
        val artworkThumbUrl: String?,
        val artworkUrl: String?,
        val lyrics: Lyrics,
        val trackDuration: Duration?,
        val recognizedAt: Duration?,
        val recognitionDate: Instant,
        val lyricsStyle: LyricsStyle,
        val themeSeedColor: Int?,
        val artworkBasedThemeEnabled: Boolean,
        val themeMode: ThemeMode,
        val usePureBlackForDarkTheme: Boolean,
        val isTrackViewed: Boolean,
    ) : LyricsUiState()
}
