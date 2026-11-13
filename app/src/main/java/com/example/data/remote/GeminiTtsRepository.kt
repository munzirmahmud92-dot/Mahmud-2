package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.audio.AudioProcessor
import com.example.data.local.ApiUsageDao
import com.example.data.local.ApiUsageEntity
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.local.AudioSourceType
import com.example.data.preferences.AudioQuality
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeminiVoicePreset(
    val id: String,
    val style: String,
    val gender: String
)

data class GeminiLanguageOption(
    val code: String?,
    val labelEn: String,
    val labelBn: String
)

class GeminiTtsRepository(
    private val context: Context,
    private val audioClipDao: AudioClipDao,
    private val apiUsageDao: ApiUsageDao
) {
    companion object {
        private const val TAG = "GeminiTtsRepository"
        const val MODEL_NAME = "gemini-3.1-flash-tts-preview"
        const val DAILY_SOFT_QUOTA_REQUESTS = 50

        val AVAILABLE_VOICES = listOf(
            GeminiVoicePreset("Kore", "Firm & Crisp", "Female"),
            GeminiVoicePreset("Puck", "Upbeat & Natural", "Male"),
            GeminiVoicePreset("Zephyr", "Bright & Clear", "Female"),
            GeminiVoicePreset("Charon", "Informative", "Male"),
            GeminiVoicePreset("Aoede", "Breezy & Smooth", "Female"),
            GeminiVoicePreset("Sulafat", "Warm & Conversational", "Female"),
            GeminiVoicePreset("Fenrir", "Excitable & Dynamic", "Male"),
            GeminiVoicePreset("Orus", "Firm Studio", "Male"),
            GeminiVoicePreset("Leda", "Youthful", "Female"),
            GeminiVoicePreset("Iapetus", "Clear Narration", "Male"),
            GeminiVoicePreset("Vindemiatrix", "Gentle", "Female"),
            GeminiVoicePreset("Achird", "Friendly", "Male")
        )

        val SUPPORTED_LANGUAGES = listOf(
            GeminiLanguageOption(null, "Auto Detect (BN / EN)", "স্বয়ংক্রিয় শনাক্তকরণ"),
            GeminiLanguageOption("bn-IN", "Bengali (বাংলা)", "বাংলা (Bengali)"),
            GeminiLanguageOption("en-US", "English (US)", "ইংরেজি (US)"),
            GeminiLanguageOption("en-GB", "English (UK)", "ইংরেজি (UK)"),
            GeminiLanguageOption("en-IN", "English (India)", "ইংরেজি (India)")
        )
    }

    private val aiService by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun observeTodayUsage(): Flow<ApiUsageEntity?> =
        apiUsageDao.observeUsageForDate(todayDateKey())

    @OptIn(PublicPreviewAPI::class)
    suspend fun synthesizeRawPcm(
        text: String,
        voiceName: String,
        languageCode: String?
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Text cannot be empty."))
        }
        try {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = if (languageCode != null) {
                    SpeechConfig(
                        voice = Voice(voiceName),
                        languageCode = languageCode
                    )
                } else {
                    SpeechConfig(
                        voice = Voice(voiceName)
                    )
                }
            }

            val model = aiService.generativeModel(
                modelName = MODEL_NAME,
                generationConfig = config
            )

            val response = withTimeout(60_000L) {
                model.generateContent(cleanText)
            }

            val audioStream = ByteArrayOutputStream()
            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            for (part in parts) {
                if (part is InlineDataPart && part.mimeType.startsWith("audio/", ignoreCase = true)) {
                    audioStream.write(part.inlineData)
                }
            }

            val rawPcm = audioStream.toByteArray()
            if (rawPcm.isEmpty()) {
                val fallbackMsg = response.text?.take(140)
                recordUsage(chars = 0, error = fallbackMsg ?: "Model returned no audio bytes.")
                return@withContext Result.failure(
                    IllegalStateException(
                        fallbackMsg?.let { "Gemini TTS returned text instead of audio: $it" }
                            ?: "Gemini TTS returned empty audio data."
                    )
                )
            }
            recordUsage(chars = cleanText.length, error = null)
            Result.success(rawPcm)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini TTS timed out", e)
            recordUsage(chars = 0, error = "Request timed out after 60 seconds")
            Result.failure(IllegalStateException("Gemini TTS timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini TTS failed", e)
            val msg = e.localizedMessage ?: "Gemini TTS request failed."
            recordUsage(chars = 0, error = msg)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun generateSpeech(
        text: String,
        voiceName: String,
        languageCode: String?,
        speed: Float,
        pitch: Float,
        outputFormat: AudioFormat,
        audioQuality: AudioQuality
    ): Result<AudioClipEntity> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        val rawResult = synthesizeRawPcm(cleanText, voiceName, languageCode)
        val rawPcm = rawResult.getOrElse { return@withContext Result.failure(it) }

        try {

            // Gemini TTS outputs 24kHz 16-bit mono PCM
            val processedPcm = AudioProcessor.processPcm(
                pcm16 = rawPcm,
                sourceRateHz = 24000,
                targetRateHz = audioQuality.sampleRateHz,
                speed = speed,
                pitch = pitch
            )

            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(audioDir, "gemini_${voiceName.lowercase(Locale.ROOT)}_$stamp.${outputFormat.extension}")

            when (outputFormat) {
                AudioFormat.WAV -> {
                    AudioProcessor.writePcmAsWav(
                        pcm16 = processedPcm,
                        sampleRateHz = audioQuality.sampleRateHz,
                        channels = 1,
                        outFile = outFile
                    )
                }
                AudioFormat.MP3 -> {
                    AudioProcessor.writePcmAsMp3(
                        pcm16 = processedPcm,
                        sampleRateHz = audioQuality.sampleRateHz,
                        channels = 1,
                        bitrateKbps = audioQuality.mp3BitrateKbps,
                        outFile = outFile
                    )
                }
            }

            val durationMs = AudioProcessor.calculatePcmDurationMs(
                pcmByteLength = processedPcm.size.toLong(),
                sampleRateHz = audioQuality.sampleRateHz,
                channels = 1
            )

            val clip = AudioClipEntity(
                title = "Gemini $voiceName • ${cleanText.take(24)}",
                filePath = outFile.absolutePath,
                format = outputFormat.name,
                sourceType = AudioSourceType.GEMINI_TTS.name,
                languageCode = languageCode ?: "auto",
                voiceName = voiceName,
                scriptPreview = cleanText.take(120),
                durationMs = durationMs,
                fileSizeBytes = outFile.length(),
                sampleRateHz = audioQuality.sampleRateHz,
                isProtected = false
            )
            val insertedId = audioClipDao.insertClip(clip)
            Result.success(clip.copy(id = insertedId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini audio processing failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Audio processing failed.", e))
        }
    }

    private suspend fun recordUsage(chars: Int, error: String?) {
        val key = todayDateKey()
        val existing = apiUsageDao.getUsageForDate(key)
        val updated = ApiUsageEntity(
            dateKey = key,
            requestCount = (existing?.requestCount ?: 0) + 1,
            charactersProcessed = (existing?.charactersProcessed ?: 0) + chars,
            lastError = error,
            lastUpdatedAt = System.currentTimeMillis()
        )
        apiUsageDao.upsertUsage(updated)
    }
}
