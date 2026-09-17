package com.ainovel.audiobook.domain.engine

import com.ainovel.audiobook.data.local.dao.ApiKeyDao
import com.ainovel.audiobook.data.repository.QuotaRepository
import com.ainovel.audiobook.data.security.SecureKeyStorage
import com.ainovel.audiobook.domain.model.ModelConfig
import com.ainovel.audiobook.domain.model.RotationCandidate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class RotationManager(
    private val apiKeyDao: ApiKeyDao,
    private val secureKeyStorage: SecureKeyStorage,
    private val quotaRepository: QuotaRepository,
    private var models: List<ModelConfig> = ModelConfig.DEFAULT_MODELS
) {
    private val mutex = Mutex()
    private val inMemoryCooldowns = ConcurrentHashMap<String, Long>()

    fun setModels(newModels: List<ModelConfig>) {
        models = newModels.sortedBy { it.priority }
    }

    fun getModels(): List<ModelConfig> = models

    /**
     * Elects the next candidate based on Model-First, Key-Second logic.
     * Iterates through the highest priority model and all its available keys before
     * moving to the next priority model.
     */
    suspend fun electCandidate(): RotationCandidate? = mutex.withLock {
        quotaRepository.purgeExpiredLocks()
        val now = System.currentTimeMillis()

        // Clean expired in-memory cooldowns
        inMemoryCooldowns.entries.removeIf { it.value <= now }

        val activeKeys = apiKeyDao.getActiveKeys()
        if (activeKeys.isEmpty()) return null

        val sortedModels = models.sortedBy { it.priority }

        for (model in sortedModels) {
            for (keyEntity in activeKeys) {
                val pairKey = "${model.id}::${keyEntity.keyHash}"

                // Check in-memory transient cooldown
                val memExpiry = inMemoryCooldowns[pairKey]
                if (memExpiry != null && memExpiry > now) {
                    continue
                }

                // Check persistent database lock
                if (quotaRepository.isModelKeyLocked(model.id, keyEntity.keyHash)) {
                    continue
                }

                val rawKey = secureKeyStorage.getRawKey(keyEntity.keyHash)
                if (!rawKey.isNullOrBlank()) {
                    return RotationCandidate(
                        modelId = model.id,
                        rawApiKey = rawKey,
                        keyHash = keyEntity.keyHash,
                        maskedKey = keyEntity.maskedKey
                    )
                }
            }
        }

        return null
    }

    /**
     * Reports an HTTP 429 Rate Limit error.
     * Differentiates between short RPM throttling (30s–60s) and true daily RPD exhaustion (30 hours).
     */
    suspend fun reportRateLimit(
        modelId: String,
        keyHash: String,
        responseBody: String? = null,
        retryAfterHeader: String? = null
    ) {
        val model = models.find { it.id == modelId } ?: ModelConfig.DEFAULT_MODELS.first()
        val pairKey = "$modelId::$keyHash"
        val now = System.currentTimeMillis()

        var isRpm = false
        var suggestedDelayMs = 0L

        // 1. Check Retry-After header
        if (!retryAfterHeader.isNullOrBlank()) {
            val seconds = retryAfterHeader.trim().toLongOrNull()
            if (seconds != null && seconds in 1..300) {
                isRpm = true
                suggestedDelayMs = seconds * 1000L
            }
        }

        // 2. Parse Google Gemini Error JSON for retryDelay or ResourceExhausted details
        if (!responseBody.isNullOrBlank()) {
            try {
                val json = JSONObject(responseBody)
                val error = json.optJSONObject("error")
                val message = error?.optString("message", "") ?: ""

                if (message.contains("Per-minute", ignoreCase = true) ||
                    message.contains("RPM", ignoreCase = true) ||
                    message.contains("rate limit", ignoreCase = true)
                ) {
                    isRpm = true
                }

                val details = error?.optJSONArray("details")
                if (details != null) {
                    for (i in 0 until details.length()) {
                        val item = details.optJSONObject(i)
                        val retryDelayStr = item?.optString("retryDelay", "") ?: ""
                        if (retryDelayStr.endsWith("s")) {
                            val sec = retryDelayStr.removeSuffix("s").toDoubleOrNull()
                            if (sec != null && sec > 0) {
                                isRpm = true
                                suggestedDelayMs = (sec * 1000L).toLong()
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore JSON parsing failures
            }
        }

        if (isRpm) {
            val cooldownMs = if (suggestedDelayMs > 0) suggestedDelayMs else model.rpmCooldownMs
            inMemoryCooldowns[pairKey] = now + cooldownMs
            quotaRepository.lockModelKey(
                modelId = modelId,
                keyHash = keyHash,
                errorStatus = 429,
                lockType = "rpm_cooldown",
                cooldownMs = cooldownMs,
                isPermanent = false
            )
        } else {
            // Full RPD daily exhaustion -> lock for 30 hours
            inMemoryCooldowns[pairKey] = now + model.rpdExhaustedMs
            quotaRepository.lockModelKey(
                modelId = modelId,
                keyHash = keyHash,
                errorStatus = 429,
                lockType = "rpd_exhausted",
                cooldownMs = model.rpdExhaustedMs,
                isPermanent = false
            )
        }
    }

    /**
     * Reports an HTTP 503 Server Overload or request timeout.
     * Places the pair in a 5-minute cooldown.
     */
    suspend fun reportServerOverloadOrTimeout(modelId: String, keyHash: String) {
        val model = models.find { it.id == modelId } ?: ModelConfig.DEFAULT_MODELS.first()
        val pairKey = "$modelId::$keyHash"
        val cooldownMs = model.timeoutCooldownMs

        inMemoryCooldowns[pairKey] = System.currentTimeMillis() + cooldownMs
        quotaRepository.lockModelKey(
            modelId = modelId,
            keyHash = keyHash,
            errorStatus = 503,
            lockType = "timeout_cooldown",
            cooldownMs = cooldownMs,
            isPermanent = false
        )
    }

    /**
     * Reports an empty stream (0 tokens received).
     * Applies a 3-minute cooldown to prevent infinite looping on faulty model pairs.
     */
    suspend fun reportEmptyStream(modelId: String, keyHash: String) {
        val pairKey = "$modelId::$keyHash"
        val cooldownMs = 180_000L // 3 minutes

        inMemoryCooldowns[pairKey] = System.currentTimeMillis() + cooldownMs
        quotaRepository.lockModelKey(
            modelId = modelId,
            keyHash = keyHash,
            errorStatus = 500,
            lockType = "zero_token_cooldown",
            cooldownMs = cooldownMs,
            isPermanent = false
        )
    }

    fun clearMemoryCooldowns() {
        inMemoryCooldowns.clear()
    }
}
