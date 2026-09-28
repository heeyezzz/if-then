package com.ifthen.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class ApiSettings(val baseUrl: String, val apiKey: String, val model: String)

class SettingsRepository(private val context: Context) {
    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyApiKey = stringPreferencesKey("api_key")
    private val keyModel = stringPreferencesKey("model")

    val apiSettings: Flow<ApiSettings> = context.dataStore.data.map { prefs ->
        ApiSettings(
            baseUrl = prefs[keyBaseUrl] ?: "https://api.deepseek.com/v1",
            apiKey = prefs[keyApiKey] ?: "",
            // 旧默认值 deepseek-chat 已被 DeepSeek 下线，自动迁移
            model = (prefs[keyModel] ?: "deepseek-flash")
                .let { if (it == "deepseek-chat") "deepseek-flash" else it }
        )
    }

    suspend fun saveApiSettings(settings: ApiSettings) {
        context.dataStore.edit { prefs ->
            prefs[keyBaseUrl] = settings.baseUrl
            prefs[keyApiKey] = settings.apiKey
            prefs[keyModel] = settings.model
        }
    }
}
