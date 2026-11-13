package com.example.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val activeFilePath: String? = null,
    val activeTitle: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val errorMessage: String? = null
)

class StudioAudioPlayer(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    fun playFile(
        filePath: String,
        title: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val current = _state.value
        if (current.activeFilePath == filePath && mediaPlayer != null) {
            if (!current.isPlaying) {
                resume()
                updateSpeedAndPitch(speed, pitch)
                return
            }
        }

        stop()

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            _state.value = PlaybackState(errorMessage = "Audio file not found or empty.")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        playbackParams = PlaybackParams().apply {
                            this.speed = speed.coerceIn(0.5f, 2.0f)
                            this.pitch = pitch.coerceIn(0.6f, 1.5f)
                        }
                    }
                }
                setOnCompletionListener {
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        positionMs = 0L
                    )
                    progressJob?.cancel()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("StudioAudioPlayer", "MediaPlayer error what=$what extra=$extra")
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        errorMessage = "Audio playback failed (code $what)."
                    )
                    true
                }
                start()
            }

            mediaPlayer = player
            _state.value = PlaybackState(
                activeFilePath = filePath,
                activeTitle = title,
                isPlaying = true,
                positionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(1L),
                speed = speed,
                pitch = pitch,
                errorMessage = null
            )
            startProgressTracking()
        } catch (e: Exception) {
            Log.e("StudioAudioPlayer", "Failed to start playback", e)
            stop()
            _state.value = PlaybackState(
                errorMessage = e.localizedMessage ?: "Unable to play audio file."
            )
        }
    }

    fun togglePlayPause(filePath: String, title: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        val current = _state.value
        if (current.activeFilePath == filePath && mediaPlayer != null) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
        } else {
            playFile(filePath, title, speed, pitch)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.pause()
            }
            _state.value = _state.value.copy(isPlaying = false)
            progressJob?.cancel()
        } catch (_: Exception) {
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                it.start()
                _state.value = _state.value.copy(isPlaying = true)
                startProgressTracking()
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.let { player ->
                val clamped = positionMs.coerceIn(0L, player.duration.toLong().coerceAtLeast(1L))
                player.seekTo(clamped.toInt())
                _state.value = _state.value.copy(positionMs = clamped)
            }
        } catch (_: Exception) {
        }
    }

    fun updateSpeedAndPitch(speed: Float, pitch: Float) {
        _state.value = _state.value.copy(speed = speed, pitch = pitch)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { player ->
                    val wasPlaying = player.isPlaying
                    player.playbackParams = PlaybackParams().apply {
                        this.speed = speed.coerceIn(0.5f, 2.0f)
                        this.pitch = pitch.coerceIn(0.6f, 1.5f)
                    }
                    if (!wasPlaying && player.isPlaying) {
                        player.pause()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _state.value = PlaybackState()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && _state.value.isPlaying) {
                val pos = runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
                _state.value = _state.value.copy(positionMs = pos)
                delay(80L)
            }
        }
    }
}
