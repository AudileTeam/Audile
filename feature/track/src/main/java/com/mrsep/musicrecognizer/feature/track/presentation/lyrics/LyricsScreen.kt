package com.mrsep.musicrecognizer.feature.track.presentation.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrsep.musicrecognizer.core.domain.preferences.FontSize
import com.mrsep.musicrecognizer.core.domain.preferences.LyricsStyle
import com.mrsep.musicrecognizer.core.domain.preferences.ThemeMode
import com.mrsep.musicrecognizer.core.domain.track.model.PlainLyrics
import com.mrsep.musicrecognizer.core.domain.track.model.SyncedLyrics
import com.mrsep.musicrecognizer.core.ui.components.LoadingStub
import com.mrsep.musicrecognizer.feature.track.presentation.track.TrackNotFoundMessage
import com.mrsep.musicrecognizer.feature.track.presentation.track.shouldUseDarkTheme
import com.mrsep.musicrecognizer.core.ui.theme.SwitchingMusicRecognizerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsScreen(
    route: LyricsRoute,
    onBackPressed: () -> Unit,
    viewModel: LyricsViewModel = hiltViewModel<LyricsViewModel, LyricsViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    ),
) {
    val screenUiState by viewModel.uiStateStream.collectAsStateWithLifecycle()
    when (val uiState = screenUiState) {
        LyricsUiState.Loading -> Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            LyricsScreenLoadingTopBar(onBackPressed = onBackPressed)
            LoadingStub(modifier = Modifier.fillMaxSize())
        }

        LyricsUiState.TrackNotFound -> Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            LyricsScreenLoadingTopBar(onBackPressed = onBackPressed)
            TrackNotFoundMessage(modifier = Modifier.fillMaxSize())
        }

        LyricsUiState.LyricsNotFound -> Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            LyricsScreenLoadingTopBar(onBackPressed = onBackPressed)
            LyricsNotFoundMessage(modifier = Modifier.fillMaxSize())
        }

        is LyricsUiState.Success -> {
            var showLyricsStyleSheet by rememberSaveable { mutableStateOf(false) }
            val lyricsStyleSheetState = rememberModalBottomSheetState(true)
            LaunchedEffect(uiState.isTrackViewed) {
                if (!uiState.isTrackViewed) viewModel.setTrackAsViewed(uiState.trackId)
            }
            val useDarkTheme = shouldUseDarkTheme(uiState.themeMode)

            SwitchingMusicRecognizerTheme(
                seedColor = uiState.themeSeedColor?.run(::Color),
                artworkBasedThemeEnabled = uiState.artworkBasedThemeEnabled,
                useDarkTheme = useDarkTheme,
                usePureBlackForDarkTheme = uiState.usePureBlackForDarkTheme,
                highContrastMode = uiState.lyricsStyle.isHighContrast,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (val lyrics = uiState.lyrics) {
                        is PlainLyrics -> PlainLyricsContent(
                            lyrics = uiState.lyrics.plain,
                            title = uiState.title,
                            artist = uiState.artist,
                            artworkThumbUrl = uiState.artworkThumbUrl,
                            artworkUrl = uiState.artworkUrl,
                            trackDuration = uiState.trackDuration,
                            lyricsStyle = uiState.lyricsStyle,
                            lyricsTextStyle = uiState.lyricsStyle.toTextStyle(uiState.themeMode),
                            onBackPressed = onBackPressed,
                            onShowLyricsStyleSettings = { showLyricsStyleSheet = true },
                            onChangeLyricsStyle = viewModel::setLyricsStyle,
                            createSeedColor = uiState.artworkBasedThemeEnabled &&
                                    uiState.themeSeedColor == null,
                            onSeedColorCreated = { seedColor ->
                                if (seedColor == uiState.themeSeedColor) return@PlainLyricsContent
                                viewModel.setThemeSeedColor(uiState.trackId, seedColor)
                            },
                        )
                        is SyncedLyrics -> SyncedLyricsContent(
                            lyrics = lyrics,
                            title = uiState.title,
                            artist = uiState.artist,
                            artworkThumbUrl = uiState.artworkThumbUrl,
                            artworkUrl = uiState.artworkUrl,
                            trackDuration = uiState.trackDuration,
                            recognizedAt = uiState.recognizedAt,
                            recognitionDate = uiState.recognitionDate,
                            lyricsStyle = uiState.lyricsStyle,
                            lyricsTextStyle = uiState.lyricsStyle.toTextStyle(uiState.themeMode),
                            onBackPressed = onBackPressed,
                            onShowLyricsStyleSettings = { showLyricsStyleSheet = true },
                            onChangeLyricsStyle = viewModel::setLyricsStyle,
                            createSeedColor = uiState.artworkBasedThemeEnabled &&
                                    uiState.themeSeedColor == null,
                            onSeedColorCreated = { seedColor ->
                                if (seedColor == uiState.themeSeedColor) return@SyncedLyricsContent
                                viewModel.setThemeSeedColor(uiState.trackId, seedColor)
                            },
                        )
                    }
                    if (showLyricsStyleSheet) {
                        LyricsStyleBottomSheet(
                            sheetState = lyricsStyleSheetState,
                            onDismissClick = { showLyricsStyleSheet = false },
                            style = uiState.lyricsStyle,
                            onStyleChanged = viewModel::setLyricsStyle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsStyle.toTextStyle(themeMode: ThemeMode): TextStyle {
    return when (fontSize) {
        FontSize.Small -> MaterialTheme.typography.bodyMedium
        FontSize.Normal -> MaterialTheme.typography.bodyLarge
        FontSize.Large -> MaterialTheme.typography.titleLarge
        FontSize.Huge -> MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.sp)
    }.copy(
        textAlign = if (alignToStart) {
            TextAlign.Start
        } else {
            TextAlign.Center
        },
        fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (isHighContrast) {
            when (themeMode) {
                ThemeMode.FollowSystem -> if (isSystemInDarkTheme()) Color.White else Color.Black
                ThemeMode.AlwaysLight -> Color.Black
                ThemeMode.AlwaysDark -> Color.White
            }
        } else {
            Color.Unspecified
        }
    )
}
