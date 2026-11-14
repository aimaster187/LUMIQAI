package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiProviderType
import com.example.data.model.CustomProviderConfig
import com.example.data.model.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.lumiqDataStore by preferencesDataStore(name = "lumiq_preferences")

class LocalPreferencesStore(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val STT_LOCALE = stringPreferencesKey("stt_locale")
        val ACTIVE_VOICE_ID = stringPreferencesKey("active_voice_id")
        val VOICE_SPEED = floatPreferencesKey("voice_speed")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        val APP_LOCK_PIN_SALT = stringPreferencesKey("app_lock_pin_salt")
        val APP_LOCK_BIOMETRIC = booleanPreferencesKey("app_lock_biometric")
        val FAILED_PIN_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        val LOCKOUT_UNTIL_MS = longPreferencesKey("lockout_until_ms")
        val CUSTOM_PROVIDERS_JSON = stringPreferencesKey("custom_providers_json")
        val VAULT_ENTRIES_JSON = stringPreferencesKey("vault_entries_json")
        val TOTAL_AI_REQUESTS = longPreferencesKey("total_ai_requests")
        val LAST_AI_LATENCY_MS = longPreferencesKey("last_ai_latency_ms")
        val WHATSAPP_DRAFTS_JSON = stringPreferencesKey("whatsapp_drafts_json")
        val BROWSER_BOOKMARKS_JSON = stringPreferencesKey("browser_bookmarks_json")
    }

    val isDarkTheme: Flow<Boolean> = context.lumiqDataStore.data.map { it[Keys.DARK_THEME] ?: false }
    val activeProviderId: Flow<String> = context.lumiqDataStore.data.map {
        it[Keys.ACTIVE_PROVIDER_ID] ?: AiProviderType.FIREBASE_GEMINI.name
    }
    val wakeWord: Flow<String> = context.lumiqDataStore.data.map { it[Keys.WAKE_WORD] ?: "Hey LUMIQ" }
    val sttLocale: Flow<String> = context.lumiqDataStore.data.map { it[Keys.STT_LOCALE] ?: "en-IN" }
    val activeVoiceId: Flow<String> = context.lumiqDataStore.data.map { it[Keys.ACTIVE_VOICE_ID] ?: "lumiq_nova_female" }
    val voiceSpeed: Flow<Float> = context.lumiqDataStore.data.map { it[Keys.VOICE_SPEED] ?: 1.02f }
    val voicePitch: Flow<Float> = context.lumiqDataStore.data.map { it[Keys.VOICE_PITCH] ?: 1.0f }
    val voiceVolume: Flow<Float> = context.lumiqDataStore.data.map { it[Keys.VOICE_VOLUME] ?: 1.0f }
    val searchEngine: Flow<String> = context.lumiqDataStore.data.map { it[Keys.SEARCH_ENGINE] ?: "Google" }
    val appLockEnabled: Flow<Boolean> = context.lumiqDataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: false }
    val appLockPinHash: Flow<String> = context.lumiqDataStore.data.map { it[Keys.APP_LOCK_PIN_HASH] ?: "" }
    val appLockPinSalt: Flow<String> = context.lumiqDataStore.data.map { it[Keys.APP_LOCK_PIN_SALT] ?: "" }
    val appLockBiometric: Flow<Boolean> = context.lumiqDataStore.data.map { it[Keys.APP_LOCK_BIOMETRIC] ?: true }
    val failedPinAttempts: Flow<Int> = context.lumiqDataStore.data.map { it[Keys.FAILED_PIN_ATTEMPTS] ?: 0 }
    val lockoutUntilMs: Flow<Long> = context.lumiqDataStore.data.map { it[Keys.LOCKOUT_UNTIL_MS] ?: 0L }
    val totalAiRequests: Flow<Long> = context.lumiqDataStore.data.map { it[Keys.TOTAL_AI_REQUESTS] ?: 0L }
    val lastAiLatencyMs: Flow<Long> = context.lumiqDataStore.data.map { it[Keys.LAST_AI_LATENCY_MS] ?: 0L }

    fun customProvidersFlow(userId: String): Flow<List<CustomProviderConfig>> =
        context.lumiqDataStore.data.map { prefs ->
            val raw = prefs[stringPreferencesKey("providers_$userId")] ?: "[]"
            parseProvidersJson(raw)
        }

    fun vaultEntriesFlow(userId: String): Flow<List<VaultEntry>> =
        context.lumiqDataStore.data.map { prefs ->
            val raw = prefs[stringPreferencesKey("vault_$userId")] ?: "[]"
            parseVaultJson(raw)
        }

    fun speakerVoiceprintFlow(userId: String): Flow<String> =
        context.lumiqDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("voiceprint_$userId")] ?: ""
        }

    val browserBookmarks: Flow<List<Pair<String, String>>> =
        context.lumiqDataStore.data.map { prefs ->
            val raw = prefs[Keys.BROWSER_BOOKMARKS_JSON] ?: "[]"
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                obj.optString("title") to obj.optString("url")
            }
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.lumiqDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setActiveProviderId(id: String) {
        context.lumiqDataStore.edit { it[Keys.ACTIVE_PROVIDER_ID] = id }
    }

    suspend fun setWakeWord(word: String) {
        context.lumiqDataStore.edit { it[Keys.WAKE_WORD] = word }
    }

    suspend fun setSttLocale(locale: String) {
        context.lumiqDataStore.edit { it[Keys.STT_LOCALE] = locale }
    }

    suspend fun setVoiceSettings(voiceId: String, speed: Float, pitch: Float, volume: Float) {
        context.lumiqDataStore.edit {
            it[Keys.ACTIVE_VOICE_ID] = voiceId
            it[Keys.VOICE_SPEED] = speed
            it[Keys.VOICE_PITCH] = pitch
            it[Keys.VOICE_VOLUME] = volume
        }
    }

    suspend fun setSearchEngine(engine: String) {
        context.lumiqDataStore.edit { it[Keys.SEARCH_ENGINE] = engine }
    }

    suspend fun saveAppLockPin(pinHash: String, salt: String, biometric: Boolean) {
        context.lumiqDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = true
            it[Keys.APP_LOCK_PIN_HASH] = pinHash
            it[Keys.APP_LOCK_PIN_SALT] = salt
            it[Keys.APP_LOCK_BIOMETRIC] = biometric
            it[Keys.FAILED_PIN_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun disableAppLock() {
        context.lumiqDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = false
            it[Keys.APP_LOCK_PIN_HASH] = ""
            it[Keys.APP_LOCK_PIN_SALT] = ""
            it[Keys.FAILED_PIN_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun recordPinFailure(attempts: Int, lockoutUntilMs: Long) {
        context.lumiqDataStore.edit {
            it[Keys.FAILED_PIN_ATTEMPTS] = attempts
            it[Keys.LOCKOUT_UNTIL_MS] = lockoutUntilMs
        }
    }

    suspend fun resetPinFailures() {
        context.lumiqDataStore.edit {
            it[Keys.FAILED_PIN_ATTEMPTS] = 0
            it[Keys.LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun saveCustomProviders(userId: String, providers: List<CustomProviderConfig>) {
        val arr = JSONArray()
        providers.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("providerType", p.providerType.name)
                put("label", p.label)
                put("endpointUrl", p.endpointUrl)
                put("modelName", p.modelName)
                put("encryptedApiKey", p.encryptedApiKey)
                put("isConnectedVerified", p.isConnectedVerified)
                put("lastLatencyMs", p.lastLatencyMs)
            })
        }
        context.lumiqDataStore.edit {
            it[stringPreferencesKey("providers_$userId")] = arr.toString()
        }
    }

    suspend fun saveVaultEntries(userId: String, entries: List<VaultEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("usernameOrLabel", e.usernameOrLabel)
                put("encryptedSecret", e.encryptedSecret)
                put("category", e.category)
                put("updatedEpochMs", e.updatedEpochMs)
            })
        }
        context.lumiqDataStore.edit {
            it[stringPreferencesKey("vault_$userId")] = arr.toString()
        }
    }

    suspend fun saveSpeakerVoiceprint(userId: String, encryptedPayload: String) {
        context.lumiqDataStore.edit {
            it[stringPreferencesKey("voiceprint_$userId")] = encryptedPayload
        }
    }

    suspend fun saveBrowserBookmarks(bookmarks: List<Pair<String, String>>) {
        val arr = JSONArray()
        bookmarks.forEach { (title, url) ->
            arr.put(JSONObject().apply {
                put("title", title)
                put("url", url)
            })
        }
        context.lumiqDataStore.edit {
            it[Keys.BROWSER_BOOKMARKS_JSON] = arr.toString()
        }
    }

    suspend fun recordAiUsage(latencyMs: Long) {
        context.lumiqDataStore.edit {
            val current = it[Keys.TOTAL_AI_REQUESTS] ?: 0L
            it[Keys.TOTAL_AI_REQUESTS] = current + 1L
            it[Keys.LAST_AI_LATENCY_MS] = latencyMs
        }
    }

    private fun parseProvidersJson(raw: String): List<CustomProviderConfig> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val type = runCatching {
                AiProviderType.valueOf(obj.optString("providerType", AiProviderType.OPENAI_COMPAT.name))
            }.getOrDefault(AiProviderType.OPENAI_COMPAT)
            CustomProviderConfig(
                id = obj.optString("id"),
                providerType = type,
                label = obj.optString("label"),
                endpointUrl = obj.optString("endpointUrl"),
                modelName = obj.optString("modelName"),
                encryptedApiKey = obj.optString("encryptedApiKey"),
                isConnectedVerified = obj.optBoolean("isConnectedVerified", false),
                lastLatencyMs = obj.optLong("lastLatencyMs", 0L)
            )
        }
    }

    private fun parseVaultJson(raw: String): List<VaultEntry> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            VaultEntry(
                id = obj.optString("id"),
                title = obj.optString("title"),
                usernameOrLabel = obj.optString("usernameOrLabel"),
                encryptedSecret = obj.optString("encryptedSecret"),
                category = obj.optString("category", "Password"),
                updatedEpochMs = obj.optLong("updatedEpochMs", 0L)
            )
        }
    }
}
