package com.ainovel.audiobook.data.local.dao

import androidx.room.*
import com.ainovel.audiobook.data.local.entity.AudioTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioTrackDao {
    @Query("SELECT * FROM audio_tracks WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getTrackForChapter(chapterId: String): AudioTrackEntity?

    @Query("SELECT * FROM audio_tracks WHERE chapterId = :chapterId LIMIT 1")
    fun observeTrackForChapter(chapterId: String): Flow<AudioTrackEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioTrack(track: AudioTrackEntity)

    @Delete
    suspend fun deleteAudioTrack(track: AudioTrackEntity)

    @Query("DELETE FROM audio_tracks WHERE chapterId = :chapterId")
    suspend fun deleteTrackForChapter(chapterId: String)
}
