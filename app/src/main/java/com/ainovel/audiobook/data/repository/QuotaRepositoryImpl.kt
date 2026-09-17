package com.ainovel.audiobook.data.repository

import com.ainovel.audiobook.data.local.dao.QuotaLogDao
import com.ainovel.audiobook.data.local.entity.QuotaLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface QuotaRepository {
    fun getAllQuotaLogs(): Flow<List<QuotaLogEntity>>
    suspend fun isModelKeyLocked(modelId: String, keyHash: String): Boolean
    suspend fun lockModelKey(
        modelId: String,
        keyHash: String,
        errorStatus: Int,
        lockType: String,
        cooldownMs: Long,
        isPermanent: Boolean = false
    )
    suspend fun releaseLock(modelId: String, keyHash: String)
    suspend fun purgeExpiredLocks(): Int
    suspend fun clearAllLocks()
}

class QuotaRepositoryImpl(
    private val quotaLogDao: QuotaLogDao
) : QuotaRepository {

    override fun getAllQuotaLogs(): Flow<List<QuotaLogEntity>> = quotaLogDao.getAllQuotaLogs()

    override suspend fun isModelKeyLocked(modelId: String, keyHash: String): Boolean {
        return quotaLogDao.getActiveLock(modelId, keyHash) != null
    }

    override suspend fun lockModelKey(
        modelId: String,
        keyHash: String,
        errorStatus: Int,
        lockType: String,
        cooldownMs: Long,
        isPermanent: Boolean
    ) {
        val now = System.currentTimeMillis()
        val entity = QuotaLogEntity(
            id = UUID.randomUUID().toString(),
            modelId = modelId,
            keyHash = keyHash,
            errorStatus = errorStatus,
            lockType = lockType,
            lockedAt = now,
            expiresAt = now + cooldownMs,
            isPermanent = isPermanent
        )
        quotaLogDao.insertQuotaLog(entity)
    }

    override suspend fun releaseLock(modelId: String, keyHash: String) {
        quotaLogDao.removeLock(modelId, keyHash)
    }

    override suspend fun purgeExpiredLocks(): Int {
        return quotaLogDao.purgeExpiredLocks()
    }

    override suspend fun clearAllLocks() {
        quotaLogDao.clearAllLocks()
    }
}
