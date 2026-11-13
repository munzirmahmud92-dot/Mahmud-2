package com.example

import com.example.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class AudioProcessorTest {

    private fun generateSinePcm(sampleRate: Int, durationSec: Double, freqHz: Double = 440.0): ByteArray {
        val sampleCount = (sampleRate * durationSec).toInt()
        val bytes = ByteArray(sampleCount * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val v = (sin(2.0 * Math.PI * freqHz * i / sampleRate) * 16000.0).toInt().toShort()
            bb.putShort(v)
        }
        return bytes
    }

    @Test
    fun wavRoundTripAndDurationAreAccurate() {
        val pcm = generateSinePcm(sampleRate = 24000, durationSec = 1.0)
        val tempFile = File.createTempFile("test_wav_", ".wav")
        try {
            AudioProcessor.writePcmAsWav(pcm, sampleRateHz = 24000, channels = 1, outFile = tempFile)
            assertTrue(tempFile.exists())
            assertEquals(44 + pcm.size, tempFile.length().toInt())

            val decoded = AudioProcessor.readWavFile(tempFile)
            assertNotNull(decoded)
            assertEquals(24000, decoded!!.sampleRateHz)
            assertEquals(1, decoded.channels)
            assertEquals(pcm.size, decoded.pcmBytes.size)

            val durationMs = AudioProcessor.calculatePcmDurationMs(pcm.size.toLong(), 24000, 1)
            assertEquals(1000L, durationMs)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun mp3LameEncodingProducesValidMpegFrames() {
        val pcm = generateSinePcm(sampleRate = 24000, durationSec = 1.0)
        val tempMp3 = File.createTempFile("test_mp3_", ".mp3")
        try {
            AudioProcessor.writePcmAsMp3(
                pcm16 = pcm,
                sampleRateHz = 24000,
                channels = 1,
                bitrateKbps = 128,
                outFile = tempMp3
            )
            assertTrue(tempMp3.exists())
            val bytes = tempMp3.readBytes()
            assertTrue("MP3 output should be > 1KB", bytes.size > 1000)
            // MPEG sync word: 0xFF followed by 0xE0..0xFF
            val hasSync = (0 until bytes.size - 1).any { idx ->
                (bytes[idx].toInt() and 0xFF) == 0xFF && (bytes[idx + 1].toInt() and 0xE0) == 0xE0
            }
            assertTrue("MP3 output must contain valid MPEG frame sync header", hasSync)
        } finally {
            tempMp3.delete()
        }
    }

    @Test
    fun speedAndPitchDspScalesOutputCorrectly() {
        val pcm = generateSinePcm(sampleRate = 24000, durationSec = 1.0)
        val faster = AudioProcessor.processPcm(
            pcm16 = pcm,
            sourceRateHz = 24000,
            targetRateHz = 44100,
            speed = 1.5f,
            pitch = 1.0f
        )
        assertTrue(faster.isNotEmpty())
        val peaks = AudioProcessor.extractWaveformPeaks(
            File.createTempFile("test_peak_", ".wav").apply {
                AudioProcessor.writePcmAsWav(faster, 44100, 1, this)
                deleteOnExit()
            },
            barCount = 24
        )
        assertEquals(24, peaks.size)
        assertTrue(peaks.all { it in 0.08f..1.0f })
    }
}
