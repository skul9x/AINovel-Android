package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.audiobook.data.local.dao.ApiKeyDao
import com.ainovel.audiobook.data.repository.QuotaRepository
import com.ainovel.audiobook.data.security.SecureKeyStorage
import com.ainovel.audiobook.domain.engine.RotationManager
import com.ainovel.audiobook.domain.model.ModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MaskedApiKeyItem(
    val id: String,
    val maskedKey: String,
    val status: String = "ACTIVE",
    val requestCount: Int = 0
)

data class ModelPriorityItem(
    val modelId: String,
    val displayName: String,
    val tier: String,
    val isEnabled: Boolean = true
)

data class QuotaIndicatorItem(
    val keyId: String,
    val modelTier: String,
    val remainingRequests: Int,
    val resetTimeRemaining: String
)

data class SettingsUiState(
    val apiKeys: List<MaskedApiKeyItem> = listOf(
        MaskedApiKeyItem(UUID.randomUUID().toString(), "AIzaSyD...k489", "ACTIVE", 42),
        MaskedApiKeyItem(UUID.randomUUID().toString(), "AIzaSyA...w912", "ACTIVE", 18)
    ),
    val modelPriorities: List<ModelPriorityItem> = listOf(
        ModelPriorityItem("gemini-2.5-flash", "Gemini 2.5 Flash", "Tier 1: High Quality & Multimodal"),
        ModelPriorityItem("gemini-2.0-flash", "Gemini 2.0 Flash", "Tier 2: Speed / Velocity"),
        ModelPriorityItem("gemini-1.5-flash", "Gemini 1.5 Flash", "Tier 3: High-Throughput Fallback"),
        ModelPriorityItem("gemini-1.5-pro", "Gemini 1.5 Pro", "Tier 4: Deep Long-Context Reasoning")
    ),
    val quotaStatuses: List<QuotaIndicatorItem> = listOf(
        QuotaIndicatorItem("Key-1", "Gemini 2.5 Flash", 1450, "03:42:15"),
        QuotaIndicatorItem("Key-1", "Gemini 2.0 Flash", 9850, "05:12:00")
    ),
    val currentLanguage: String = "vi"
)

class SettingsViewModel(
    private val apiKeyDao: ApiKeyDao? = null,
    private val secureKeyStorage: SecureKeyStorage? = null,
    private val quotaRepository: QuotaRepository? = null,
    private val rotationManager: RotationManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        if (apiKeyDao != null) {
            viewModelScope.launch {
                apiKeyDao.getAllKeys().collect { entities ->
                    if (entities.isNotEmpty()) {
                        val items = entities.map {
                            MaskedApiKeyItem(
                                id = it.id,
                                maskedKey = it.maskedKey,
                                status = if (it.isActive) "ACTIVE" else "INACTIVE",
                                requestCount = 0
                            )
                        }
                        _uiState.update { it.copy(apiKeys = items) }
                    }
                }
            }
        }
    }

    fun addApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return

        val masked = if (trimmed.length > 8) {
            "${trimmed.take(6)}...${trimmed.takeLast(4)}"
        } else {
            "****${trimmed.takeLast(2)}"
        }

        val newItem = MaskedApiKeyItem(
            id = UUID.randomUUID().toString(),
            maskedKey = masked,
            status = "ACTIVE",
            requestCount = 0
        )
        _uiState.update { it.copy(apiKeys = it.apiKeys + newItem) }

        if (secureKeyStorage != null) {
            viewModelScope.launch {
                secureKeyStorage.saveKey(trimmed, provider = "gemini")
            }
        }
    }

    fun deleteApiKey(keyId: String) {
        if (apiKeyDao != null && secureKeyStorage != null) {
            viewModelScope.launch {
                val all = apiKeyDao.getActiveKeys()
                val target = all.find { it.id == keyId }
                if (target != null) {
                    secureKeyStorage.deleteKey(target.keyHash)
                }
            }
        }
        _uiState.update { state ->
            state.copy(apiKeys = state.apiKeys.filter { it.id != keyId })
        }
    }

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(currentLanguage = lang) }
    }

    fun toggleModelEnabled(modelId: String) {
        _uiState.update { state ->
            val updated = state.modelPriorities.map {
                if (it.modelId == modelId) it.copy(isEnabled = !it.isEnabled) else it
            }
            state.copy(modelPriorities = updated)
        }
    }

    fun moveModelPriority(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            val list = state.modelPriorities.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                state.copy(modelPriorities = list)
            } else {
                state
            }
        }
    }
}
