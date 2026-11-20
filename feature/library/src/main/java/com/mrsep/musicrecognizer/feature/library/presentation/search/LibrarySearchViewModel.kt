package com.mrsep.musicrecognizer.feature.library.presentation.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrsep.musicrecognizer.core.common.di.DefaultDispatcher
import com.mrsep.musicrecognizer.core.common.util.AppDateTimeFormatter
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
internal class LibrarySearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val trackRepository: TrackRepository,
    private val dateTimeFormatter: AppDateTimeFormatter,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchScope = savedStateHandle.getStateFlow(
        key = KEY_SEARCH_SCOPE,
        initialValue = setOf(
            TrackDataField.Title,
            TrackDataField.Artist,
        )
    )

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val trackSearchResultFlow: StateFlow<SearchResultUi> = query
        .map { query -> if (query.length >= SEARCH_QUERY_MIN_LENGTH) query else "" }
        .debounce(SEARCH_INPUT_DEBOUNCE)
        .distinctUntilChanged()
        .combine(searchScope) { query, searchScope -> query to searchScope }
        .flatMapLatest { (query, searchScope) ->
            if (query.isBlank()) {
                flowOf(
                    SearchResultUi.Success(
                        query = "",
                        searchScope = searchScope.toImmutableSet(),
                        data = persistentListOf()
                    )
                )
            } else {
                trackRepository.getSearchResultFlow(query, searchScope)
                    .map { result -> result.toUi(dateTimeFormatter) }
            }
        }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchResultUi.Success(
                query = "",
                searchScope = persistentSetOf(
                    TrackDataField.Title,
                    TrackDataField.Artist,
                ),
                data = persistentListOf()
            )
        )

    fun submitSearchQuery(query: String) {
        if (query.length > SEARCH_QUERY_MAX_LENGTH) return
        _query.value = query
    }

    fun submitSearchScope(searchScope: Set<TrackDataField>) {
        savedStateHandle[KEY_SEARCH_SCOPE] = searchScope
    }

    companion object {
        private val SEARCH_INPUT_DEBOUNCE = 400.milliseconds
        private const val SEARCH_QUERY_MIN_LENGTH = 2
        internal const val SEARCH_QUERY_MAX_LENGTH = 30
        private const val KEY_SEARCH_SCOPE = "KEY_SEARCH_SCOPE"
    }
}
