package com.example.ui.screens.clone

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioProcessor
import com.example.audio.RecordingStatus
import com.example.audio.StudioAudioPlayer
import com.example.audio.StudioAudioRecorder
import com.example.core.NetworkMonitor
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.local.AudioSourceType
import com.example.data.local.VoiceProfileDao
import com.example.data.local.VoiceProfileEntity
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.StudioSettings
import com.example.data.remote.CloningAvailability
import com.example.data.remote.FreeCloneResult
import com.example.data.remote.FreeVoiceCloneRepository
import com.example.data.remote.VoiceReplicationRepository
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StagedVoiceSample(
    val filePath: String,
    val displayName: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val waveformPeaks: List<Float>,
    val fingerprint: AudioProcessor.AcousticVoiceFingerprint
)

class VoiceCloneViewModel(
    application: Application,
    private val voiceProfileDao: VoiceProfileDao,
    private val audioClipDao: AudioClipDao,
    private val settingsRepository: SettingsRepository,
    private val replicationRepository: VoiceReplicationRepository,
    private val freeCloneRepository: FreeVoiceCloneRepository,
    private val networkMonitor: NetworkMonitor,
    val player: StudioAudioPlayer
) : AndroidViewModel(application) {

    private val recorder = StudioAudioRecorder(application.cacheDir)

    val recordingStatus: StateFlow<RecordingStatus> = recorder.status
    val recordingAmplitude: StateFlow<Float> = recorder.amplitude
    val recordingElapsedMs: StateFlow<Long> = recorder.elapsedMs
    val recordingWaveform: StateFlow<List<Float>> = recorder.waveformHistory

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _stagedSample = MutableStateFlow<StagedVoiceSample?>(null)
    val stagedSample: StateFlow<StagedVoiceSample?> = _stagedSample.asStateFlow()

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    private val _speakerName = MutableStateFlow("")
    val speakerName: StateFlow<String> = _speakerName.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("bn-IN")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _consentGranted = MutableStateFlow(false)
    val consentGranted: StateFlow<Boolean> = _consentGranted.asStateFlow()

    private val _protectWithPin = MutableStateFlow(true)
    val protectWithPin: StateFlow<Boolean> = _protectWithPin.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Free Voice-Match Cloning Synthesis state
    private val _cloneScript = MutableStateFlow("")
    val cloneScript: StateFlow<String> = _cloneScript.asStateFlow()

    private val _useOfflineClone = MutableStateFlow(false)
    val useOfflineClone: StateFlow<Boolean> = _useOfflineClone.asStateFlow()

    private val _isSynthesizingClone = MutableStateFlow(false)
    val isSynthesizingClone: StateFlow<Boolean> = _isSynthesizingClone.asStateFlow()

    private val _generatedCloneResult = MutableStateFlow<FreeCloneResult?>(null)
    val generatedCloneResult: StateFlow<FreeCloneResult?> = _generatedCloneResult.asStateFlow()

    private val _generatedClonePeaks = MutableStateFlow<List<Float>>(emptyList())
    val generatedClonePeaks: StateFlow<List<Float>> = _generatedClonePeaks.asStateFlow()

    fun getCloningAvailability(): CloningAvailability {
        val current = settings.value
        return replicationRepository.evaluateAvailability(
            cloningApiBaseUrl = current.cloningApiBaseUrl,
            isNetworkAvailable = isOnline.value,
            offlineOnlyMode = current.offlineOnlyMode
        )
    }

    fun updateCloneScript(text: String) {
        _cloneScript.value = text
    }

    fun setUseOfflineClone(offline: Boolean) {
        _useOfflineClone.value = offline
    }

    fun updateProfileName(value: String) {
        _profileName.value = value
    }

    fun updateSpeakerName(value: String) {
        _speakerName.value = value
    }

    fun selectLanguage(code: String) {
        _selectedLanguage.value = code
    }

    fun setConsentGranted(granted: Boolean) {
        _consentGranted.value = granted
    }

    fun setProtectWithPin(protect: Boolean) {
        _protectWithPin.value = protect
    }

    fun startRecordingSample() {
        _errorMessage.value = null
        val res = recorder.startRecording(viewModelScope, settings.value.audioQuality.sampleRateHz)
        res.onFailure {
            _errorMessage.value = it.localizedMessage ?: "Failed to access microphone."
        }
    }

    fun stopRecordingSample() {
        val capture = recorder.stopAndFinish() ?: run {
            _errorMessage.value = "Recording was too short or empty."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val staged = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                val samplesDir = File(app.filesDir, "samples").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val wavFile = File(samplesDir, "voice_sample_$stamp.wav")
                val pcmBytes = capture.pcmFile.readBytes()
                AudioProcessor.writePcmAsWav(pcmBytes, capture.sampleRateHz, 1, wavFile)
                capture.pcmFile.delete()
                val peaks = AudioProcessor.extractWaveformPeaks(wavFile, 36)
                val fingerprint = AudioProcessor.analyzeVoiceSample(wavFile)
                StagedVoiceSample(
                    filePath = wavFile.absolutePath,
                    displayName = wavFile.name,
                    durationMs = capture.durationMs,
                    fileSizeBytes = wavFile.length(),
                    waveformPeaks = peaks,
                    fingerprint = fingerprint
                )
            }
            _stagedSample.value = staged
            _isBusy.value = false
        }
    }

    fun importAudioSample(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = resolveFileName(uri) ?: "imported_sample.wav"
                    val ext = fileName.substringAfterLast('.', "wav").lowercase(Locale.ROOT)
                    val samplesDir = File(app.filesDir, "samples").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val destFile = File(samplesDir, "import_${stamp}.$ext")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to open selected audio file.")

                    val durationMs = AudioProcessor.probeAudioDurationMs(destFile)
                    val peaks = AudioProcessor.extractWaveformPeaks(destFile, 36)
                    val fingerprint = AudioProcessor.analyzeVoiceSample(destFile)
                    StagedVoiceSample(
                        filePath = destFile.absolutePath,
                        displayName = fileName,
                        durationMs = durationMs,
                        fileSizeBytes = destFile.length(),
                        waveformPeaks = peaks,
                        fingerprint = fingerprint
                    )
                }
            }
            result.fold(
                onSuccess = { sample ->
                    _stagedSample.value = sample
                    if (_profileName.value.isBlank()) {
                        _profileName.value = sample.displayName.substringBeforeLast('.')
                    }
                },
                onFailure = { err ->
                    _errorMessage.value = err.localizedMessage ?: "Failed to import audio sample."
                }
            )
            _isBusy.value = false
        }
    }

    fun loadExistingSamplePath(filePath: String, title: String) {
        viewModelScope.launch {
            val file = File(filePath)
            if (!file.exists()) return@launch
            val staged = withContext(Dispatchers.IO) {
                StagedVoiceSample(
                    filePath = file.absolutePath,
                    displayName = title,
                    durationMs = AudioProcessor.probeAudioDurationMs(file),
                    fileSizeBytes = file.length(),
                    waveformPeaks = AudioProcessor.extractWaveformPeaks(file, 36),
                    fingerprint = AudioProcessor.analyzeVoiceSample(file)
                )
            }
            _stagedSample.value = staged
            if (_profileName.value.isBlank()) {
                _profileName.value = title
            }
        }
    }

    fun saveAuthorizedProfile(sendToReplicationApi: Boolean) {
        val sample = _stagedSample.value
        if (sample == null) {
            _errorMessage.value = "Record or import a voice sample first."
            return
        }
        val cleanProfile = _profileName.value.trim()
        val cleanSpeaker = _speakerName.value.trim()
        if (cleanProfile.isBlank() || cleanSpeaker.isBlank()) {
            _errorMessage.value = "Enter both Voice Profile Title and Voice Owner Full Name."
            return
        }
        val mustConsent = settings.value.requireConsentBeforeClone
        if (mustConsent && !_consentGranted.value) {
            _errorMessage.value = "Explicit voice-owner consent is mandatory before saving or cloning."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            _statusMessage.value = null

            val consentText =
                "Authorized by $cleanSpeaker for profile '$cleanProfile'. Confirmed voice ownership/explicit permission."
            val initialStatus = if (sendToReplicationApi) {
                "SUBMITTING_TO_API"
            } else {
                "LOCAL_SAMPLE_AUTHORIZED"
            }

            val profile = VoiceProfileEntity(
                profileName = cleanProfile,
                speakerName = cleanSpeaker,
                sampleFilePath = sample.filePath,
                durationMs = sample.durationMs,
                fileSizeBytes = sample.fileSizeBytes,
                languageCode = _selectedLanguage.value,
                consentGranted = _consentGranted.value,
                consentStatement = consentText,
                consentTimestamp = System.currentTimeMillis(),
                isProtected = _protectWithPin.value,
                cloningStatus = initialStatus
            )

            val newId = voiceProfileDao.insertProfile(profile)
            audioClipDao.insertClip(
                AudioClipEntity(
                    title = "Voice Sample • $cleanProfile",
                    filePath = sample.filePath,
                    format = if (sample.filePath.endsWith(".mp3", true)) AudioFormat.MP3.name else AudioFormat.WAV.name,
                    sourceType = AudioSourceType.VOICE_SAMPLE.name,
                    languageCode = _selectedLanguage.value,
                    voiceName = cleanSpeaker,
                    durationMs = sample.durationMs,
                    fileSizeBytes = sample.fileSizeBytes,
                    isProtected = _protectWithPin.value
                )
            )

            if (sendToReplicationApi) {
                when (val avail = getCloningAvailability()) {
                    CloningAvailability.NotConfigured -> {
                        voiceProfileDao.updateRemoteStatus(newId, null, "LOCAL_ONLY_NO_API_URL")
                        _errorMessage.value =
                            "Profile & consent saved locally, but no HTTPS Voice Replication API URL is configured in Settings."
                    }
                    CloningAvailability.Offline -> {
                        voiceProfileDao.updateRemoteStatus(newId, null, "PENDING_ONLINE_UPLOAD")
                        _errorMessage.value =
                            "Profile & consent saved locally, but network is offline or Offline-Only Mode is active."
                    }
                    is CloningAvailability.Ready -> {
                        val apiRes = replicationRepository.uploadAuthorizedVoiceSample(
                            endpointUrl = avail.endpointUrl,
                            profile = profile.copy(id = newId)
                        )
                        apiRes.fold(
                            onSuccess = { resp ->
                                voiceProfileDao.updateRemoteStatus(
                                    newId,
                                    resp.voiceId,
                                    resp.status ?: "REPLICATION_SUBMITTED"
                                )
                                _statusMessage.value = resp.message ?: "Authorized voice sample sent to replication API."
                            },
                            onFailure = { err ->
                                voiceProfileDao.updateRemoteStatus(newId, null, "API_UPLOAD_FAILED")
                                _errorMessage.value = err.localizedMessage ?: "Replication API request failed."
                            }
                        )
                    }
                }
            } else {
                _statusMessage.value = "Authorized voice profile and consent record saved locally."
            }

            _isBusy.value = false
        }
    }

    fun generateFreeClonedSpeech() {
        val sample = _stagedSample.value
        if (sample == null) {
            _errorMessage.value = "Record or import a voice sample first."
            return
        }
        val mustConsent = settings.value.requireConsentBeforeClone
        if (mustConsent && !_consentGranted.value) {
            _errorMessage.value = "Please confirm voice-owner consent checkbox above before cloning."
            return
        }
        val script = _cloneScript.value.trim()
        if (script.isBlank()) {
            _errorMessage.value = "Enter Bengali or English text to speak in the cloned voice."
            return
        }

        val currentSettings = settings.value
        val online = networkMonitor.refresh()
        val runOffline = _useOfflineClone.value || currentSettings.offlineOnlyMode || !online
        val label = _profileName.value.trim().ifBlank { sample.displayName.substringBeforeLast('.') }

        viewModelScope.launch {
            _isSynthesizingClone.value = true
            _errorMessage.value = null
            _statusMessage.value = null

            try {
                val res = if (runOffline) {
                    freeCloneRepository.synthesizeFreeOfflineClone(
                        profileName = label,
                        sampleFile = File(sample.filePath),
                        text = script,
                        languageCode = _selectedLanguage.value,
                        outputFormat = currentSettings.defaultFormat,
                        audioQuality = currentSettings.audioQuality
                    )
                } else {
                    freeCloneRepository.synthesizeFreeOnlineClone(
                        profileName = label,
                        sampleFile = File(sample.filePath),
                        text = script,
                        languageCode = _selectedLanguage.value,
                        outputFormat = currentSettings.defaultFormat,
                        audioQuality = currentSettings.audioQuality
                    )
                }

                res.fold(
                    onSuccess = { result ->
                        val peaks = withContext(Dispatchers.IO) {
                            AudioProcessor.extractWaveformPeaks(File(result.clip.filePath), 40)
                        }
                        _generatedCloneResult.value = result
                        _generatedClonePeaks.value = peaks
                        _statusMessage.value =
                            "Free voice clone generated (${result.baseVoiceUsed} matched to ${result.fingerprint.fundamentalFreqHz.toInt()} Hz)."
                        player.playFile(result.clip.filePath, result.clip.title)
                    },
                    onFailure = { err ->
                        _errorMessage.value = err.localizedMessage ?: "Free voice cloning synthesis failed."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Free voice cloning error."
            } finally {
                _isSynthesizingClone.value = false
            }
        }
    }

    fun togglePlayGeneratedClone() {
        val clip = _generatedCloneResult.value?.clip ?: return
        player.togglePlayPause(clip.filePath, clip.title)
    }

    fun togglePreviewSample() {
        val sample = _stagedSample.value ?: return
        player.togglePlayPause(sample.filePath, sample.displayName)
    }

    fun clearMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    private fun resolveFileName(uri: Uri): String? {
        val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
        }
    }

    override fun onCleared() {
        recorder.cancelRecording()
        super.onCleared()
    }
}
