package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.jarvisDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_vip_prefs")

data class LocalDeviceSettings(
    val micEnabled: Boolean = true,
    val automationEnabled: Boolean = true,
    val autoSpeakReplies: Boolean = true,
    val handsFreeWakeWord: Boolean = false,
    val weatherCity: String = "New Delhi",
    val backendProxyUrl: String = ""
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val MIC_ENABLED = booleanPreferencesKey("mic_enabled")
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val HANDS_FREE = booleanPreferencesKey("hands_free_wake_word")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val BACKEND_PROXY_URL = stringPreferencesKey("backend_proxy_url")
    }

    val settings: Flow<LocalDeviceSettings> = context.jarvisDataStore.data.map { prefs ->
        LocalDeviceSettings(
            micEnabled = prefs[Keys.MIC_ENABLED] ?: true,
            automationEnabled = prefs[Keys.AUTOMATION_ENABLED] ?: true,
            autoSpeakReplies = prefs[Keys.AUTO_SPEAK] ?: true,
            handsFreeWakeWord = prefs[Keys.HANDS_FREE] ?: false,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "New Delhi",
            backendProxyUrl = prefs[Keys.BACKEND_PROXY_URL] ?: ""
        )
    }

    suspend fun setMicEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit {
            it[Keys.MIC_ENABLED] = enabled
            if (!enabled) it[Keys.HANDS_FREE] = false
        }
    }

    suspend fun setAutomationEnabled(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTOMATION_ENABLED] = enabled }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.jarvisDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setHandsFreeWakeWord(enabled: Boolean) {
        context.jarvisDataStore.edit {
            it[Keys.HANDS_FREE] = enabled
            if (enabled) it[Keys.MIC_ENABLED] = true
        }
    }

    suspend fun setWeatherCity(city: String) {
        val clean = city.trim().take(60)
        if (clean.isNotEmpty()) {
            context.jarvisDataStore.edit { it[Keys.WEATHER_CITY] = clean }
        }
    }

    suspend fun setBackendProxyUrl(url: String) {
        context.jarvisDataStore.edit { it[Keys.BACKEND_PROXY_URL] = url.trim().take(300) }
    }
}
