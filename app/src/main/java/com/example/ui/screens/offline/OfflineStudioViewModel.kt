package com.example.ui.screens.offline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioProcessor
import com.example.audio.OfflineTtsEngine
import com.example.audio.OfflineVoiceInfo
import com.example.audio.StudioAudioPlayer
import com.example.audio.TtsEngineState
import com.example.core.NetworkMonitor
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.local.AudioSourceType
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineStudioViewModel(
    application: Application,
    val offlineTtsEngine: OfflineTtsEngine,
    private val audioClipDao: AudioClipDao,
    private val settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
    val player: StudioAudioPlayer
) : AndroidViewModel(application) {

    val engineState: StateFlow<TtsEngineState> = offlineTtsEngine.engineState
    val voices: StateFlow<List<OfflineVoiceInfo>> = offlineTtsEngine.voices
    val isSpeaking: StateFlow<Boolean> = offlineTtsEngine.isSpeaking
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    private val _selectedLanguageTag = MutableStateFlow("bn-BD")
    val selectedLanguageTag: StateFlow<String> = _selectedLanguageTag.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _savedClip = MutableStateFlow<AudioClipEntity?>(null)
    val savedClip: StateFlow<AudioClipEntity?> = _savedClip.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun updateText(text: String) {
        _inputText.value = text
    }

    fun selectVoice(voice: OfflineVoiceInfo) {
        _selectedVoiceName.value = voice.name
        _selectedLanguageTag.value = voice.localeTag
    }

    fun selectLanguageTag(tag: String) {
        _selectedLanguageTag.value = tag
        val matching = voices.value.firstOrNull { it.localeTag.startsWith(tag.take(2), ignoreCase = true) }
        _selectedVoiceName.value = matching?.name
    }

    fun updateSpeed(value: Float) {
        _speed.value = value
    }

    fun updatePitch(value: Float) {
        _pitch.value = value
    }

    fun speakNow() {
        val text = _inputText.value.trim()
        if (text.isBlank()) {
            _statusMessage.value = "Please enter Bengali or English text first."
            return
        }
        val res = offlineTtsEngine.speak(
            text = text,
            voiceName = _selectedVoiceName.value,
            languageTag = _selectedLanguageTag.value,
            speed = _speed.value,
            pitch = _pitch.value
        )
        res.onFailure {
            _statusMessage.value = it.localizedMessage ?: "Failed to speak offline."
        }
    }

    fun stopSpeaking() {
        offlineTtsEngine.stopSpeaking()
    }

    fun synthesizeAndSave() {
        val text = _inputText.value.trim()
        if (text.isBlank()) {
            _statusMessage.value = "Please enter text before saving audio."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            _statusMessage.value = null
            val app = getApplication<Application>()
            val tempWav = File(app.cacheDir, "offline_temp_${System.currentTimeMillis()}.wav")
            val result = offlineTtsEngine.synthesizeToWavFile(
                text = text,
                voiceName = _selectedVoiceName.value,
                languageTag = _selectedLanguageTag.value,
                speed = _speed.value,
                pitch = _pitch.value,
                outFile = tempWav
            )
            result.fold(
                onSuccess = { wavFile ->
                    val saved = withContext(Dispatchers.IO) {
                        val format = settings.value.defaultFormat
                        val audioDir = File(app.filesDir, "audio").apply { mkdirs() }
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val outFile = File(audioDir, "offline_$stamp.${format.extension}")

                        val wavData = AudioProcessor.readWavFile(wavFile)
                        if (wavData != null && format == AudioFormat.MP3) {
                            AudioProcessor.writePcmAsMp3(
                                pcm16 = wavData.pcmBytes,
                                sampleRateHz = wavData.sampleRateHz,
                                channels = wavData.channels,
                                bitrateKbps = settings.value.audioQuality.mp3BitrateKbps,
                                outFile = outFile
                            )
                        } else {
                            wavFile.copyTo(outFile, overwrite = true)
                        }
                        val duration = wavData?.let {
                            AudioProcessor.calculatePcmDurationMs(
                                it.pcmBytes.size.toLong(),
                                it.sampleRateHz,
                                it.channels
                            )
                        } ?: 1000L
                        wavFile.delete()

                        val clip = AudioClipEntity(
                            title = "Offline TTS • ${text.take(24)}",
                            filePath = outFile.absolutePath,
                            format = format.name,
                            sourceType = AudioSourceType.OFFLINE_TTS.name,
                            languageCode = _selectedLanguageTag.value,
                            voiceName = _selectedVoiceName.value ?: "System TTS",
                            scriptPreview = text.take(120),
                            durationMs = duration,
                            fileSizeBytes = outFile.length(),
                            sampleRateHz = wavData?.sampleRateHz ?: 22050
                        )
                        val id = audioClipDao.insertClip(clip)
                        clip.copy(id = id)
                    }
                    _savedClip.value = saved
                    _statusMessage.value = "Saved offline audio to Audio Library (${saved.format})."
                },
                onFailure = { err ->
                    tempWav.delete()
                    _statusMessage.value = err.localizedMessage ?: "Failed to synthesize offline audio file."
                }
            )
            _isSaving.value = false
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }
}
