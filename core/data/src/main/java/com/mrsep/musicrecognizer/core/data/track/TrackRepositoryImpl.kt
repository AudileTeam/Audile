package com.mrsep.musicrecognizer.core.data.track

import com.mrsep.musicrecognizer.core.common.di.ApplicationScope
import com.mrsep.musicrecognizer.core.common.di.IoDispatcher
import com.mrsep.musicrecognizer.core.data.PersistentStoreLock
import com.mrsep.musicrecognizer.core.database.ApplicationDatabase
import com.mrsep.musicrecognizer.core.database.track.TrackEntity
import com.mrsep.musicrecognizer.core.database.track.TrackPreviewTuple
import com.mrsep.musicrecognizer.core.domain.preferences.FavoritesMode
import com.mrsep.musicrecognizer.core.domain.preferences.TrackFilter
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkError
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.TrackRepository
import com.mrsep.musicrecognizer.core.domain.track.model.SearchResult
import com.mrsep.musicrecognizer.core.domain.track.model.SyncedLyrics
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import com.mrsep.musicrecognizer.core.domain.track.model.TrackPreview
import com.mrsep.musicrecognizer.core.metadata.lyrics.LyricsFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.collections.map

internal class TrackRepositoryImpl @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val lyricsFetcher: LyricsFetcher,
    private val storeLock: PersistentStoreLock,
    database: ApplicationDatabase
) : TrackRepository {

    private val trackDao = database.trackDao()
    private val persistentCoroutineContext = appScope.coroutineContext + ioDispatcher

    override suspend fun upsertFromRecognition(track: Track): Track {
        return withStoreWrite {
            trackDao.upsertFromRecognition(track.toEntity()).toDomain()
        }
    }

    override suspend fun updateTransform(trackId: String, transform: (previous: Track) -> Track) {
        withStoreWrite {
            trackDao.updateTransform(trackId) { trackEntity ->
                transform(trackEntity.toDomain()).toEntity()
            }
        }
    }

    override suspend fun setFavorite(trackId: String, isFavorite: Boolean) {
        withStoreWrite {
            trackDao.setFavorite(trackId, isFavorite)
        }
    }

    override suspend fun setViewed(trackId: String, isViewed: Boolean) {
        withStoreWrite {
            trackDao.setViewed(trackId, isViewed)
        }
    }

    override suspend fun setAllViewed() {
        withStoreWrite {
            trackDao.setAllViewed()
        }
    }

    override suspend fun setThemeSeedColor(trackId: String, color: Int?) {
        withStoreWrite {
            trackDao.setThemeSeedColor(trackId, color)
        }
    }

    override suspend fun delete(trackIds: List<String>) {
        withStoreWrite {
            trackDao.delete(trackIds)
        }
    }

    override suspend fun deleteAll() {
        withStoreWrite {
            trackDao.deleteAll()
        }
    }

    override fun isEmptyFlow(): Flow<Boolean> {
        return trackDao.isEmptyDatabaseFlow()
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    override fun getUnviewedCountFlow(): Flow<Int> {
        return trackDao.getUnviewedCountFlow()
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    override fun getTrackFlow(trackId: String): Flow<Track?> {
        return trackDao.getTrackFlow(trackId)
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)
    }

    override fun getPreviewsByFilterFlow(
        filter: TrackFilter
    ): Flow<List<TrackPreview>> {
        return trackDao.getPreviewsFlowByFilter(
            favoritesMode = filter.favoritesMode,
            startDate = filter.dateRange.first,
            endDate = filter.dateRange.last,
            sortBy = filter.sortBy,
            orderBy = filter.orderBy
        )
            .map { list -> list.map(TrackPreviewTuple::toDomain) }
            .flowOn(ioDispatcher)
    }

    override fun getSearchResultFlow(
        query: String,
        searchScope: Set<TrackDataField>
    ): Flow<SearchResult> {
        return trackDao.getPreviewsFlowByQuery(query, searchScope)
            .map<List<TrackPreviewTuple>, SearchResult> { list ->
                SearchResult.Success(
                    query = query,
                    searchScope = searchScope,
                    data = list.map(TrackPreviewTuple::toDomain)
                )
            }
            .onStart { emit(SearchResult.Pending(query, searchScope)) }
            .flowOn(ioDispatcher)
    }

    override fun getTracksFlow(favoritesMode: FavoritesMode): Flow<List<Track>> {
        return trackDao.getTracksFlow(favoritesMode)
            .map { list -> list.map(TrackEntity::toDomain) }
            .flowOn(ioDispatcher)
    }

    override suspend fun fetchAndUpdateLyrics(
        trackId: String
    ): NetworkResult<Unit> = withContext(ioDispatcher) {
        // Null if track was not found or was deleted in process
        val track = getTrackFlow(trackId).first() ?: return@withContext NetworkResult.Success(Unit)

        when (val result = lyricsFetcher.fetch(track)) {
            is NetworkResult.Success -> {
                result.data?.let { lyrics ->
                    withStoreWrite {
                        trackDao.setLyrics(
                            trackId,
                            lyrics = lyrics.toDbLyricsData(),
                            isSynced = lyrics is SyncedLyrics
                        )
                    }
                }
                NetworkResult.Success(Unit)
            }
            is NetworkError -> result
        }
    }

    private suspend fun <T> withStoreWrite(block: suspend () -> T): T =
        withContext(persistentCoroutineContext) {
            storeLock.withShared(block)
        }
}
