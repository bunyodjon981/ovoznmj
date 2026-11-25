package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "telefonim_settings")

data class AssistantSettings(
    val groqApiKey: String = "",
    val wakeWords: List<String> = listOf("Telefonim"),
    val unlockPin: String = "",
    val activeModel: String = "llama-3.3-70b-versatile",
    val discoveredModels: List<String> = emptyList(),
    val autoFreeModel: Boolean = true,
    val voiceFeedbackEnabled: Boolean = true,
    val autoDismissOverlay: Boolean = true,
    val continuousWakeListening: Boolean = true
) {
    val wakeWord: String
        get() = wakeWords.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Telefonim"
}

class SettingsDataStore(private val context: Context) {

    companion object {
        const val MAX_WAKE_WORDS = 10
    }

    private object Keys {
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val WAKE_WORDS_LIST = stringPreferencesKey("wake_words_list")
        val UNLOCK_PIN = stringPreferencesKey("unlock_pin")
        val ACTIVE_MODEL = stringPreferencesKey("active_model")
        val DISCOVERED_MODELS = stringPreferencesKey("discovered_models")
        val AUTO_FREE_MODEL = booleanPreferencesKey("auto_free_model")
        val VOICE_FEEDBACK = booleanPreferencesKey("voice_feedback")
        val AUTO_DISMISS_OVERLAY = booleanPreferencesKey("auto_dismiss_overlay")
        val CONTINUOUS_WAKE = booleanPreferencesKey("continuous_wake")
    }

    val settingsFlow: Flow<AssistantSettings> = context.dataStore.data.map { prefs ->
        val rawModels = prefs[Keys.DISCOVERED_MODELS].orEmpty()
        val rawWakeList = prefs[Keys.WAKE_WORDS_LIST].orEmpty()
        val legacyWake = prefs[Keys.WAKE_WORD]?.takeIf { it.isNotBlank() }

        val parsedWakeWords = if (rawWakeList.isNotBlank()) {
            rawWakeList.split("|").map { it.trim() }.filter { it.isNotBlank() }.take(MAX_WAKE_WORDS)
        } else if (legacyWake != null) {
            listOf(legacyWake)
        } else {
            listOf("Telefonim")
        }.ifEmpty { listOf("Telefonim") }

        AssistantSettings(
            groqApiKey = prefs[Keys.GROQ_API_KEY].orEmpty(),
            wakeWords = parsedWakeWords,
            unlockPin = prefs[Keys.UNLOCK_PIN].orEmpty(),
            activeModel = prefs[Keys.ACTIVE_MODEL]?.takeIf { it.isNotBlank() } ?: "llama-3.3-70b-versatile",
            discoveredModels = if (rawModels.isBlank()) emptyList() else rawModels.split("|").filter { it.isNotBlank() },
            autoFreeModel = prefs[Keys.AUTO_FREE_MODEL] ?: true,
            voiceFeedbackEnabled = prefs[Keys.VOICE_FEEDBACK] ?: true,
            autoDismissOverlay = prefs[Keys.AUTO_DISMISS_OVERLAY] ?: true,
            continuousWakeListening = prefs[Keys.CONTINUOUS_WAKE] ?: true
        )
    }

    suspend fun getSnapshot(): AssistantSettings = settingsFlow.first()

    suspend fun saveGroqApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GROQ_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveWakeWord(wakeWord: String) {
        val current = getSnapshot().wakeWords.toMutableList()
        val cleaned = wakeWord.trim().ifBlank { "Telefonim" }
        if (current.isEmpty()) {
            current.add(cleaned)
        } else {
            current[0] = cleaned
        }
        saveWakeWords(current)
    }

    suspend fun saveWakeWords(wakeWords: List<String>) {
        val cleaned = wakeWords
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(MAX_WAKE_WORDS)
            .ifEmpty { listOf("Telefonim") }

        context.dataStore.edit { prefs ->
            prefs[Keys.WAKE_WORDS_LIST] = cleaned.joinToString("|")
            prefs[Keys.WAKE_WORD] = cleaned.first()
        }
    }

    suspend fun saveUnlockPin(pin: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UNLOCK_PIN] = pin.trim()
        }
    }

    suspend fun saveActiveAndDiscoveredModels(activeModel: String, discovered: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_MODEL] = activeModel
            prefs[Keys.DISCOVERED_MODELS] = discovered.joinToString("|")
        }
    }

    suspend fun setAutoFreeModel(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_FREE_MODEL] = enabled
        }
    }

    suspend fun setVoiceFeedback(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOICE_FEEDBACK] = enabled
        }
    }

    suspend fun setAutoDismissOverlay(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_DISMISS_OVERLAY] = enabled
        }
    }

    suspend fun setContinuousWakeListening(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CONTINUOUS_WAKE] = enabled
        }
    }
}
