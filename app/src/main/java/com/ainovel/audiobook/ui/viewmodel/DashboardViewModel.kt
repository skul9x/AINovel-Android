package com.ainovel.audiobook.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ainovel.audiobook.data.local.entity.NovelEntity
import com.ainovel.audiobook.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ActiveGenerationState(
    val novelId: String = "",
    val chapterTitle: String = "Writing Chapter 4: Trước Khi Thành Phố Kịp Nhắm Mắt",
    val progressPercent: Int = 62,
    val tokensPerSecond: Int = 52,
    val activeModel: String = "Gemini 3.8 Flash",
    val isGenerating: Boolean = true
)

data class DashboardUiState(
    val novels: List<NovelEntity> = emptyList(),
    val activeGeneration: ActiveGenerationState? = null,
    val isLoading: Boolean = false,
    val currentLanguage: String = "vi"
)

class DashboardViewModel(
    private val novelRepository: NovelRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadNovels()
    }

    fun loadNovels() {
        if (novelRepository != null) {
            viewModelScope.launch {
                novelRepository.getAllNovels().collect { novels ->
                    _uiState.update { it.copy(novels = novels) }
                }
            }
        } else {
            // Default sample novel for preview / offline
            val sampleNovels = listOf(
                NovelEntity(
                    id = "sample_1",
                    title = "The Chronicles of Whisper",
                    topic = "Một câu chuyện huyền bí nơi các bí mật cổ xưa dần hé lộ trong đêm tĩnh mịch.",
                    genre = "mystery",
                    status = "in_progress"
                ),
                NovelEntity(
                    id = "sample_2",
                    title = "Kỷ Nguyên Không Gian 2099",
                    topic = "Cuộc phiêu lưu sinh tồn ngoài rìa dải ngân hà với công nghệ Cyberpunk tối tân.",
                    genre = "hard_sci_fi",
                    status = "completed"
                )
            )
            _uiState.update { it.copy(novels = sampleNovels) }
        }
    }

    fun createNovel(title: String, topic: String = "", genre: String = "web_novel") {
        if (novelRepository != null) {
            viewModelScope.launch {
                novelRepository.createNovel(title, topic, genre)
            }
        } else {
            val newNovel = NovelEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                topic = topic,
                genre = genre,
                status = "draft"
            )
            _uiState.update { it.copy(novels = listOf(newNovel) + it.novels) }
        }
    }

    fun deleteNovel(novelId: String) {
        if (novelRepository != null) {
            viewModelScope.launch {
                novelRepository.deleteNovel(novelId)
            }
        } else {
            _uiState.update { state ->
                state.copy(novels = state.novels.filter { it.id != novelId })
            }
        }
    }

    fun setLanguage(lang: String) {
        _uiState.update { it.copy(currentLanguage = lang) }
    }
}
