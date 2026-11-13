package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID

data class OfflineVoiceInfo(
    val name: String,
    val localeTag: String,
    val displayLanguage: String,
    val isOfflineReady: Boolean,
    val qualityScore: Int
)

enum class TtsEngineState {
    INITIALIZING,
    READY,
    UNAVAILABLE
}

class OfflineTtsEngine(private val context: Context) {
    private var tts: TextToSpeech? = null

    private val _engineState = MutableStateFlow(TtsEngineState.INITIALIZING)
    val engineState: StateFlow<TtsEngineState> = _engineState.asStateFlow()

    private val _voices = MutableStateFlow<List<OfflineVoiceInfo>>(emptyList())
    val voices: StateFlow<List<OfflineVoiceInfo>> = _voices.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var pendingSyntheses = mutableMapOf<String, CompletableDeferred<Result<File>>>()

    init {
        initializeTts()
    }

    fun initializeTts() {
        _engineState.value = TtsEngineState.INITIALIZING
        tts?.shutdown()
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _engineState.value = TtsEngineState.READY
                attachUtteranceListener()
                refreshVoices()
            } else {
                _engineState.value = TtsEngineState.UNAVAILABLE
            }
        }
    }

    private fun attachUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId?.startsWith("speak_") == true) {
                    _isSpeaking.value = true
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("speak_") == true) {
                    _isSpeaking.value = false
                }
                utteranceId?.let { id ->
                    pendingSyntheses.remove(id)?.let { deferred ->
                        val file = pendingFiles.remove(id)
                        if (file != null && file.exists() && file.length() > 0L) {
                            deferred.complete(Result.success(file))
                        } else {
                            deferred.complete(Result.failure(IllegalStateException("Synthesized file was empty.")))
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleFailure(utteranceId, "Offline TTS synthesis failed.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleFailure(utteranceId, "Offline TTS synthesis failed (error code $errorCode).")
            }
        })
    }

    private val pendingFiles = mutableMapOf<String, File>()

    private fun handleFailure(utteranceId: String?, message: String) {
        if (utteranceId?.startsWith("speak_") == true) {
            _isSpeaking.value = false
        }
        utteranceId?.let { id ->
            pendingFiles.remove(id)
            pendingSyntheses.remove(id)?.complete(Result.failure(IllegalStateException(message)))
        }
    }

    fun refreshVoices() {
        val engine = tts ?: return
        val allVoices: Set<Voice> = runCatching { engine.voices ?: emptySet() }.getOrDefault(emptySet())
        val supported = allVoices
            .filter { voice ->
                val lang = voice.locale.language.lowercase(Locale.ROOT)
                lang == "bn" || lang == "en"
            }
            .sortedWith(
                compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                    .thenBy { it.locale.language }
                    .thenByDescending { it.quality }
            )
            .map { voice ->
                val langLabel = when (voice.locale.language.lowercase(Locale.ROOT)) {
                    "bn" -> "Bengali (${voice.locale.country.ifBlank { "BD/IN" }})"
                    else -> "English (${voice.locale.country.ifBlank { "US" }})"
                }
                OfflineVoiceInfo(
                    name = voice.name,
                    localeTag = voice.locale.toLanguageTag(),
                    displayLanguage = langLabel,
                    isOfflineReady = !voice.isNetworkConnectionRequired,
                    qualityScore = voice.quality
                )
            }
        _voices.value = supported
    }

    fun speak(
        text: String,
        voiceName: String?,
        languageTag: String,
        speed: Float,
        pitch: Float
    ): Result<Unit> {
        val engine = tts ?: return Result.failure(IllegalStateException("TextToSpeech is not ready."))
        if (_engineState.value != TtsEngineState.READY) {
            return Result.failure(IllegalStateException("TextToSpeech engine is not initialized."))
        }
        applyVoiceAndParams(engine, voiceName, languageTag, speed, pitch)
        val utteranceId = "speak_${UUID.randomUUID()}"
        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        return if (res == TextToSpeech.SUCCESS) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to start offline speech."))
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    suspend fun synthesizeToWavFile(
        text: String,
        voiceName: String?,
        languageTag: String,
        speed: Float,
        pitch: Float,
        outFile: File
    ): Result<File> = withContext(Dispatchers.Main) {
        val engine = tts ?: return@withContext Result.failure(IllegalStateException("TextToSpeech is not ready."))
        if (_engineState.value != TtsEngineState.READY) {
            return@withContext Result.failure(IllegalStateException("TextToSpeech engine is unavailable."))
        }
        outFile.parentFile?.mkdirs()
        applyVoiceAndParams(engine, voiceName, languageTag, speed, pitch)

        val utteranceId = "synth_${UUID.randomUUID()}"
        val deferred = CompletableDeferred<Result<File>>()
        pendingSyntheses[utteranceId] = deferred
        pendingFiles[utteranceId] = outFile

        val res = engine.synthesizeToFile(text, Bundle(), outFile, utteranceId)
        if (res != TextToSpeech.SUCCESS) {
            pendingSyntheses.remove(utteranceId)
            pendingFiles.remove(utteranceId)
            return@withContext Result.failure(IllegalStateException("TextToSpeech.synthesizeToFile rejected request."))
        }

        try {
            withTimeout(45_000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            pendingSyntheses.remove(utteranceId)
            pendingFiles.remove(utteranceId)
            Result.failure(e)
        }
    }

    private fun applyVoiceAndParams(
        engine: TextToSpeech,
        voiceName: String?,
        languageTag: String,
        speed: Float,
        pitch: Float
    ) {
        val matchingVoice = if (!voiceName.isNullOrBlank()) {
            engine.voices?.firstOrNull { it.name == voiceName }
        } else null

        if (matchingVoice != null) {
            engine.voice = matchingVoice
        } else {
            val locale = Locale.forLanguageTag(languageTag)
            engine.language = locale
        }
        engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 1.5f))
    }

    fun createInstallVoiceDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
