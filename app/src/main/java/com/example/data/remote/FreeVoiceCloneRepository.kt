package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.audio.AudioProcessor
import com.example.audio.AudioProcessor.AcousticVoiceFingerprint
import com.example.audio.OfflineTtsEngine
import com.example.data.local.AudioClipDao
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import com.example.data.local.AudioSourceType
import com.example.data.preferences.AudioQuality
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FreeCloneResult(
    val clip: AudioClipEntity,
    val baseVoiceUsed: String,
    val fingerprint: AcousticVoiceFingerprint,
    val usedOfflineEngine: Boolean
)

class FreeVoiceCloneRepository(
    private val context: Context,
    private val geminiTtsRepository: GeminiTtsRepository,
    private val offlineTtsEngine: OfflineTtsEngine,
    private val audioClipDao: AudioClipDao
) {
    companion object {
        private const val TAG = "FreeVoiceCloneRepo"
        private const val MAX_INLINE_AUDIO_BYTES = 10L * 1024L * 1024L

        private val BASE_VOICE_F0_HZ = mapOf(
            "Charon" to 118f,
            "Orus" to 122f,
            "Iapetus" to 126f,
            "Fenrir" to 132f,
            "Puck" to 136f,
            "Achird" to 140f,
            "Sulafat" to 188f,
            "Aoede" to 198f,
            "Kore" to 206f,
            "Vindemiatrix" to 212f,
            "Zephyr" to 218f,
            "Leda" to 224f
        )
    }

    private val aiBackend by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val voiceMatcherSchema = Schema.obj(
        properties = mapOf(
            "matchedVoiceId" to Schema.enumeration(
                values = GeminiTtsRepository.AVAILABLE_VOICES.map { it.id },
                description = "Gemini TTS base voice closest in vocal register and tone to the sample audio"
            ),
            "estimatedSpeed" to Schema.double(
                description = "Natural speaking speed multiplier between 0.85 and 1.20"
            )
        )
    )

    private val voiceMatcherModel by lazy {
        aiBackend.generativeModel(
            modelName = GeminiAiStudioRepository.TEXT_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceMatcherSchema
            }
        )
    }

    suspend fun analyzeSample(sampleFile: File): AcousticVoiceFingerprint = withContext(Dispatchers.IO) {
        AudioProcessor.analyzeVoiceSample(sampleFile)
    }

    suspend fun synthesizeFreeOnlineClone(
        profileName: String,
        sampleFile: File,
        text: String,
        languageCode: String,
        outputFormat: AudioFormat,
        audioQuality: AudioQuality
    ): Result<FreeCloneResult> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter Bengali or English text to speak."))
        }
        if (!sampleFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Recorded or imported voice sample file is missing."))
        }

        try {
            val fingerprint = AudioProcessor.analyzeVoiceSample(sampleFile)
            val (matchedVoiceId, matchedSpeed) = matchGeminiBaseVoice(sampleFile, fingerprint)

            val rawPcm = geminiTtsRepository.synthesizeRawPcm(
                text = cleanText,
                voiceName = matchedVoiceId,
                languageCode = languageCode
            ).getOrElse { return@withContext Result.failure(it) }

            val baseF0 = BASE_VOICE_F0_HZ[matchedVoiceId] ?: 155f
            val speedAdjusted = if (kotlin.math.abs(matchedSpeed - 1.0f) > 0.04f) {
                AudioProcessor.processPcm(
                    pcm16 = rawPcm,
                    sourceRateHz = 24000,
                    targetRateHz = 24000,
                    speed = matchedSpeed,
                    pitch = 1.0f
                )
            } else {
                rawPcm
            }

            val clonedPcm = AudioProcessor.applyAcousticTimbreMatch(
                pcm16 = speedAdjusted,
                sourceRateHz = 24000,
                targetRateHz = audioQuality.sampleRateHz,
                targetF0Hz = fingerprint.fundamentalFreqHz,
                baseVoiceF0Hz = baseF0,
                targetBrightness = fingerprint.spectralBrightness
            )

            val savedClip = writeAndSaveClip(
                pcm16 = clonedPcm,
                profileName = profileName,
                voiceLabel = "FreeClone ($matchedVoiceId • ${fingerprint.fundamentalFreqHz.toInt()}Hz)",
                text = cleanText,
                languageCode = languageCode,
                outputFormat = outputFormat,
                audioQuality = audioQuality
            )

            Result.success(
                FreeCloneResult(
                    clip = savedClip,
                    baseVoiceUsed = matchedVoiceId,
                    fingerprint = fingerprint,
                    usedOfflineEngine = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Free online clone failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Free voice cloning failed.", e))
        }
    }

    suspend fun synthesizeFreeOfflineClone(
        profileName: String,
        sampleFile: File,
        text: String,
        languageCode: String,
        outputFormat: AudioFormat,
        audioQuality: AudioQuality
    ): Result<FreeCloneResult> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter Bengali or English text to speak."))
        }
        if (!sampleFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Voice sample file is missing."))
        }

        val tempWav = File(context.cacheDir, "clone_offline_tmp_${System.currentTimeMillis()}.wav")
        try {
            val fingerprint = AudioProcessor.analyzeVoiceSample(sampleFile)

            val synthRes = offlineTtsEngine.synthesizeToWavFile(
                text = cleanText,
                voiceName = null,
                languageTag = languageCode,
                speed = 1.0f,
                pitch = 1.0f,
                outFile = tempWav
            )
            synthRes.getOrElse { return@withContext Result.failure(it) }

            val decoded = AudioProcessor.decodeAudioFileToPcm(tempWav)
                ?: return@withContext Result.failure(IllegalStateException("Could not decode offline TTS audio."))

            val baseFingerprint = AudioProcessor.analyzeVoiceSample(tempWav)
            val clonedPcm = AudioProcessor.applyAcousticTimbreMatch(
                pcm16 = decoded.pcmBytes,
                sourceRateHz = decoded.sampleRateHz,
                targetRateHz = audioQuality.sampleRateHz,
                targetF0Hz = fingerprint.fundamentalFreqHz,
                baseVoiceF0Hz = baseFingerprint.fundamentalFreqHz,
                targetBrightness = fingerprint.spectralBrightness
            )

            val savedClip = writeAndSaveClip(
                pcm16 = clonedPcm,
                profileName = profileName,
                voiceLabel = "OfflineClone (${fingerprint.fundamentalFreqHz.toInt()}Hz)",
                text = cleanText,
                languageCode = languageCode,
                outputFormat = outputFormat,
                audioQuality = audioQuality
            )

            Result.success(
                FreeCloneResult(
                    clip = savedClip,
                    baseVoiceUsed = "On-Device TTS + Acoustic DSP",
                    fingerprint = fingerprint,
                    usedOfflineEngine = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Free offline clone failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Offline voice clone failed.", e))
        } finally {
            runCatching { tempWav.delete() }
        }
    }

    private suspend fun matchGeminiBaseVoice(
        sampleFile: File,
        fingerprint: AcousticVoiceFingerprint
    ): Pair<String, Float> {
        val fallbackVoice = when {
            fingerprint.fundamentalFreqHz < 130f -> "Charon"
            fingerprint.fundamentalFreqHz < 152f -> "Puck"
            fingerprint.fundamentalFreqHz < 192f -> "Sulafat"
            else -> "Kore"
        }

        val mimeType = when (sampleFile.extension.lowercase(Locale.ROOT)) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/opus"
            else -> null
        } ?: return fallbackVoice to 1.0f

        if (sampleFile.length() !in 1024L..MAX_INLINE_AUDIO_BYTES) {
            return fallbackVoice to 1.0f
        }

        return try {
            val audioBytes = sampleFile.readBytes()
            val voiceCatalog = GeminiTtsRepository.AVAILABLE_VOICES.joinToString(", ") {
                "${it.id}(${it.gender},${it.style})"
            }
            val promptContent = content {
                inlineData(audioBytes, mimeType)
                text(
                    "Listen to this speaker's voice sample. Measured fundamental pitch is ${fingerprint.fundamentalFreqHz.toInt()} Hz. " +
                        "Pick the single closest base voice from [$voiceCatalog] and estimate speaking speed (0.85 to 1.20)."
                )
            }
            val response = withTimeout(25_000L) {
                voiceMatcherModel.generateContent(promptContent)
            }
            val raw = response.text ?: return fallbackVoice to 1.0f
            val json = JSONObject(raw)
            val id = json.optString("matchedVoiceId", fallbackVoice)
                .takeIf { candidate -> GeminiTtsRepository.AVAILABLE_VOICES.any { it.id == candidate } }
                ?: fallbackVoice
            val speed = json.optDouble("estimatedSpeed", 1.0).toFloat().coerceIn(0.85f, 1.2f)
            id to speed
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Multimodal voice match timed out; using F0 fallback", e)
            fallbackVoice to 1.0f
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Multimodal voice match fallback: ${e.localizedMessage}")
            fallbackVoice to 1.0f
        }
    }

    private suspend fun writeAndSaveClip(
        pcm16: ByteArray,
        profileName: String,
        voiceLabel: String,
        text: String,
        languageCode: String,
        outputFormat: AudioFormat,
        audioQuality: AudioQuality
    ): AudioClipEntity {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = profileName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "voice" }.take(18)
        val outFile = File(audioDir, "clone_${safeName}_$stamp.${outputFormat.extension}")

        when (outputFormat) {
            AudioFormat.WAV -> AudioProcessor.writePcmAsWav(
                pcm16 = pcm16,
                sampleRateHz = audioQuality.sampleRateHz,
                channels = 1,
                outFile = outFile
            )
            AudioFormat.MP3 -> AudioProcessor.writePcmAsMp3(
                pcm16 = pcm16,
                sampleRateHz = audioQuality.sampleRateHz,
                channels = 1,
                bitrateKbps = audioQuality.mp3BitrateKbps,
                outFile = outFile
            )
        }

        val durationMs = AudioProcessor.calculatePcmDurationMs(
            pcmByteLength = pcm16.size.toLong(),
            sampleRateHz = audioQuality.sampleRateHz,
            channels = 1
        )

        val clip = AudioClipEntity(
            title = "Clone ($profileName) • ${text.take(22)}",
            filePath = outFile.absolutePath,
            format = outputFormat.name,
            sourceType = AudioSourceType.CLONED_API.name,
            languageCode = languageCode,
            voiceName = voiceLabel,
            scriptPreview = text.take(120),
            durationMs = durationMs,
            fileSizeBytes = outFile.length(),
            sampleRateHz = audioQuality.sampleRateHz,
            isProtected = false
        )
        val id = audioClipDao.insertClip(clip)
        return clip.copy(id = id)
    }
}
