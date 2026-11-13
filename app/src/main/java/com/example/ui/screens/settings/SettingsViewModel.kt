package com.example.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioFormat
import com.example.data.local.AudioSourceType
import com.example.data.preferences.AudioQuality
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.StudioSettings
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageStats(
    val audioBytes: Long = 0L,
    val samplesBytes: Long = 0L,
    val cacheBytes: Long = 0L
) {
    val totalBytes: Long get() = audioBytes + samplesBytes + cacheBytes
}

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val audioClipDao: AudioClipDao
) : AndroidViewModel(application) {

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshStorageStats()
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepository.setLanguage(language)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDefaultFormat(format: AudioFormat) = viewModelScope.launch {
        settingsRepository.setDefaultFormat(format)
    }

    fun setAudioQuality(quality: AudioQuality) = viewModelScope.launch {
        settingsRepository.setAudioQuality(quality)
    }

    fun setOfflineOnlyMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setOfflineOnlyMode(enabled)
    }

    fun setPrivacyPin(pin: String?) = viewModelScope.launch {
        settingsRepository.setPrivacyPin(pin)
        _statusMessage.value = if (pin.isNullOrBlank()) "Privacy PIN removed." else "Privacy PIN saved."
    }

    fun setRequireConsent(required: Boolean) = viewModelScope.launch {
        settingsRepository.setRequireConsentBeforeClone(required)
    }

    fun setLocalOnlyPrivacy(localOnly: Boolean) = viewModelScope.launch {
        settingsRepository.setLocalOnlyPrivacyMode(localOnly)
    }

    fun setCloningApiUrl(url: String) = viewModelScope.launch {
        val cleaned = url.trim()
        if (cleaned.isNotEmpty() && !cleaned.startsWith("https://")) {
            _statusMessage.value = "Cloning API URL must start with https://"
            return@launch
        }
        settingsRepository.setCloningApiBaseUrl(cleaned)
        _statusMessage.value = if (cleaned.isBlank()) {
            "Cloning endpoint cleared."
        } else {
            "Cloning endpoint saved."
        }
    }

    fun refreshStorageStats() = viewModelScope.launch {
        val stats = withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            StorageStats(
                audioBytes = folderSize(File(app.filesDir, "audio")),
                samplesBytes = folderSize(File(app.filesDir, "samples")),
                cacheBytes = folderSize(app.cacheDir)
            )
        }
        _storageStats.value = stats
    }

    fun clearExportCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            File(app.cacheDir, "exports").deleteRecursively()
            File(app.cacheDir, "recording_temp").deleteRecursively()
        }
        refreshStorageStats()
        _statusMessage.value = "Export and temp recording cache cleared."
    }

    fun clearGeneratedTtsHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val audioDir = File(app.filesDir, "audio")
            audioDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("gemini_") || file.name.startsWith("offline_")) {
                    file.delete()
                }
            }
            audioClipDao.deleteClipsBySources(
                listOf(AudioSourceType.GEMINI_TTS.name, AudioSourceType.OFFLINE_TTS.name)
            )
        }
        refreshStorageStats()
        _statusMessage.value = "Generated TTS audio history cleared."
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    private fun folderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
