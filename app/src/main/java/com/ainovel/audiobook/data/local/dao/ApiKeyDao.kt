package com.ainovel.audiobook.data.local.dao

import androidx.room.*
import com.ainovel.audiobook.data.local.entity.ApiKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY createdAt DESC")
    fun getAllKeys(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE isActive = 1")
    suspend fun getActiveKeys(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE keyHash = :keyHash LIMIT 1")
    suspend fun getKeyByHash(keyHash: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: ApiKeyEntity)

    @Update
    suspend fun updateKey(key: ApiKeyEntity)

    @Delete
    suspend fun deleteKey(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE keyHash = :keyHash")
    suspend fun deleteKeyByHash(keyHash: String)
}
