package com.example.ui.screens.tts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioProcessor
import com.example.audio.StudioAudioPlayer
import com.example.core.NetworkMonitor
import com.example.data.local.ApiUsageEntity
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.StudioSettings
import com.example.data.remote.GeminiAiStudioRepository
import com.example.data.remote.GeminiTtsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface TtsUiState {
    data object Idle : TtsUiState
    data object Generating : TtsUiState
    data class Ready(val clip: AudioClipEntity, val waveformPeaks: List<Float>) : TtsUiState
    data class Error(val message: String) : TtsUiState
}

class TextToSpeechViewModel(
    private val geminiTtsRepository: GeminiTtsRepository,
    private val geminiAiStudioRepository: GeminiAiStudioRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    val player: StudioAudioPlayer
) : ViewModel() {

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    val isNetworkOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val todayUsage: StateFlow<ApiUsageEntity?> = geminiTtsRepository.observeTodayUsage()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedVoice = MutableStateFlow(GeminiTtsRepository.AVAILABLE_VOICES.first().id)
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _selectedLanguageCode = MutableStateFlow<String?>(null)
    val selectedLanguageCode: StateFlow<String?> = _selectedLanguageCode.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _isAiAssisting = MutableStateFlow(false)
    val isAiAssisting: StateFlow<Boolean> = _isAiAssisting.asStateFlow()

    private val _uiState = MutableStateFlow<TtsUiState>(TtsUiState.Idle)
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private var appliedInitialRouteKey: String? = null

    fun applyPresetIfNeeded(
        initialText: String?,
        voiceId: String?,
        languageCode: String?,
        speedValue: Float?,
        pitchValue: Float?
    ) {
        val key = "$initialText|$voiceId|$languageCode|$speedValue|$pitchValue"
        if (key == appliedInitialRouteKey) return
        if (initialText == null && voiceId == null && languageCode == null && speedValue == null && pitchValue == null) {
            return
        }
        appliedInitialRouteKey = key
        initialText?.let { _inputText.value = it }
        voiceId?.let { id ->
            if (GeminiTtsRepository.AVAILABLE_VOICES.any { it.id == id }) {
                _selectedVoice.value = id
            }
        }
        if (languageCode != null) {
            _selectedLanguageCode.value = languageCode
        }
        speedValue?.let { _speed.value = it.coerceIn(0.6f, 1.8f) }
        pitchValue?.let { _pitch.value = it.coerceIn(0.7f, 1.4f) }
    }

    fun runInlineAiAssist(mode: String) {
        val currentSettings = settings.value
        val online = networkMonitor.refresh()
        if (currentSettings.offlineOnlyMode || !online) {
            _uiState.value = TtsUiState.Error("Internet is required for Gemini AI script assistance.")
            return
        }
        val text = _inputText.value.trim()
        if (text.isBlank()) {
            _uiState.value = TtsUiState.Error("Please enter text first to use AI Assist.")
            return
        }
        if (_isAiAssisting.value) return

        viewModelScope.launch {
            _isAiAssisting.value = true
            try {
                geminiAiStudioRepository.streamTransformText(text, mode).collect { updated ->
                    _inputText.value = updated
                }
                if (mode == "TRANSLATE_TO_BN") {
                    _selectedLanguageCode.value = "bn-IN"
                } else if (mode == "TRANSLATE_TO_EN") {
                    _selectedLanguageCode.value = "en-US"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TtsUiState.Error(e.localizedMessage ?: "AI Assist failed.")
            } finally {
                _isAiAssisting.value = false
            }
        }
    }

    fun updateText(text: String) {
        _inputText.value = text
    }

    fun selectVoice(voiceId: String) {
        _selectedVoice.value = voiceId
    }

    fun selectLanguage(code: String?) {
        _selectedLanguageCode.value = code
    }

    fun updateSpeed(value: Float) {
        _speed.value = value
        player.updateSpeedAndPitch(value, _pitch.value)
    }

    fun updatePitch(value: Float) {
        _pitch.value = value
        player.updateSpeedAndPitch(_speed.value, value)
    }

    fun generateSpeech() {
        val currentSettings = settings.value
        val online = networkMonitor.refresh()
        if (currentSettings.offlineOnlyMode || !online) {
            _uiState.value = TtsUiState.Error(
                if (currentSettings.offlineOnlyMode) {
                    "Offline-Only Mode is enabled in Settings. Disable it or switch to Offline Voice Studio."
                } else {
                    "No active internet connection detected. Check your network or use Offline Voice Studio."
                }
            )
            return
        }

        val text = _inputText.value.trim()
        if (text.isBlank()) {
            _uiState.value = TtsUiState.Error("Please enter Bengali or English text first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = TtsUiState.Generating
            try {
                val result = geminiTtsRepository.generateSpeech(
                    text = text,
                    voiceName = _selectedVoice.value,
                    languageCode = _selectedLanguageCode.value,
                    speed = _speed.value,
                    pitch = _pitch.value,
                    outputFormat = currentSettings.defaultFormat,
                    audioQuality = currentSettings.audioQuality
                )
                result.fold(
                    onSuccess = { clip ->
                        val peaks = withContext(Dispatchers.IO) {
                            AudioProcessor.extractWaveformPeaks(File(clip.filePath), 40)
                        }
                        _uiState.value = TtsUiState.Ready(clip, peaks)
                        player.playFile(clip.filePath, clip.title, speed = 1.0f, pitch = 1.0f)
                    },
                    onFailure = { err ->
                        _uiState.value = TtsUiState.Error(
                            err.localizedMessage ?: "Failed to generate Gemini speech."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TtsUiState.Error(
                    e.localizedMessage ?: "Unexpected network or synthesis error."
                )
            }
        }
    }

    fun togglePlayClip(clip: AudioClipEntity) {
        player.togglePlayPause(clip.filePath, clip.title)
    }

    fun stopPlayback() {
        player.stop()
    }

    fun clearError() {
        if (_uiState.value is TtsUiState.Error) {
            _uiState.value = TtsUiState.Idle
        }
    }
}
