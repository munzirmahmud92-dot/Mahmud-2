package com.example.ui.screens.recorder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioProcessor
import com.example.audio.RecordedCapture
import com.example.audio.RecordingStatus
import com.example.audio.StudioAudioPlayer
import com.example.audio.StudioAudioRecorder
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

class RecordingStudioViewModel(
    application: Application,
    private val audioClipDao: AudioClipDao,
    settingsRepository: SettingsRepository,
    val player: StudioAudioPlayer
) : AndroidViewModel(application) {

    private val recorder = StudioAudioRecorder(application.cacheDir)

    val status: StateFlow<RecordingStatus> = recorder.status
    val amplitude: StateFlow<Float> = recorder.amplitude
    val elapsedMs: StateFlow<Long> = recorder.elapsedMs
    val waveform: StateFlow<List<Float>> = recorder.waveformHistory

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    private val _selectedFormat = MutableStateFlow(AudioFormat.WAV)
    val selectedFormat: StateFlow<AudioFormat> = _selectedFormat.asStateFlow()

    private val _recordingTitle = MutableStateFlow("")
    val recordingTitle: StateFlow<String> = _recordingTitle.asStateFlow()

    private val _finishedClip = MutableStateFlow<AudioClipEntity?>(null)
    val finishedClip: StateFlow<AudioClipEntity?> = _finishedClip.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setSelectedFormat(format: AudioFormat) {
        _selectedFormat.value = format
    }

    fun updateTitle(title: String) {
        _recordingTitle.value = title
    }

    fun startRecording() {
        player.stop()
        _finishedClip.value = null
        _statusMessage.value = null
        val res = recorder.startRecording(viewModelScope, settings.value.audioQuality.sampleRateHz)
        res.onFailure {
            _statusMessage.value = it.localizedMessage ?: "Unable to start microphone."
        }
    }

    fun pauseRecording() = recorder.pauseRecording()
    fun resumeRecording() = recorder.resumeRecording()

    fun stopAndSave() {
        val capture: RecordedCapture = recorder.stopAndFinish() ?: run {
            _statusMessage.value = "Recording is empty."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val app = getApplication<Application>()
            val format = _selectedFormat.value
            val customTitle = _recordingTitle.value.trim()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val title = customTitle.ifBlank { "Studio Take $stamp" }

            val clip = withContext(Dispatchers.IO) {
                val audioDir = File(app.filesDir, "audio").apply { mkdirs() }
                val outFile = File(audioDir, "rec_$stamp.${format.extension}")
                val pcmBytes = capture.pcmFile.readBytes()
                when (format) {
                    AudioFormat.WAV -> AudioProcessor.writePcmAsWav(pcmBytes, capture.sampleRateHz, 1, outFile)
                    AudioFormat.MP3 -> AudioProcessor.writePcmAsMp3(
                        pcmBytes,
                        capture.sampleRateHz,
                        1,
                        settings.value.audioQuality.mp3BitrateKbps,
                        outFile
                    )
                }
                capture.pcmFile.delete()

                val entity = AudioClipEntity(
                    title = title,
                    filePath = outFile.absolutePath,
                    format = format.name,
                    sourceType = AudioSourceType.STUDIO_RECORDING.name,
                    languageCode = "studio",
                    voiceName = "Studio Mic",
                    durationMs = capture.durationMs,
                    fileSizeBytes = outFile.length(),
                    sampleRateHz = capture.sampleRateHz
                )
                val id = audioClipDao.insertClip(entity)
                entity.copy(id = id)
            }

            _finishedClip.value = clip
            _recordingTitle.value = ""
            _statusMessage.value = "Saved recording (${clip.format}) to Audio Library."
            _isSaving.value = false
        }
    }

    fun togglePreview() {
        val clip = _finishedClip.value ?: return
        player.togglePlayPause(clip.filePath, clip.title)
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        recorder.cancelRecording()
        super.onCleared()
    }
}
