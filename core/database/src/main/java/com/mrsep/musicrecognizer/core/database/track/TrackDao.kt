package com.mrsep.musicrecognizer.core.database.track

import androidx.room.*
import com.mrsep.musicrecognizer.core.database.DatabaseUtils
import com.mrsep.musicrecognizer.core.database.DatabaseUtils.eachDbChunk
import com.mrsep.musicrecognizer.core.database.SQLSearchPattern
import com.mrsep.musicrecognizer.core.domain.preferences.FavoritesMode
import com.mrsep.musicrecognizer.core.domain.preferences.OrderBy
import com.mrsep.musicrecognizer.core.domain.preferences.SortBy
import com.mrsep.musicrecognizer.core.domain.track.model.TrackDataField
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Update
    suspend fun update(track: TrackEntity)

    @Transaction
    suspend fun upsertFromRecognition(tracks: List<TrackEntity>): List<TrackEntity> {
        val merged = tracks.map { newTrack ->
            val keptProps = getTrack(newTrack.id)?.properties ?: newTrack.properties
            newTrack.copy(properties = keptProps.copy(isViewed = false))
        }
        upsert(merged)
        return merged
    }

    @Transaction
    suspend fun upsertFromRecognition(track: TrackEntity): TrackEntity {
        val kept = getTrack(track.id)?.properties ?: track.properties
        val merged = track.copy(properties = kept.copy(isViewed = false))
        upsert(merged)
        return merged
    }

    @Transaction
    suspend fun updateTransform(
        trackId: String,
        transform: (previous: TrackEntity) -> TrackEntity,
    ) {
        val previous = getTrack(trackId) ?: return
        val transformed = transform(previous)
        check(previous.id == transformed.id)
        update(transformed)
    }

    @Transaction
    suspend fun delete(trackIds: List<String>) {
        trackIds.eachDbChunk(::deleteInternal)
    }

    @Query("DELETE FROM track WHERE id IN (:trackIds)")
    suspend fun deleteInternal(trackIds: List<String>)

    @Query("DELETE FROM track")
    suspend fun deleteAll()

    @Query("UPDATE track SET is_favorite = :isFavorite WHERE id = :trackId AND is_favorite IS NOT :isFavorite")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE track SET is_viewed = :isViewed WHERE id = :trackId AND is_viewed IS NOT :isViewed")
    suspend fun setViewed(trackId: String, isViewed: Boolean)

    @Query("UPDATE track SET is_viewed = 1 WHERE is_viewed = 0")
    suspend fun setAllViewed()

    @Query("UPDATE track SET theme_seed_color = :color WHERE id = :trackId AND theme_seed_color IS NOT :color")
    suspend fun setThemeSeedColor(trackId: String, color: Int?)

    @Query("UPDATE track SET lyrics = :lyrics, is_lyrics_synced = :isSynced WHERE id = :trackId")
    suspend fun setLyrics(trackId: String, lyrics: String, isSynced: Boolean)

    @Query("SELECT NOT EXISTS(SELECT 1 FROM track LIMIT 1)")
    fun isEmptyDatabaseFlow(): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM track WHERE is_viewed = 0")
    fun getUnviewedCountFlow(): Flow<Int>

    @Query("SELECT * FROM track WHERE id = :trackId")
    suspend fun getTrack(trackId: String): TrackEntity?

    @Query("SELECT * FROM track WHERE id = :trackId")
    fun getTrackFlow(trackId: String): Flow<TrackEntity?>

    fun getPreviewsFlowByQuery(
        query: String,
        searchScope: Set<TrackDataField>
    ): Flow<List<TrackPreviewTuple>> {
        return getPreviewsFlowByPattern(
            pattern = DatabaseUtils.createSearchPatternForSQLite(query),
            escapeSymbol = DatabaseUtils.ESCAPE_SYMBOL,
            searchScope
        )
    }

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
            SELECT * FROM track WHERE 
                CASE 
                    WHEN 'Title' IN (:searchScope) 
                    THEN title LIKE :pattern ESCAPE :escapeSymbol 
                    ELSE 0 
                END 
                OR CASE 
                    WHEN 'Artist' IN (:searchScope) 
                    THEN artist LIKE :pattern ESCAPE :escapeSymbol 
                    ELSE 0 
                END 
                OR CASE 
                    WHEN 'Album' IN (:searchScope) 
                    THEN album LIKE :pattern ESCAPE :escapeSymbol 
                    ELSE 0 
                END 
            ORDER BY recognition_date DESC
        """
    )
    fun getPreviewsFlowByPattern(
        pattern: SQLSearchPattern,
        escapeSymbol: String,
        searchScope: Set<TrackDataField>
    ): Flow<List<TrackPreviewTuple>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
            SELECT * FROM track
            WHERE
                -- Favorites filter
                CASE :favoritesMode
                    WHEN 'All' THEN 1
                    WHEN 'OnlyFavorites' THEN is_favorite
                    WHEN 'ExcludeFavorites' THEN NOT is_favorite
                END
                AND
                -- Date range filter
                CASE :applyDateFilter
                    WHEN 0 THEN 1
                    WHEN 1 THEN recognition_date BETWEEN :startDate AND :endDate
                END
            ORDER BY
                -- Sort and order
                CASE WHEN :orderBy = 'Asc' AND :sortBy = 'RecognitionDate' THEN recognition_date END ASC,
                CASE WHEN :orderBy = 'Asc' AND :sortBy = 'ReleaseDate' THEN release_date END ASC,
                CASE WHEN :orderBy = 'Asc' AND :sortBy = 'Title' THEN title END COLLATE LOCALIZED ASC,
                CASE WHEN :orderBy = 'Asc' AND :sortBy = 'Artist' THEN artist END COLLATE LOCALIZED ASC,

                CASE WHEN :orderBy = 'Desc' AND :sortBy = 'RecognitionDate' THEN recognition_date END DESC,
                CASE WHEN :orderBy = 'Desc' AND :sortBy = 'ReleaseDate' THEN release_date END DESC,
                CASE WHEN :orderBy = 'Desc' AND :sortBy = 'Title' THEN title END COLLATE LOCALIZED DESC,
                CASE WHEN :orderBy = 'Desc' AND :sortBy = 'Artist' THEN artist END COLLATE LOCALIZED DESC
        """
    )
    fun getPreviewsFlowByFilter(
        favoritesMode: FavoritesMode,
        startDate: Long,
        endDate: Long,
        applyDateFilter: Boolean = (startDate != Long.MIN_VALUE || endDate != Long.MAX_VALUE),
        sortBy: SortBy,
        orderBy: OrderBy
    ): Flow<List<TrackPreviewTuple>>

    @Query(
        """
            SELECT * FROM track 
            WHERE
                CASE :favoritesMode
                    WHEN 'All' THEN 1
                    WHEN 'OnlyFavorites' THEN is_favorite
                    WHEN 'ExcludeFavorites' THEN NOT is_favorite
                END
            ORDER BY recognition_date DESC
        """
    )
    fun getTracksFlow(favoritesMode: FavoritesMode): Flow<List<TrackEntity>>
}
