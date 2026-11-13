package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED
}

data class RecordedCapture(
    val pcmFile: File,
    val sampleRateHz: Int,
    val durationMs: Long
)

class StudioAudioRecorder(private val cacheDir: File) {
    private val _status = MutableStateFlow(RecordingStatus.IDLE)
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _waveformHistory = MutableStateFlow<List<Float>>(emptyList())
    val waveformHistory: StateFlow<List<Float>> = _waveformHistory.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var tempPcmFile: File? = null
    private var currentSampleRate = 24000
    private var totalBytesWritten = 0L

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope, sampleRateHz: Int = 24000): Result<Unit> {
        return try {
            stopInternal(deleteTemp = true)
            currentSampleRate = sampleRateHz
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return Result.failure(IllegalStateException("Microphone AudioRecord failed to initialize."))
            }

            val outDir = File(cacheDir, "recording_temp").apply { mkdirs() }
            val pcmFile = File(outDir, "capture_${System.currentTimeMillis()}.pcm")
            tempPcmFile = pcmFile
            totalBytesWritten = 0L
            _elapsedMs.value = 0L
            _waveformHistory.value = emptyList()

            audioRecord = recorder
            recorder.startRecording()
            _status.value = RecordingStatus.RECORDING

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(minBufferSize / 2)
                val byteBuffer = ByteArray(buffer.size * 2)
                FileOutputStream(pcmFile).use { fos ->
                    while (isActive && _status.value != RecordingStatus.IDLE) {
                        if (_status.value == RecordingStatus.PAUSED) {
                            _amplitude.value = 0f
                            Thread.sleep(60)
                            continue
                        }
                        val readShorts = recorder.read(buffer, 0, buffer.size)
                        if (readShorts > 0) {
                            var sumSquares = 0.0
                            for (i in 0 until readShorts) {
                                val sample = buffer[i].toInt()
                                byteBuffer[i * 2] = (sample and 0xFF).toByte()
                                byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                                val norm = sample / 32768.0
                                sumSquares += norm * norm
                            }
                            fos.write(byteBuffer, 0, readShorts * 2)
                            totalBytesWritten += readShorts * 2L

                            val rms = sqrt(sumSquares / readShorts).toFloat().coerceIn(0f, 1f)
                            val boosted = (rms * 3.2f).coerceIn(0.04f, 1f)
                            _amplitude.value = boosted
                            _waveformHistory.value = (_waveformHistory.value + boosted).takeLast(64)

                            val bytesPerSec = currentSampleRate * 2L
                            _elapsedMs.value = (totalBytesWritten * 1000L) / bytesPerSec
                        }
                    }
                    fos.flush()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            stopInternal(deleteTemp = true)
            Result.failure(e)
        }
    }

    fun pauseRecording() {
        if (_status.value == RecordingStatus.RECORDING) {
            _status.value = RecordingStatus.PAUSED
            _amplitude.value = 0f
        }
    }

    fun resumeRecording() {
        if (_status.value == RecordingStatus.PAUSED) {
            _status.value = RecordingStatus.RECORDING
        }
    }

    fun stopAndFinish(): RecordedCapture? {
        val pcmFile = tempPcmFile
        val sampleRate = currentSampleRate
        val duration = _elapsedMs.value
        stopInternal(deleteTemp = false)
        return if (pcmFile != null && pcmFile.exists() && pcmFile.length() > 0L) {
            RecordedCapture(
                pcmFile = pcmFile,
                sampleRateHz = sampleRate,
                durationMs = duration.coerceAtLeast(100L)
            )
        } else {
            null
        }
    }

    fun cancelRecording() {
        stopInternal(deleteTemp = true)
    }

    private fun stopInternal(deleteTemp: Boolean) {
        _status.value = RecordingStatus.IDLE
        _amplitude.value = 0f
        try {
            recordingJob?.cancel()
        } catch (_: Exception) {
        }
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        if (deleteTemp) {
            tempPcmFile?.delete()
            tempPcmFile = null
            _elapsedMs.value = 0L
            _waveformHistory.value = emptyList()
        }
    }
}
