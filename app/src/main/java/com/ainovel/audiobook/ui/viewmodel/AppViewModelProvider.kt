package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ainovel.audiobook.AppContainer

object AppViewModelProvider {
    fun createFactory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                    DashboardViewModel(container.novelRepository) as T
                }
                modelClass.isAssignableFrom(GeneratorViewModel::class.java) -> {
                    GeneratorViewModel(
                        engine = container.novelGenerationEngine,
                        repository = container.novelRepository,
                        rotationManager = container.rotationManager
                    ) as T
                }
                modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                    SettingsViewModel(
                        apiKeyDao = container.apiKeyDao,
                        secureKeyStorage = container.secureKeyStorage,
                        quotaRepository = container.quotaRepository,
                        rotationManager = container.rotationManager
                    ) as T
                }
                modelClass.isAssignableFrom(ReaderViewModel::class.java) -> {
                    ReaderViewModel(container.novelRepository) as T
                }
                modelClass.isAssignableFrom(AudioStudioViewModel::class.java) -> {
                    AudioStudioViewModel(container.audioPlayerManager) as T
                }
                modelClass.isAssignableFrom(VoicePickerViewModel::class.java) -> {
                    VoicePickerViewModel(container.voiceSamplePlayer) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
