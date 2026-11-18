package com.mrsep.musicrecognizer.feature.track.presentation.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.domain.preferences.ThemeMode
import com.mrsep.musicrecognizer.core.domain.preferences.UserPreferences
import com.mrsep.musicrecognizer.core.domain.preferences.PreferencesRepository
import com.mrsep.musicrecognizer.core.domain.recognition.ResultNotificationManager
import com.mrsep.musicrecognizer.core.domain.recognition.TrackMetadataFetchManager
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.MusicService
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.domain.usecase.DeleteTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.annotation.concurrent.Immutable

@HiltViewModel(assistedFactory = TrackViewModel.Factory::class)
internal class TrackViewModel @AssistedInject constructor(
    @Assisted route: TrackRoute,
    preferencesRepository: PreferencesRepository,
    private val trackRepository: TrackRepository,
    trackMetadataFetchManager: TrackMetadataFetchManager,
    private val deleteTrackUseCase: DeleteTrack,
    private val resultNotificationManager: ResultNotificationManager,
) : ViewModel() {

    private val trackId = route.trackId

    @AssistedFactory
    interface Factory {
        fun create(route: TrackRoute): TrackViewModel
    }

    private var trackRemovalRequested = false

    private val _trackExistingState = MutableStateFlow(true)
    val trackExistingState = _trackExistingState.asStateFlow()

    val uiStateStream = combine(
        trackRepository.getTrackFlow(trackId),
        preferencesRepository.userPreferencesFlow,
        trackMetadataFetchManager.isTrackLinksFetcherRunning(trackId),
        trackMetadataFetchManager.isLyricsFetcherRunning(trackId),
    ) { track, preferences, isTrackLinksFetcherRunning, isLyricsFetcherRunning ->
        track?.toUiState(preferences, isTrackLinksFetcherRunning, isLyricsFetcherRunning)
            ?: TrackUiState.TrackNotFound
    }.transformWhile { uiState ->
        // do not update track state if track removal was requested
        // after track deletion and final animation, the screen should be destroyed
        if (!trackRemovalRequested) emit(uiState)
        !trackRemovalRequested
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TrackUiState.Loading
    )

    fun deleteTrack(trackId: String) {
        trackRemovalRequested = true
        viewModelScope.launch {
            deleteTrackUseCase(listOf(trackId))
            _trackExistingState.update { false }
        }
    }

    fun setFavorite(trackId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            trackRepository.setFavorite(trackId, isFavorite)
        }
    }

    fun setThemeSeedColor(trackId: String, color: Int?) {
        viewModelScope.launch {
            trackRepository.setThemeSeedColor(trackId, color)
        }
    }

    fun setTrackAsViewed(trackId: String) {
        viewModelScope.launch {
            trackRepository.setViewed(trackId, true)
            resultNotificationManager.cancelBackgroundMatches(setOf(trackId))
        }
    }
}

@Immutable
internal sealed class TrackUiState {

    data object Loading : TrackUiState()

    data object TrackNotFound : TrackUiState()

    data class Success(
        val track: TrackUi,
        val isTrackViewed: Boolean,
        val isTrackLinksFetcherRunning: Boolean,
        val isLyricsFetcherRunning: Boolean,
        val requiredServices: ImmutableList<MusicService>,
        val artworkBasedThemeEnabled: Boolean,
        val themeMode: ThemeMode,
        val usePureBlackForDarkTheme: Boolean,
    ) : TrackUiState()
}

private fun Track.toUiState(
    preferences: UserPreferences,
    isTrackLinksFetcherRunning: Boolean,
    isLyricsFetcherRunning: Boolean,
): TrackUiState.Success {
    val trackUi = this.toUi(preferences.requiredMusicServices)
    return TrackUiState.Success(
        track = this.toUi(preferences.requiredMusicServices),
        isTrackViewed = this.properties.isViewed,
        isTrackLinksFetcherRunning = isTrackLinksFetcherRunning &&
                trackUi.trackLinks.size < preferences.requiredMusicServices.size,
        isLyricsFetcherRunning = isLyricsFetcherRunning,
        requiredServices = preferences.requiredMusicServices.toImmutableList(),
        artworkBasedThemeEnabled = preferences.artworkBasedThemeEnabled,
        themeMode = preferences.themeMode,
        usePureBlackForDarkTheme = preferences.usePureBlackForDarkTheme,
    )
}
