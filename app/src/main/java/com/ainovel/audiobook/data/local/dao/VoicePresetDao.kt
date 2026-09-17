package com.ainovel.audiobook.data.local.dao

import androidx.room.*
import com.ainovel.audiobook.data.local.entity.VoicePresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoicePresetDao {
    @Query("SELECT * FROM voice_presets ORDER BY isFavorite DESC, name ASC")
    fun getAllPresets(): Flow<List<VoicePresetEntity>>

    @Query("SELECT * FROM voice_presets ORDER BY isFavorite DESC, name ASC")
    suspend fun getAllPresetsList(): List<VoicePresetEntity>

    @Query("SELECT * FROM voice_presets WHERE region = :region ORDER BY name ASC")
    fun getPresetsByRegion(region: String): Flow<List<VoicePresetEntity>>

    @Query("SELECT * FROM voice_presets WHERE gender = :gender ORDER BY name ASC")
    fun getPresetsByGender(gender: String): Flow<List<VoicePresetEntity>>

    @Query("SELECT * FROM voice_presets WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoritePresets(): Flow<List<VoicePresetEntity>>

    @Query("SELECT * FROM voice_presets WHERE id = :id LIMIT 1")
    suspend fun getPresetById(id: String): VoicePresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: VoicePresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<VoicePresetEntity>)

    @Query("UPDATE voice_presets SET sampleAudioUri = :uri WHERE id = :id")
    suspend fun updateSampleAudioUri(id: String, uri: String)

    @Query("UPDATE voice_presets SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Delete
    suspend fun deletePreset(preset: VoicePresetEntity)

    @Query("DELETE FROM voice_presets WHERE id = :id")
    suspend fun deletePresetById(id: String)
}
