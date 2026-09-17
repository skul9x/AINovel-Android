package com.ainovel.audiobook.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ainovel.audiobook.data.local.dao.ApiKeyDao
import com.ainovel.audiobook.data.local.entity.ApiKeyEntity
import java.security.MessageDigest
import java.util.UUID

interface SecureKeyStorage {
    suspend fun saveKey(rawKey: String, provider: String = "gemini", label: String = ""): ApiKeyEntity
    fun getRawKey(keyHash: String): String?
    suspend fun deleteKey(keyHash: String)
    fun maskKey(rawKey: String): String
    fun hashKey(rawKey: String): String
}

class SecureKeyStorageImpl(
    private val context: Context,
    private val apiKeyDao: ApiKeyDao
) : SecureKeyStorage {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_api_keys_store",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback to private SharedPreferences (useful for unit tests or older devices)
            context.getSharedPreferences("secure_api_keys_fallback", Context.MODE_PRIVATE)
        }
    }

    override fun hashKey(rawKey: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawKey.trim().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.substring(0, 16)
    }

    override fun maskKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        if (trimmed.length <= 8) return "••••••••"
        val prefix = trimmed.take(6)
        val suffix = trimmed.takeLast(4)
        return "$prefix••••••••$suffix"
    }

    override suspend fun saveKey(rawKey: String, provider: String, label: String): ApiKeyEntity {
        val trimmed = rawKey.trim()
        val keyHash = hashKey(trimmed)
        val masked = maskKey(trimmed)

        // Save raw key to encrypted shared preferences
        prefs.edit().putString(keyHash, trimmed).apply()

        val entity = ApiKeyEntity(
            id = UUID.randomUUID().toString(),
            maskedKey = masked,
            keyHash = keyHash,
            provider = provider,
            label = label.ifBlank { "API Key ($provider)" },
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        apiKeyDao.insertKey(entity)
        return entity
    }

    override fun getRawKey(keyHash: String): String? {
        return prefs.getString(keyHash, null)
    }

    override suspend fun deleteKey(keyHash: String) {
        prefs.edit().remove(keyHash).apply()
        apiKeyDao.deleteKeyByHash(keyHash)
    }
}
