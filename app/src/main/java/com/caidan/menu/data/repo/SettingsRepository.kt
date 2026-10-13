package com.caidan.menu.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "menu_settings")

/** 应用设置：Qwen API Key 与所用模型。 */
class SettingsRepository(private val context: Context) {

    private val keyApiKey = stringPreferencesKey("qwen_api_key")
    private val keyTextModel = stringPreferencesKey("qwen_text_model")
    private val keyVisionModel = stringPreferencesKey("qwen_vision_model")

    val apiKey: Flow<String> = context.dataStore.data.map { it[keyApiKey].orEmpty() }
    val textModel: Flow<String> = context.dataStore.data.map { it[keyTextModel] ?: DEFAULT_TEXT_MODEL }
    val visionModel: Flow<String> = context.dataStore.data.map { it[keyVisionModel] ?: DEFAULT_VISION_MODEL }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[keyApiKey] = value.trim() }
    }

    suspend fun setTextModel(value: String) {
        context.dataStore.edit { it[keyTextModel] = value }
    }

    suspend fun setVisionModel(value: String) {
        context.dataStore.edit { it[keyVisionModel] = value }
    }

    companion object {
        const val DEFAULT_TEXT_MODEL = "qwen-plus"
        const val DEFAULT_VISION_MODEL = "qwen-vl-plus"
        val TEXT_MODELS = listOf("qwen-turbo", "qwen-plus", "qwen-max")
        val VISION_MODELS = listOf("qwen-vl-plus", "qwen-vl-max")
    }
}
