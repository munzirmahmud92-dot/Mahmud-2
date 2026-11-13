package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class OnlineVoiceCloneRoute(
    val samplePath: String? = null,
    val sampleTitle: String? = null
)

@Serializable
data object OfflineStudioRoute

@Serializable
data object VoiceProfilesRoute

@Serializable
data class TextToSpeechRoute(
    val initialText: String? = null,
    val voiceId: String? = null,
    val languageCode: String? = null,
    val speed: Float? = null,
    val pitch: Float? = null
)

@Serializable
data object AiStudioRoute

@Serializable
data object AudioLibraryRoute

@Serializable
data object RecordingStudioRoute

@Serializable
data object SettingsRoute
