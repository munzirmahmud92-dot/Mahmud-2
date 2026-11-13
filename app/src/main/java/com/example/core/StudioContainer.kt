package com.example.core

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audio.OfflineTtsEngine
import com.example.audio.StudioAudioPlayer
import com.example.data.local.StudioDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.remote.FreeVoiceCloneRepository
import com.example.data.remote.GeminiAiStudioRepository
import com.example.data.remote.GeminiTtsRepository
import com.example.data.remote.VoiceReplicationRepository
import com.example.ui.screens.aistudio.AiStudioViewModel
import com.example.ui.screens.clone.VoiceCloneViewModel
import com.example.ui.screens.library.AudioLibraryViewModel
import com.example.ui.screens.offline.OfflineStudioViewModel
import com.example.ui.screens.profiles.VoiceProfilesViewModel
import com.example.ui.screens.recorder.RecordingStudioViewModel
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.tts.TextToSpeechViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StudioAppContainer(val application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: StudioDatabase by lazy { StudioDatabase.getInstance(application) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(application) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(application) }
    val offlineTtsEngine: OfflineTtsEngine by lazy { OfflineTtsEngine(application) }
    val audioPlayer: StudioAudioPlayer by lazy { StudioAudioPlayer(appScope) }
    val voiceReplicationRepository: VoiceReplicationRepository by lazy { VoiceReplicationRepository() }
    val geminiAiStudioRepository: GeminiAiStudioRepository by lazy {
        GeminiAiStudioRepository(apiUsageDao = database.apiUsageDao())
    }

    val geminiTtsRepository: GeminiTtsRepository by lazy {
        GeminiTtsRepository(
            context = application,
            audioClipDao = database.audioClipDao(),
            apiUsageDao = database.apiUsageDao()
        )
    }

    val freeVoiceCloneRepository: FreeVoiceCloneRepository by lazy {
        FreeVoiceCloneRepository(
            context = application,
            geminiTtsRepository = geminiTtsRepository,
            offlineTtsEngine = offlineTtsEngine,
            audioClipDao = database.audioClipDao()
        )
    }

    fun release() {
        audioPlayer.stop()
        offlineTtsEngine.release()
    }
}

class StudioViewModelFactory(
    private val container: StudioAppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(TextToSpeechViewModel::class.java) -> TextToSpeechViewModel(
                geminiTtsRepository = container.geminiTtsRepository,
                geminiAiStudioRepository = container.geminiAiStudioRepository,
                settingsRepository = container.settingsRepository,
                networkMonitor = container.networkMonitor,
                player = container.audioPlayer
            ) as T

            modelClass.isAssignableFrom(AiStudioViewModel::class.java) -> AiStudioViewModel(
                aiStudioRepository = container.geminiAiStudioRepository,
                settingsRepository = container.settingsRepository,
                networkMonitor = container.networkMonitor
            ) as T

            modelClass.isAssignableFrom(VoiceCloneViewModel::class.java) -> VoiceCloneViewModel(
                application = container.application,
                voiceProfileDao = container.database.voiceProfileDao(),
                audioClipDao = container.database.audioClipDao(),
                settingsRepository = container.settingsRepository,
                replicationRepository = container.voiceReplicationRepository,
                freeCloneRepository = container.freeVoiceCloneRepository,
                networkMonitor = container.networkMonitor,
                player = container.audioPlayer
            ) as T

            modelClass.isAssignableFrom(OfflineStudioViewModel::class.java) -> OfflineStudioViewModel(
                application = container.application,
                offlineTtsEngine = container.offlineTtsEngine,
                audioClipDao = container.database.audioClipDao(),
                settingsRepository = container.settingsRepository,
                networkMonitor = container.networkMonitor,
                player = container.audioPlayer
            ) as T

            modelClass.isAssignableFrom(VoiceProfilesViewModel::class.java) -> VoiceProfilesViewModel(
                voiceProfileDao = container.database.voiceProfileDao(),
                settingsRepository = container.settingsRepository
            ) as T

            modelClass.isAssignableFrom(AudioLibraryViewModel::class.java) -> AudioLibraryViewModel(
                application = container.application,
                audioClipDao = container.database.audioClipDao(),
                settingsRepository = container.settingsRepository,
                player = container.audioPlayer
            ) as T

            modelClass.isAssignableFrom(RecordingStudioViewModel::class.java) -> RecordingStudioViewModel(
                application = container.application,
                audioClipDao = container.database.audioClipDao(),
                settingsRepository = container.settingsRepository,
                player = container.audioPlayer
            ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                application = container.application,
                settingsRepository = container.settingsRepository,
                audioClipDao = container.database.audioClipDao()
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
