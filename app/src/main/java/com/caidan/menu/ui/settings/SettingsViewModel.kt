package com.caidan.menu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caidan.menu.data.repo.RecipeRepository
import com.caidan.menu.data.repo.SettingsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val textModel: String = SettingsRepository.DEFAULT_TEXT_MODEL,
    val visionModel: String = SettingsRepository.DEFAULT_VISION_MODEL,
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repository: RecipeRepository,
) : ViewModel() {

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.apiKey, settings.textModel, settings.visionModel
    ) { key, text, vision ->
        SettingsUiState(apiKey = key, textModel = text, visionModel = vision)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveApiKey(value: String) {
        viewModelScope.launch {
            settings.setApiKey(value)
            _messages.send(if (value.isBlank()) "已清除 API Key" else "API Key 已保存")
        }
    }

    fun setTextModel(value: String) {
        viewModelScope.launch { settings.setTextModel(value) }
    }

    fun setVisionModel(value: String) {
        viewModelScope.launch { settings.setVisionModel(value) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.deleteAllRecipes()
                _messages.send("已清空全部菜谱数据")
            } catch (e: Exception) {
                _messages.send("清空失败：${e.message ?: "未知错误"}")
            }
        }
    }
}
