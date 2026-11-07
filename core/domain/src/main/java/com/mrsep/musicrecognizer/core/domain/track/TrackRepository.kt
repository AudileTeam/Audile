package com.mrsep.musicrecognizer.core.domain.track

import com.mrsep.musicrecognizer.core.domain.preferences.FavoritesMode
import com.mrsep.musicrecognizer.core.domain.preferences.TrackFilter
import com.mrsep.musicrecognizer.core.domain.recognition.model.NetworkResult
import com.mrsep.musicrecognizer.core.domain.track.model.SearchResult
import com.mrsep.musicrecognizer.core.domain.track.model.Track
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import com.mrsep.musicrecognizer.core.domain.track.model.TrackPreview
import kotlinx.coroutines.flow.Flow

interface TrackRepository {

    suspend fun upsertFromRecognition(track: Track): Track

    suspend fun updateTransform(trackId: String, transform: (previous: Track) -> Track)

    suspend fun setThemeSeedColor(trackId: String, color: Int?)

    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    suspend fun setViewed(trackId: String, isViewed: Boolean)

    suspend fun setAllViewed()

    suspend fun delete(trackIds: List<String>)

    suspend fun deleteAll()

    fun isEmptyFlow(): Flow<Boolean>

    fun getUnviewedCountFlow(): Flow<Int>

    fun getTrackFlow(trackId: String): Flow<Track?>

    fun getPreviewsByFilterFlow(filter: TrackFilter): Flow<List<TrackPreview>>

    fun getSearchResultFlow(query: String, searchScope: Set<TrackDataField>): Flow<SearchResult>

    fun getTracksFlow(favoritesMode: FavoritesMode): Flow<List<Track>>

    suspend fun fetchAndUpdateLyrics(trackId: String): NetworkResult<Unit>
}
