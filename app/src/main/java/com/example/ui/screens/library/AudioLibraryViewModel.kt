package com.example.ui.screens.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExporter
import com.example.audio.StudioAudioPlayer
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.local.AudioSourceType
import com.example.data.preferences.AudioQuality
import com.example.data.preferences.SettingsRepository
import com.example.data.preferences.StudioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class LibraryFilter {
    ALL,
    ONLINE_TTS,
    OFFLINE_TTS,
    RECORDINGS
}

class AudioLibraryViewModel(
    application: Application,
    private val audioClipDao: AudioClipDao,
    private val settingsRepository: SettingsRepository,
    val player: StudioAudioPlayer
) : AndroidViewModel(application) {

    val settings: StateFlow<StudioSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    val filteredClips: StateFlow<List<AudioClipEntity>> = combine(
        audioClipDao.observeAllClips(),
        _searchQuery,
        _filter
    ) { clips, query, activeFilter ->
        clips.filter { clip ->
            val matchesFilter = when (activeFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.ONLINE_TTS -> clip.sourceType == AudioSourceType.GEMINI_TTS.name || clip.sourceType == AudioSourceType.CLONED_API.name
                LibraryFilter.OFFLINE_TTS -> clip.sourceType == AudioSourceType.OFFLINE_TTS.name
                LibraryFilter.RECORDINGS -> clip.sourceType == AudioSourceType.STUDIO_RECORDING.name || clip.sourceType == AudioSourceType.VOICE_SAMPLE.name
            }
            val q = query.trim()
            val matchesQuery = q.isBlank() ||
                clip.title.contains(q, ignoreCase = true) ||
                clip.voiceName.contains(q, ignoreCase = true) ||
                clip.scriptPreview.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    fun togglePlay(clip: AudioClipEntity) {
        player.togglePlayPause(clip.filePath, clip.title)
    }

    fun renameClip(id: Long, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            audioClipDao.renameClip(id, clean)
        }
    }

    fun deleteClip(clip: AudioClipEntity) {
        if (player.state.value.activeFilePath == clip.filePath) {
            player.stop()
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(clip.filePath).delete() }
            }
            audioClipDao.deleteClipById(clip.id)
        }
    }

    fun exportToPhoneStorage(clip: AudioClipEntity, format: AudioFormat) {
        viewModelScope.launch {
            _isExporting.value = true
            val res = AudioExporter.saveClipToMediaStore(
                context = getApplication(),
                clip = clip,
                targetFormat = format,
                bitrateKbps = settings.value.audioQuality.mp3BitrateKbps
            )
            res.fold(
                onSuccess = { savedName ->
                    _statusMessage.value = "Exported to Music/MunzirVoiceCloner/$savedName"
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Export failed."
                }
            )
            _isExporting.value = false
        }
    }

    fun exportToUri(clip: AudioClipEntity, format: AudioFormat, destUri: Uri) {
        viewModelScope.launch {
            _isExporting.value = true
            val res = AudioExporter.writeClipToUri(
                context = getApplication(),
                clip = clip,
                targetFormat = format,
                bitrateKbps = settings.value.audioQuality.mp3BitrateKbps,
                destUri = destUri
            )
            res.fold(
                onSuccess = { _statusMessage.value = "Saved ${format.name} file successfully." },
                onFailure = { _statusMessage.value = it.localizedMessage ?: "Failed to save file." }
            )
            _isExporting.value = false
        }
    }

    fun shareClip(clip: AudioClipEntity, format: AudioFormat) {
        viewModelScope.launch {
            _isExporting.value = true
            val res = AudioExporter.createShareIntent(
                context = getApplication(),
                clip = clip,
                targetFormat = format,
                bitrateKbps = settings.value.audioQuality.mp3BitrateKbps
            )
            res.fold(
                onSuccess = { intent ->
                    val chooser = Intent.createChooser(intent, clip.title).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    getApplication<Application>().startActivity(chooser)
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Share failed."
                }
            )
            _isExporting.value = false
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }
}
