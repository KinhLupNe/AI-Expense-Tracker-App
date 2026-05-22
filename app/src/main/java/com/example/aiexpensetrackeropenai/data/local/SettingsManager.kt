package com.example.aiexpensetrackeropenai.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val API_KEY = stringPreferencesKey("openai_api_key")
        val WEBHOOK_URL = stringPreferencesKey("google_sheets_webhook_url")
        val SHEET_URL = stringPreferencesKey("google_sheet_url")
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY] ?: "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"
    }

    val webhookUrlFlow: Flow<String?> = kotlinx.coroutines.flow.flowOf(
        "https://script.google.com/macros/s/AKfycbyt4FV8x8YQNbNjRABrWV3fcZNN4S13U21RLKY9-jLCN-tPPhVjyK7PbFvp0Fzo8jzdzA/exec"
    )

    val sheetUrlFlow: Flow<String?> = kotlinx.coroutines.flow.flowOf(
        "https://docs.google.com/spreadsheets/d/1URnleVRchiJQej9iYeek51p7TLqs2Ho6Kd4NouKg8dk/edit?gid=0#gid=0"
    )

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    suspend fun saveWebhookUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBHOOK_URL] = url
        }
    }

    suspend fun saveSheetUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SHEET_URL] = url
        }
    }
}
