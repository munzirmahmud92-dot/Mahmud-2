package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AudioSourceType {
    GEMINI_TTS,
    OFFLINE_TTS,
    STUDIO_RECORDING,
    VOICE_SAMPLE,
    CLONED_API
}

enum class AudioFormat(val extension: String, val mimeType: String) {
    WAV("wav", "audio/wav"),
    MP3("mp3", "audio/mpeg")
}

@Entity(tableName = "audio_clips")
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: String = AudioFormat.WAV.name,
    val sourceType: String = AudioSourceType.STUDIO_RECORDING.name,
    val languageCode: String = "en-US",
    val voiceName: String = "",
    val scriptPreview: String = "",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val sampleRateHz: Int = 24000,
    val isProtected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val speakerName: String,
    val sampleFilePath: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val languageCode: String = "bn-IN",
    val consentGranted: Boolean = false,
    val consentStatement: String = "",
    val consentTimestamp: Long = 0L,
    val isProtected: Boolean = false,
    val remoteVoiceId: String? = null,
    val cloningStatus: String = "LOCAL_SAMPLE_AUTHORIZED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_usage")
data class ApiUsageEntity(
    @PrimaryKey val dateKey: String,
    val requestCount: Int = 0,
    val charactersProcessed: Int = 0,
    val lastError: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
