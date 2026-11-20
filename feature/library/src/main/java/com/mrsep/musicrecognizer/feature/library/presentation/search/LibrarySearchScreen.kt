package com.mrsep.musicrecognizer.feature.library.presentation.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrsep.musicrecognizer.core.ui.components.VinylRotating
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import com.mrsep.musicrecognizer.core.strings.R as StringsR

@Composable
internal fun LibrarySearchScreen(
    onBackPressed: () -> Unit,
    onNavigateToTrack: (trackId: String) -> Unit,
    viewModel: LibrarySearchViewModel = hiltViewModel()
) {
    val topBarBehaviour = TopAppBarDefaults.pinnedScrollBehavior()
    val query by viewModel.query.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val queryState = rememberTextFieldState(query)
    val searchScope = viewModel.searchScope.collectAsStateWithLifecycle().value.toImmutableSet()
    val searchResult by viewModel.trackSearchResultFlow.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(queryState) {
        snapshotFlow { queryState.text.toString() }
            .collect { viewModel.submitSearchQuery(it) }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun clearFocusAndCloseSearch() {
        focusManager.clearFocus()
        onBackPressed()
    }

    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
    ) {
        SearchScreenTopBar(
            queryState = queryState,
            onBackPressed = ::clearFocusAndCloseSearch,
            scrollBehavior = topBarBehaviour,
            searchScope = searchScope,
            onSearchScopeChanged = viewModel::submitSearchScope,
            focusRequester = focusRequester
        )
        SearchResultLazyColumn(
            query = query,
            searchResult = searchResult,
            onTrackClick = onNavigateToTrack,
            topBarBehaviour = topBarBehaviour,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SearchResultLazyColumn(
    query: String,
    searchResult: SearchResultUi,
    onTrackClick: (trackId: String) -> Unit,
    topBarBehaviour: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = searchResult,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 90)))
                .togetherWith(fadeOut(animationSpec = tween(90)))
        },
        contentAlignment = Alignment.TopCenter,
        label = "SearchResult",
        modifier = modifier,
    ) { result ->
        when (result) {
            is SearchResultUi.Pending -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.surface)
                    .fillMaxSize()
            ) {
                VinylRotating(modifier = Modifier.size(32.dp))
            }

            is SearchResultUi.Success -> Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedVisibility(
                    visible = result.query == query,
                    enter = fadeIn(spring()),
                    exit = fadeOut(spring()),
                    label = "SearchResult"
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(topBarBehaviour.nestedScrollConnection),
                    ) {
                        itemsIndexed(
                            items = result.data,
                            key = { _, track -> track.id }
                        ) { index, track ->
                            Column(modifier = Modifier.animateItem()) {
                                TrackSearchItem(
                                    track = track,
                                    query = result.query,
                                    searchScope = result.searchScope,
                                    onClick = { onTrackClick(track.id) },
                                    contentPadding = PaddingValues(
                                        vertical = 5.dp,
                                        horizontal = 12.dp
                                    )
                                )
                                if (index != result.data.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.alpha(0.2f))
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = result.isEmpty && result.query.isNotBlank(),
                    enter = fadeIn(spring()),
                    exit = fadeOut(spring()),
                    label = "NotFoundMessage"
                ) {
                    Box(
                        modifier = Modifier
                            .background(color = MaterialTheme.colorScheme.surface)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(StringsR.string.no_tracks_match_search),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
