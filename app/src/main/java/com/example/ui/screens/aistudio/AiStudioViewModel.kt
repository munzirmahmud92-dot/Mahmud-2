package com.example.ui.screens.aistudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.NetworkMonitor
import com.example.data.local.ApiUsageEntity
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.StudioSettings
import com.example.data.remote.AiScriptPreset
import com.example.data.remote.AiVoiceDirectionPlan
import com.example.data.remote.GeminiAiStudioRepository
import com.google.firebase.ai.Chat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AiStudioTab {
    SCRIPT_WRITER,
    VOICE_COACH,
    STUDIO_CHAT
}

sealed interface AiGenUiState {
    data object Idle : AiGenUiState
    data object Loading : AiGenUiState
    data class Streaming(val text: String) : AiGenUiState
    data class Success(val text: String) : AiGenUiState
    data class Error(val message: String) : AiGenUiState
}

sealed interface VoiceCoachUiState {
    data object Idle : VoiceCoachUiState
    data object Analyzing : VoiceCoachUiState
    data class Ready(val plan: AiVoiceDirectionPlan) : VoiceCoachUiState
    data class Error(val message: String) : VoiceCoachUiState
}

data class StudioChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

class AiStudioViewModel(
    private val aiStudioRepository: GeminiAiStudioRepository,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    val isNetworkOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val todayUsage: StateFlow<ApiUsageEntity?> = aiStudioRepository.observeTodayUsage().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _activeTab = MutableStateFlow(AiStudioTab.SCRIPT_WRITER)
    val activeTab: StateFlow<AiStudioTab> = _activeTab.asStateFlow()

    // Script Writer state
    private val _topic = MutableStateFlow("")
    val topic: StateFlow<String> = _topic.asStateFlow()

    private val _selectedPreset = MutableStateFlow(AiScriptPreset.YOUTUBE_INTRO)
    val selectedPreset: StateFlow<AiScriptPreset> = _selectedPreset.asStateFlow()

    private val _targetLanguageCode = MutableStateFlow("bn-IN")
    val targetLanguageCode: StateFlow<String> = _targetLanguageCode.asStateFlow()

    private val _selectedTone = MutableStateFlow("Warm & Expressive")
    val selectedTone: StateFlow<String> = _selectedTone.asStateFlow()

    private val _scriptState = MutableStateFlow<AiGenUiState>(AiGenUiState.Idle)
    val scriptState: StateFlow<AiGenUiState> = _scriptState.asStateFlow()

    // Voice Coach & Translator state
    private val _coachInput = MutableStateFlow("")
    val coachInput: StateFlow<String> = _coachInput.asStateFlow()

    private val _coachState = MutableStateFlow<VoiceCoachUiState>(VoiceCoachUiState.Idle)
    val coachState: StateFlow<VoiceCoachUiState> = _coachState.asStateFlow()

    private val _transformState = MutableStateFlow<AiGenUiState>(AiGenUiState.Idle)
    val transformState: StateFlow<AiGenUiState> = _transformState.asStateFlow()

    // Multi-turn Studio Chat state
    private val chatSession: Chat by lazy { aiStudioRepository.createChatSession() }

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _chatMessages = MutableStateFlow(
        listOf(
            StudioChatMessage(
                id = 1L,
                isUser = false,
                text = "আসসালামু আলাইকুম! আমি মনজির ভয়েস ক্রোনার AI অ্যাসিস্ট্যান্ট। বাংলা বা ইংরেজি স্ক্রিপ্ট, অনুবাদ, ভয়েস গাইড বা রেকর্ডিং টিপস চাইলে আমাকে লিখুন।"
            )
        )
    )
    val chatMessages: StateFlow<List<StudioChatMessage>> = _chatMessages.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _isChatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = _isChatSending.asStateFlow()

    private var scriptJob: Job? = null
    private var transformJob: Job? = null
    private var chatJob: Job? = null

    fun selectTab(tab: AiStudioTab) {
        _activeTab.value = tab
    }

    fun updateTopic(value: String) {
        _topic.value = value
    }

    fun selectPreset(preset: AiScriptPreset) {
        _selectedPreset.value = preset
    }

    fun selectLanguageCode(code: String) {
        _targetLanguageCode.value = code
    }

    fun selectTone(tone: String) {
        _selectedTone.value = tone
    }

    fun updateCoachInput(text: String) {
        _coachInput.value = text
    }

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    private fun checkOnlineOrReportError(): String? {
        if (settings.value.offlineOnlyMode) {
            return "Offline-Only Mode is enabled in Settings. Disable it to use Gemini AI Studio."
        }
        if (!networkMonitor.refresh()) {
            return "No internet connection detected. Connect to a network to use Gemini AI."
        }
        return null
    }

    fun generateScript() {
        val blockReason = checkOnlineOrReportError()
        if (blockReason != null) {
            _scriptState.value = AiGenUiState.Error(blockReason)
            return
        }
        val cleanTopic = _topic.value.trim()
        if (cleanTopic.isBlank()) {
            _scriptState.value = AiGenUiState.Error("Please enter a topic or brief first.")
            return
        }

        scriptJob?.cancel()
        scriptJob = viewModelScope.launch {
            _scriptState.value = AiGenUiState.Loading
            var finalText = ""
            try {
                aiStudioRepository.streamScriptGeneration(
                    topic = cleanTopic,
                    preset = _selectedPreset.value,
                    targetLanguageCode = _targetLanguageCode.value,
                    tone = _selectedTone.value
                ).collect { accumulated ->
                    finalText = accumulated
                    _scriptState.value = AiGenUiState.Streaming(accumulated)
                }
                if (finalText.isNotBlank()) {
                    _scriptState.value = AiGenUiState.Success(finalText)
                    _coachInput.value = finalText
                } else {
                    _scriptState.value = AiGenUiState.Error("Model returned an empty script.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scriptState.value = AiGenUiState.Error(e.localizedMessage ?: "Script generation failed.")
            }
        }
    }

    fun analyzeVoiceDirection(feedbackInBengali: Boolean) {
        val blockReason = checkOnlineOrReportError()
        if (blockReason != null) {
            _coachState.value = VoiceCoachUiState.Error(blockReason)
            return
        }
        val text = _coachInput.value.trim()
        if (text.isBlank()) {
            _coachState.value = VoiceCoachUiState.Error("Enter or paste a script to analyze.")
            return
        }

        viewModelScope.launch {
            _coachState.value = VoiceCoachUiState.Analyzing
            try {
                val res = aiStudioRepository.analyzeVoiceDirection(text, feedbackInBengali)
                res.fold(
                    onSuccess = { plan -> _coachState.value = VoiceCoachUiState.Ready(plan) },
                    onFailure = { err ->
                        _coachState.value = VoiceCoachUiState.Error(
                            err.localizedMessage ?: "Voice direction failed."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _coachState.value = VoiceCoachUiState.Error(
                    e.localizedMessage ?: "Voice direction failed."
                )
            }
        }
    }

    fun transformCoachText(mode: String) {
        val blockReason = checkOnlineOrReportError()
        if (blockReason != null) {
            _transformState.value = AiGenUiState.Error(blockReason)
            return
        }
        val text = _coachInput.value.trim()
        if (text.isBlank()) {
            _transformState.value = AiGenUiState.Error("Enter or paste text first.")
            return
        }

        transformJob?.cancel()
        transformJob = viewModelScope.launch {
            _transformState.value = AiGenUiState.Loading
            var finalOut = ""
            try {
                aiStudioRepository.streamTransformText(text, mode).collect { accumulated ->
                    finalOut = accumulated
                    _transformState.value = AiGenUiState.Streaming(accumulated)
                }
                if (finalOut.isNotBlank()) {
                    _transformState.value = AiGenUiState.Success(finalOut)
                    _coachInput.value = finalOut
                } else {
                    _transformState.value = AiGenUiState.Error("Empty response from AI.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transformState.value = AiGenUiState.Error(e.localizedMessage ?: "AI transform failed.")
            }
        }
    }

    fun sendChatMessage(overridePrompt: String? = null) {
        val blockReason = checkOnlineOrReportError()
        if (blockReason != null) {
            _chatError.value = blockReason
            return
        }
        val text = (overridePrompt ?: _chatInput.value).trim()
        if (text.isBlank() || _isChatSending.value) return

        if (overridePrompt == null) {
            _chatInput.value = ""
        }
        _chatError.value = null

        val userId = System.currentTimeMillis()
        val modelId = userId + 1L
        _chatMessages.value = _chatMessages.value +
            StudioChatMessage(id = userId, isUser = true, text = text) +
            StudioChatMessage(id = modelId, isUser = false, text = "", isStreaming = true)

        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            _isChatSending.value = true
            try {
                aiStudioRepository.streamChatMessage(chatSession, text).collect { accumulated ->
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == modelId) msg.copy(text = accumulated, isStreaming = true) else msg
                    }
                }
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == modelId) msg.copy(isStreaming = false) else msg
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value.filterNot { it.id == modelId && it.text.isBlank() }
                _chatError.value = e.localizedMessage ?: "AI chat request failed."
            } finally {
                _isChatSending.value = false
            }
        }
    }
}
