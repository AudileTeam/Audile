package com.mrsep.musicrecognizer.feature.library.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrsep.musicrecognizer.core.domain.preferences.TrackFilter
import com.mrsep.musicrecognizer.core.ui.components.LoadingStub
import com.mrsep.musicrecognizer.core.ui.components.rememberMultiSelectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    onTrackClick: (trackId: String) -> Unit,
    onTrackSearchClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val screenUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val multiSelectionState = rememberMultiSelectionState<String>(screenUiState)
    var showFilterBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable(screenUiState) { mutableStateOf(false) }
    var deletionInProgress by rememberSaveable(screenUiState) { mutableStateOf(false) }
    val topBarBehaviour = TopAppBarDefaults.pinnedScrollBehavior()

    BackHandler(
        enabled = multiSelectionState.hasSelected,
        onBack = multiSelectionState::deselectAll
    )

    when (val uiState = screenUiState) {
        LibraryUiState.Loading -> Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .fillMaxSize()
        ) {
            LibraryScreenLoadingTopBar(scrollBehavior = topBarBehaviour)
            LoadingStub(modifier = Modifier.fillMaxSize())
        }

        is LibraryUiState.Success -> Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.surface)
                .fillMaxSize()
        ) {
            LibraryScreenTopBar(
                isLibraryEmpty = uiState.isEmptyLibrary,
                isFilterApplied = uiState.trackFilter.isDefault.not(),
                isMultiselectEnabled = multiSelectionState.hasSelected,
                selectedCount = multiSelectionState.selectedCount,
                totalCount = uiState.trackList.size,
                onSearchClick = onTrackSearchClick,
                onFilterClick = { showFilterBottomSheet = !showFilterBottomSheet },
                onDeleteClick = { showDeleteDialog = true },
                onSelectAll = { multiSelectionState.select(uiState.trackList.map { it.id }) },
                onDeselectAll = multiSelectionState::deselectAll,
                useGridLayout = uiState.useGridLayout,
                onChangeUseGridLayout = viewModel::setUseGrid,
                showRecognitionDate = uiState.showRecognitionDate,
                onChangeShowRecognitionDate = viewModel::setShowRecognitionDate,
                onSetAllViewed = viewModel::setAllViewed,
                scrollBehavior = topBarBehaviour
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                var newFilterApplied by rememberSaveable { mutableStateOf(false) }
                AnimatedContent(
                    targetState = uiState.useGridLayout,
                    label = "Layout"
                ) { useGrid ->
                    if (useGrid) {
                        val lazyGridState = rememberLazyGridState()
                        TrackLazyGrid(
                            trackList = uiState.trackList,
                            onTrackClick = onTrackClick,
                            lazyGridState = lazyGridState,
                            multiSelectionState = multiSelectionState,
                            showRecognitionDate = uiState.showRecognitionDate,
                            modifier = Modifier
                                .nestedScroll(topBarBehaviour.nestedScrollConnection)
                                .fillMaxSize()
                        )
                        LaunchedEffect(uiState.trackFilter) {
                            if (newFilterApplied) {
                                lazyGridState.animateScrollToItem(0)
                                topBarBehaviour.state.contentOffset = 0f
                                newFilterApplied = false
                            }
                        }
                    } else {
                        val lazyListState = rememberLazyListState()
                        TrackLazyColumn(
                            trackList = uiState.trackList,
                            onTrackClick = onTrackClick,
                            lazyListState = lazyListState,
                            multiSelectionState = multiSelectionState,
                            showRecognitionDate = uiState.showRecognitionDate,
                            modifier = Modifier
                                .nestedScroll(topBarBehaviour.nestedScrollConnection)
                                .fillMaxSize()
                        )
                        LaunchedEffect(uiState.trackFilter) {
                            if (newFilterApplied) {
                                lazyListState.animateScrollToItem(0)
                                topBarBehaviour.state.contentOffset = 0f
                                newFilterApplied = false
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !uiState.isEmptyLibrary && uiState.trackList.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    NoFilteredTracksMessage(
                        modifier = Modifier
                            .background(color = MaterialTheme.colorScheme.surface)
                            .fillMaxSize()
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.isEmptyLibrary,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    EmptyLibraryMessage(
                        modifier = Modifier
                            .background(color = MaterialTheme.colorScheme.surface)
                            .fillMaxSize()
                    )
                }

                val filterSheetState = rememberModalBottomSheetState(true)

                fun hideFilterSheet(newTrackFilter: TrackFilter) {
                    scope.launch { filterSheetState.hide() }.invokeOnCompletion {
                        if (!filterSheetState.isVisible) {
                            showFilterBottomSheet = false
                        }
                        newFilterApplied = true
                        viewModel.applyFilter(newTrackFilter)
                    }
                }
                if (showFilterBottomSheet) {
                    val filterState = rememberTrackFilterState(uiState.trackFilter)
                    TrackFilterBottomSheet(
                        sheetState = filterSheetState,
                        filterState = filterState,
                        onDismissRequest = { showFilterBottomSheet = false },
                        onApplyClick = {
                            val newFilter = filterState.makeFilter()
                            hideFilterSheet(newFilter)
                        }
                    )
                }

                if (showDeleteDialog) {
                    DeleteSelectedDialog(
                        onDeleteClick = {
                            deletionInProgress = true
                            val selectedIds = multiSelectionState.getSelected()
                            viewModel.deleteTracks(selectedIds)
                        },
                        onDismissClick = { showDeleteDialog = false },
                        inProgress = deletionInProgress
                    )
                }
            }
        }
    }
}
