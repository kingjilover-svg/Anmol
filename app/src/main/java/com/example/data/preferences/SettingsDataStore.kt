package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.GeminiModelOption
import com.example.domain.PlanTier
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "unknown_ai_prefs")

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.DAYLIGHT,
    val globalMemoryEnabled: Boolean = true,
    val autoFollowUpsEnabled: Boolean = true,
    val voiceAutoLoopEnabled: Boolean = true,
    val ttsSpeechRate: Float = 1.0f,
    val selectedModelId: String = GeminiModelOption.FLASH_38.modelId,
    val lastGuestConversationId: String? = null,
    val previewPlanTier: PlanTier = PlanTier.STARTER,
    val guestMessageCount: Long = 0L,
    val guestImageCount: Long = 0L,
    val guestSessionActive: Boolean = false
)

class SettingsDataStore(private val context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GLOBAL_MEMORY_ENABLED = booleanPreferencesKey("global_memory_enabled")
        val AUTO_FOLLOW_UPS_ENABLED = booleanPreferencesKey("auto_follow_ups_enabled")
        val VOICE_AUTO_LOOP_ENABLED = booleanPreferencesKey("voice_auto_loop_enabled")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val LAST_GUEST_CONVERSATION_ID = stringPreferencesKey("last_guest_conversation_id")
        val PREVIEW_PLAN_TIER = stringPreferencesKey("preview_plan_tier")
        val GUEST_MESSAGE_COUNT = longPreferencesKey("guest_message_count")
        val GUEST_IMAGE_COUNT = longPreferencesKey("guest_image_count")
        val GUEST_SESSION_ACTIVE = booleanPreferencesKey("guest_session_active")
    }

    val preferencesFlow: Flow<AppPreferences> = dataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DAYLIGHT.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.DAYLIGHT
        val planId = prefs[Keys.PREVIEW_PLAN_TIER] ?: PlanTier.STARTER.id
        AppPreferences(
            themeMode = themeMode,
            globalMemoryEnabled = prefs[Keys.GLOBAL_MEMORY_ENABLED] ?: true,
            autoFollowUpsEnabled = prefs[Keys.AUTO_FOLLOW_UPS_ENABLED] ?: true,
            voiceAutoLoopEnabled = prefs[Keys.VOICE_AUTO_LOOP_ENABLED] ?: true,
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f,
            selectedModelId = prefs[Keys.SELECTED_MODEL_ID] ?: GeminiModelOption.FLASH_38.modelId,
            lastGuestConversationId = prefs[Keys.LAST_GUEST_CONVERSATION_ID],
            previewPlanTier = PlanTier.fromId(planId),
            guestMessageCount = prefs[Keys.GUEST_MESSAGE_COUNT] ?: 0L,
            guestImageCount = prefs[Keys.GUEST_IMAGE_COUNT] ?: 0L,
            guestSessionActive = prefs[Keys.GUEST_SESSION_ACTIVE] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setGlobalMemoryEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GLOBAL_MEMORY_ENABLED] = enabled }
    }

    suspend fun setAutoFollowUpsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_FOLLOW_UPS_ENABLED] = enabled }
    }

    suspend fun setVoiceAutoLoopEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VOICE_AUTO_LOOP_ENABLED] = enabled }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        dataStore.edit { it[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setSelectedModelId(modelId: String) {
        dataStore.edit { it[Keys.SELECTED_MODEL_ID] = modelId }
    }

    suspend fun setLastGuestConversationId(conversationId: String?) {
        dataStore.edit {
            if (conversationId == null) it.remove(Keys.LAST_GUEST_CONVERSATION_ID)
            else it[Keys.LAST_GUEST_CONVERSATION_ID] = conversationId
        }
    }

    suspend fun setPreviewPlanTier(tier: PlanTier) {
        dataStore.edit { it[Keys.PREVIEW_PLAN_TIER] = tier.id }
    }

    suspend fun incrementGuestMessageCount() {
        dataStore.edit { prefs ->
            prefs[Keys.GUEST_MESSAGE_COUNT] = (prefs[Keys.GUEST_MESSAGE_COUNT] ?: 0L) + 1L
        }
    }

    suspend fun incrementGuestImageCount() {
        dataStore.edit { prefs ->
            prefs[Keys.GUEST_IMAGE_COUNT] = (prefs[Keys.GUEST_IMAGE_COUNT] ?: 0L) + 1L
        }
    }

    suspend fun setGuestSessionActive(active: Boolean) {
        dataStore.edit { it[Keys.GUEST_SESSION_ACTIVE] = active }
    }
}
