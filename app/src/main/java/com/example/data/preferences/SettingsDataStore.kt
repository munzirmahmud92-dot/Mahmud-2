package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.AppLanguage
import com.example.data.local.AudioFormat
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_settings")

enum class AudioQuality(val sampleRateHz: Int, val mp3BitrateKbps: Int) {
    STANDARD_24K(24000, 128),
    STUDIO_44K(44100, 192)
}

data class StudioSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultFormat: AudioFormat = AudioFormat.WAV,
    val audioQuality: AudioQuality = AudioQuality.STANDARD_24K,
    val offlineOnlyMode: Boolean = false,
    val privacyPinHash: String? = null,
    val requireConsentBeforeClone: Boolean = true,
    val localOnlyPrivacyMode: Boolean = true,
    val cloningApiBaseUrl: String = ""
) {
    val hasPrivacyPin: Boolean get() = !privacyPinHash.isNullOrBlank()
}

class SettingsRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val OFFLINE_ONLY = booleanPreferencesKey("offline_only")
        val PRIVACY_PIN_HASH = stringPreferencesKey("privacy_pin_hash")
        val REQUIRE_CONSENT = booleanPreferencesKey("require_consent")
        val LOCAL_ONLY_PRIVACY = booleanPreferencesKey("local_only_privacy")
        val CLONING_API_URL = stringPreferencesKey("cloning_api_url")
    }

    val settings: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.ENGLISH,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            defaultFormat = prefs[Keys.DEFAULT_FORMAT]?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() }
                ?: AudioFormat.WAV,
            audioQuality = prefs[Keys.AUDIO_QUALITY]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() }
                ?: AudioQuality.STANDARD_24K,
            offlineOnlyMode = prefs[Keys.OFFLINE_ONLY] ?: false,
            privacyPinHash = prefs[Keys.PRIVACY_PIN_HASH],
            requireConsentBeforeClone = prefs[Keys.REQUIRE_CONSENT] ?: true,
            localOnlyPrivacyMode = prefs[Keys.LOCAL_ONLY_PRIVACY] ?: true,
            cloningApiBaseUrl = prefs[Keys.CLONING_API_URL] ?: ""
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.studioDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.studioDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultFormat(format: AudioFormat) {
        context.studioDataStore.edit { it[Keys.DEFAULT_FORMAT] = format.name }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        context.studioDataStore.edit { it[Keys.AUDIO_QUALITY] = quality.name }
    }

    suspend fun setOfflineOnlyMode(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.OFFLINE_ONLY] = enabled }
    }

    suspend fun setPrivacyPin(pin: String?) {
        context.studioDataStore.edit { prefs ->
            if (pin.isNullOrBlank()) {
                prefs.remove(Keys.PRIVACY_PIN_HASH)
            } else {
                prefs[Keys.PRIVACY_PIN_HASH] = hashPin(pin)
            }
        }
    }

    fun verifyPin(pin: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return true
        return hashPin(pin) == storedHash
    }

    suspend fun setRequireConsentBeforeClone(required: Boolean) {
        context.studioDataStore.edit { it[Keys.REQUIRE_CONSENT] = required }
    }

    suspend fun setLocalOnlyPrivacyMode(localOnly: Boolean) {
        context.studioDataStore.edit { it[Keys.LOCAL_ONLY_PRIVACY] = localOnly }
    }

    suspend fun setCloningApiBaseUrl(url: String) {
        context.studioDataStore.edit { it[Keys.CLONING_API_URL] = url.trim() }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("munzir_studio_salt:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
