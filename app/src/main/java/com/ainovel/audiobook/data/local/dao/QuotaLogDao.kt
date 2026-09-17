package com.ainovel.audiobook.data.local.dao

import androidx.room.*
import com.ainovel.audiobook.data.local.entity.QuotaLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotaLogDao {
    @Query("SELECT * FROM quota_logs ORDER BY lockedAt DESC")
    fun getAllQuotaLogs(): Flow<List<QuotaLogEntity>>

    @Query("SELECT * FROM quota_logs WHERE modelId = :modelId AND keyHash = :keyHash AND (expiresAt > :now OR isPermanent = 1) LIMIT 1")
    suspend fun getActiveLock(modelId: String, keyHash: String, now: Long = System.currentTimeMillis()): QuotaLogEntity?

    @Query("SELECT * FROM quota_logs WHERE (expiresAt > :now OR isPermanent = 1)")
    suspend fun getAllActiveLocks(now: Long = System.currentTimeMillis()): List<QuotaLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotaLog(log: QuotaLogEntity)

    @Query("DELETE FROM quota_logs WHERE expiresAt <= :now AND isPermanent = 0")
    suspend fun purgeExpiredLocks(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM quota_logs WHERE modelId = :modelId AND keyHash = :keyHash")
    suspend fun removeLock(modelId: String, keyHash: String)

    @Query("DELETE FROM quota_logs")
    suspend fun clearAllLocks()
}
