package com.ainovel.audiobook.data.repository

import com.ainovel.audiobook.data.local.dao.VoicePresetDao
import com.ainovel.audiobook.data.local.entity.VoicePresetEntity
import kotlinx.coroutines.flow.Flow

interface VoiceRepository {
    fun getAllPresets(): Flow<List<VoicePresetEntity>>
    suspend fun getAllPresetsList(): List<VoicePresetEntity>
    fun getPresetsByRegion(region: String): Flow<List<VoicePresetEntity>>
    fun getPresetsByGender(gender: String): Flow<List<VoicePresetEntity>>
    fun getFavoritePresets(): Flow<List<VoicePresetEntity>>
    suspend fun getPreset(id: String): VoicePresetEntity?
    suspend fun savePreset(preset: VoicePresetEntity)
    suspend fun updateSampleUri(id: String, sampleUri: String)
    suspend fun toggleFavorite(id: String, isFavorite: Boolean)
    suspend fun deletePreset(id: String)
}

class VoiceRepositoryImpl(
    private val voicePresetDao: VoicePresetDao
) : VoiceRepository {

    override fun getAllPresets(): Flow<List<VoicePresetEntity>> =
        voicePresetDao.getAllPresets()

    override suspend fun getAllPresetsList(): List<VoicePresetEntity> =
        voicePresetDao.getAllPresetsList()

    override fun getPresetsByRegion(region: String): Flow<List<VoicePresetEntity>> =
        voicePresetDao.getPresetsByRegion(region)

    override fun getPresetsByGender(gender: String): Flow<List<VoicePresetEntity>> =
        voicePresetDao.getPresetsByGender(gender)

    override fun getFavoritePresets(): Flow<List<VoicePresetEntity>> =
        voicePresetDao.getFavoritePresets()

    override suspend fun getPreset(id: String): VoicePresetEntity? =
        voicePresetDao.getPresetById(id)

    override suspend fun savePreset(preset: VoicePresetEntity) =
        voicePresetDao.insertPreset(preset)

    override suspend fun updateSampleUri(id: String, sampleUri: String) =
        voicePresetDao.updateSampleAudioUri(id, sampleUri)

    override suspend fun toggleFavorite(id: String, isFavorite: Boolean) =
        voicePresetDao.setFavorite(id, isFavorite)

    override suspend fun deletePreset(id: String) =
        voicePresetDao.deletePresetById(id)
}
