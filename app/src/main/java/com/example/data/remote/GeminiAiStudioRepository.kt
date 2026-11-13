package com.example.data.remote

import android.util.Log
import com.example.data.local.ApiUsageDao
import com.example.data.local.ApiUsageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiVoiceDirectionPlan(
    val recommendedVoiceId: String,
    val recommendedLanguageCode: String,
    val recommendedSpeed: Float,
    val recommendedPitch: Float,
    val deliveryStyle: String,
    val coachingTip: String
)

enum class AiScriptPreset(
    val id: String,
    val labelEn: String,
    val labelBn: String,
    val promptHint: String
) {
    YOUTUBE_INTRO(
        id = "youtube",
        labelEn = "YouTube Intro",
        labelBn = "ইউটিউব ইন্ট্রো",
        promptHint = "an engaging, high-retention YouTube video intro voiceover"
    ),
    PODCAST_OPEN(
        id = "podcast",
        labelEn = "Podcast Open",
        labelBn = "পডকাস্ট শুরু",
        promptHint = "a warm, atmospheric podcast opening monologue"
    ),
    PRODUCT_PROMO(
        id = "promo",
        labelEn = "Promo Ad",
        labelBn = "প্রোমো বিজ্ঞাপন",
        promptHint = "a crisp, persuasive 30-second commercial voiceover script"
    ),
    STORY_NARRATION(
        id = "story",
        labelEn = "Storytelling",
        labelBn = "গল্প পাঠ",
        promptHint = "an expressive, cinematic short story narration"
    ),
    NEWS_BULLETIN(
        id = "news",
        labelEn = "News Anchor",
        labelBn = "সংবাদ পাঠ",
        promptHint = "a clear, authoritative studio news bulletin announcement"
    )
}

class GeminiAiStudioRepository(
    private val apiUsageDao: ApiUsageDao
) {
    companion object {
        private const val TAG = "GeminiAiStudioRepo"
        const val TEXT_MODEL_NAME = "gemini-3.8-flash"
    }

    private val aiBackend by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val studioTextModel by lazy {
        aiBackend.generativeModel(
            modelName = TEXT_MODEL_NAME,
            systemInstruction = content {
                text(
                    "You are the AI Voice Director and Bilingual Script Assistant for Munzir Voice Cloner Studio. " +
                        "You help creators write crisp, natural voiceover scripts in Bengali (বাংলা) and English, " +
                        "translate naturally for spoken audio, and advise on pacing, clarity, and studio mic technique. " +
                        "When asked for a script ready for Text-to-Speech, output clean spoken script text without markdown formatting like ** or #."
                )
            }
        )
    }

    private val voiceCoachSchema = Schema.obj(
        properties = mapOf(
            "recommendedVoiceId" to Schema.enumeration(
                values = GeminiTtsRepository.AVAILABLE_VOICES.map { it.id },
                description = "Exact Gemini TTS voice preset ID best suited for the script"
            ),
            "recommendedLanguageCode" to Schema.enumeration(
                values = listOf("bn-IN", "en-US", "en-GB", "en-IN"),
                description = "BCP-47 language code matching the script language"
            ),
            "recommendedSpeed" to Schema.double(
                description = "Playback speed multiplier between 0.75 and 1.40"
            ),
            "recommendedPitch" to Schema.double(
                description = "Voice pitch multiplier between 0.80 and 1.25"
            ),
            "deliveryStyle" to Schema.string(
                description = "Short 2-4 word label describing vocal mood and pacing"
            ),
            "coachingTip" to Schema.string(
                description = "Practical 1-2 sentence voice direction tip in the requested feedback language"
            )
        )
    )

    private val voiceCoachModel by lazy {
        aiBackend.generativeModel(
            modelName = TEXT_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceCoachSchema
            }
        )
    }

    fun observeTodayUsage(): Flow<ApiUsageEntity?> =
        apiUsageDao.observeUsageForDate(todayDateKey())

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun streamScriptGeneration(
        topic: String,
        preset: AiScriptPreset,
        targetLanguageCode: String,
        tone: String
    ): Flow<String> = flow {
        val langInstruction = if (targetLanguageCode.startsWith("bn", ignoreCase = true)) {
            "natural, fluent Bengali (বাংলা script)"
        } else {
            "clear, natural English"
        }
        val prompt = """
            Write ${preset.promptHint} in $langInstruction.
            Topic / Brief: $topic
            Desired Vocal Tone: $tone
            Requirements:
            - 45 to 90 words long, formatted for smooth Text-to-Speech narration.
            - No stage directions, no brackets, no markdown symbols, no bullet lists — output only the spoken script.
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                studioTextModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini AI returned an empty script.")
            }
            recordUsage(chars = accumulated.length, error = null)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Script stream timed out", e)
            recordUsage(chars = 0, error = "AI script request timed out")
            throw IllegalStateException("AI request timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Script stream failed", e)
            val msg = e.localizedMessage ?: "AI script generation failed."
            recordUsage(chars = 0, error = msg)
            throw IllegalStateException(msg, e)
        }
    }.flowOn(Dispatchers.IO)

    fun streamTransformText(
        sourceText: String,
        mode: String
    ): Flow<String> = flow {
        val instruction = when (mode) {
            "TRANSLATE_TO_BN" -> "Translate this voiceover script into natural, expressive spoken Bengali (বাংলা). Output only the translated spoken text without quotes or markdown."
            "TRANSLATE_TO_EN" -> "Translate this voiceover script into clear, natural spoken English. Output only the translated spoken text without quotes or markdown."
            "POLISH_TTS" -> "Refine punctuation, rhythm, and phrasing of this script in its original language so it sounds smooth and studio-ready for Text-to-Speech. Output only the refined spoken text without markdown."
            else -> "Improve this voiceover script for natural spoken audio delivery. Output only the spoken text."
        }
        val prompt = "$instruction\n\nScript:\n$sourceText"
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                studioTextModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini AI returned an empty response.")
            }
            recordUsage(chars = accumulated.length, error = null)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Transform stream timed out", e)
            recordUsage(chars = 0, error = "AI transform timed out")
            throw IllegalStateException("AI request timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Transform stream failed", e)
            val msg = e.localizedMessage ?: "AI text processing failed."
            recordUsage(chars = 0, error = msg)
            throw IllegalStateException(msg, e)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun analyzeVoiceDirection(
        script: String,
        feedbackInBengali: Boolean
    ): Result<AiVoiceDirectionPlan> = withContext(Dispatchers.IO) {
        val clean = script.trim()
        if (clean.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a script to analyze."))
        }
        val voiceCatalog = GeminiTtsRepository.AVAILABLE_VOICES.joinToString("\n") {
            "- ${it.id}: ${it.style} (${it.gender})"
        }
        val tipLang = if (feedbackInBengali) "Bengali (বাংলা)" else "English"
        val prompt = """
            Analyze the following script and recommend the best Gemini TTS configuration.
            Available voices:
            $voiceCatalog

            Write coachingTip in $tipLang.
            Script:
            $clean
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                voiceCoachModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty JSON response from Gemini.")
            val json = JSONObject(rawJson)
            val voiceId = json.optString("recommendedVoiceId", "Kore")
                .takeIf { id -> GeminiTtsRepository.AVAILABLE_VOICES.any { it.id == id } }
                ?: "Kore"
            val langCode = json.optString("recommendedLanguageCode", "bn-IN")
            val speed = json.optDouble("recommendedSpeed", 1.0).toFloat().coerceIn(0.75f, 1.4f)
            val pitch = json.optDouble("recommendedPitch", 1.0).toFloat().coerceIn(0.8f, 1.25f)
            val style = json.optString("deliveryStyle", "Studio Balanced")
            val tip = json.optString("coachingTip", "")

            recordUsage(chars = clean.length, error = null)
            Result.success(
                AiVoiceDirectionPlan(
                    recommendedVoiceId = voiceId,
                    recommendedLanguageCode = langCode,
                    recommendedSpeed = speed,
                    recommendedPitch = pitch,
                    deliveryStyle = style,
                    coachingTip = tip
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice direction timed out", e)
            recordUsage(chars = 0, error = "Voice direction timed out")
            Result.failure(IllegalStateException("AI Voice Coach timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice direction failed", e)
            val msg = e.localizedMessage ?: "AI Voice Coach failed."
            recordUsage(chars = 0, error = msg)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    fun createChatSession(): Chat {
        return studioTextModel.startChat(
            history = listOf(
                content("user") {
                    text("Introduce yourself briefly as the Munzir Voice Cloner AI Studio Assistant.")
                },
                content("model") {
                    text(
                        "আসসালামু আলাইকুম! আমি মনজির ভয়েস ক্রোনার এআই স্টুডিও অ্যাসিস্ট্যান্ট। " +
                            "বাংলা বা ইংরেজি ভয়েসওভার স্ক্রিপ্ট লেখা, অনুবাদ, বা রেকর্ডিং পরামর্শের জন্য আমাকে জিজ্ঞাসা করুন!"
                    )
                }
            )
        )
    }

    fun streamChatMessage(chat: Chat, message: String): Flow<String> = flow {
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(message).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI Assistant returned an empty reply.")
            }
            recordUsage(chars = accumulated.length, error = null)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat timed out", e)
            recordUsage(chars = 0, error = "AI chat timed out")
            throw IllegalStateException("AI chat timed out after 60 seconds.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed", e)
            val msg = e.localizedMessage ?: "AI chat failed."
            recordUsage(chars = 0, error = msg)
            throw IllegalStateException(msg, e)
        }
    }.flowOn(Dispatchers.IO)

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
