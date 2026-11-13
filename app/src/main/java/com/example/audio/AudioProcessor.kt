package com.example.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.PsyModel
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DecodedPcm(
    val pcmBytes: ByteArray,
    val sampleRateHz: Int,
    val channels: Int
)

object AudioProcessor {
    private const val TAG = "AudioProcessor"

    fun calculatePcmDurationMs(pcmByteLength: Long, sampleRateHz: Int, channels: Int): Long {
        val bytesPerSec = sampleRateHz.toLong() * channels.toLong() * 2L
        if (bytesPerSec <= 0L) return 0L
        return ((pcmByteLength * 1000L) / bytesPerSec).coerceAtLeast(100L)
    }

    fun writePcmAsWav(
        pcm16: ByteArray,
        sampleRateHz: Int,
        channels: Int,
        outFile: File
    ) {
        outFile.parentFile?.mkdirs()
        val dataSize = pcm16.size
        val byteRate = sampleRateHz * channels * 2
        val blockAlign = channels * 2
        val totalDataLen = dataSize + 36

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM fmt chunk size
            putShort(1) // PCM format = 1
            putShort(channels.toShort())
            putInt(sampleRateHz)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16) // Bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        FileOutputStream(outFile).use { fos ->
            fos.write(header)
            fos.write(pcm16)
            fos.flush()
        }
    }

    fun readWavFile(wavFile: File): DecodedPcm? {
        if (!wavFile.exists() || wavFile.length() < 44L) return null
        val bytes = wavFile.readBytes()
        if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) {
            return null
        }

        var pos = 12
        var channels = 1
        var sampleRate = 24000
        var pcmBytes: ByteArray? = null

        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 8
            if (chunkSize < 0 || pos + chunkSize > bytes.size) break
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16) {
                        val bb = ByteBuffer.wrap(bytes, pos, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                        bb.short // format
                        channels = bb.short.toInt().coerceIn(1, 2)
                        sampleRate = bb.int.coerceAtLeast(8000)
                    }
                }
                "data" -> {
                    pcmBytes = bytes.copyOfRange(pos, pos + chunkSize)
                }
            }
            pos += chunkSize + (chunkSize and 1)
        }

        return pcmBytes?.let { DecodedPcm(it, sampleRate, channels) }
    }

    fun writePcmAsMp3(
        pcm16: ByteArray,
        sampleRateHz: Int,
        channels: Int,
        bitrateKbps: Int,
        outFile: File
    ) {
        outFile.parentFile?.mkdirs()
        val lame = Lame()
        val gaud = GetAudio()
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, PsyModel())
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, p)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        val gfp = lame.lame_init()
        gfp.num_channels = channels.coerceIn(1, 2)
        gfp.in_samplerate = sampleRateHz
        gfp.out_samplerate = sampleRateHz
        gfp.mode = if (gfp.num_channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gfp.brate = if (sampleRateHz < 32000) min(bitrateKbps, 160) else bitrateKbps
        gfp.quality = 5
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false

        val initCode = lame.lame_init_params(gfp)
        require(initCode >= 0) { "LAME MP3 initialization failed (code $initCode)" }

        val totalShorts = pcm16.size / 2
        val samplesPerChannel = totalShorts / gfp.num_channels
        val chunkFrames = 8192
        val leftBuf = IntArray(chunkFrames)
        val rightBuf = IntArray(chunkFrames)
        val mp3Buf = ByteArray((1.25 * chunkFrames + 7200).toInt())

        FileOutputStream(outFile).use { fos ->
            var frameOffset = 0
            while (frameOffset < samplesPerChannel) {
                val count = min(chunkFrames, samplesPerChannel - frameOffset)
                if (gfp.num_channels == 1) {
                    for (i in 0 until count) {
                        val byteIdx = (frameOffset + i) * 2
                        val s = ((pcm16[byteIdx].toInt() and 0xFF) shl 16) or
                            ((pcm16[byteIdx + 1].toInt() and 0xFF) shl 24)
                        leftBuf[i] = s
                        rightBuf[i] = s
                    }
                } else {
                    for (i in 0 until count) {
                        val byteIdx = (frameOffset + i) * 4
                        leftBuf[i] = ((pcm16[byteIdx].toInt() and 0xFF) shl 16) or
                            ((pcm16[byteIdx + 1].toInt() and 0xFF) shl 24)
                        rightBuf[i] = ((pcm16[byteIdx + 2].toInt() and 0xFF) shl 16) or
                            ((pcm16[byteIdx + 3].toInt() and 0xFF) shl 24)
                    }
                }
                val encoded = lame.lame_encode_buffer_int(gfp, leftBuf, rightBuf, count, mp3Buf, 0, mp3Buf.size)
                if (encoded > 0) {
                    fos.write(mp3Buf, 0, encoded)
                }
                frameOffset += count
            }
            val flushed = lame.lame_encode_flush(gfp, mp3Buf, 0, mp3Buf.size)
            if (flushed > 0) {
                fos.write(mp3Buf, 0, flushed)
            }
            fos.flush()
        }
        lame.lame_close(gfp)
    }

    fun processPcm(
        pcm16: ByteArray,
        sourceRateHz: Int,
        targetRateHz: Int,
        speed: Float,
        pitch: Float
    ): ByteArray {
        if (pcm16.size < 4) return pcm16
        val sampleCount = pcm16.size / 2
        val inSamples = ShortArray(sampleCount)
        ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inSamples)

        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        val safePitch = pitch.coerceIn(0.6f, 1.5f)

        // Step 1: Time-stretch with Hanning Overlap-Add if speed != 1.0 or pitch != 1.0
        val effectiveTimeScale = safeSpeed / safePitch
        val stretched = if (abs(effectiveTimeScale - 1.0f) > 0.03f) {
            olaTimeScale(inSamples, effectiveTimeScale)
        } else {
            inSamples
        }

        // Step 2: Resample for pitch + targetRateHz
        val resampleRatio = (sourceRateHz.toDouble() * safePitch) / targetRateHz.toDouble()
        val outSamples = if (abs(resampleRatio - 1.0) > 0.005) {
            linearResample(stretched, resampleRatio)
        } else {
            stretched
        }

        val outBytes = ByteArray(outSamples.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outSamples)
        return outBytes
    }

    private fun olaTimeScale(input: ShortArray, speedFactor: Float): ShortArray {
        val winSize = 1024
        val hopOut = 256
        val hopIn = max(64, (hopOut * speedFactor).roundToInt())
        if (input.size <= winSize) return input

        val outLen = ((input.size - winSize).toDouble() / hopIn * hopOut).toInt() + winSize + 1
        val outAcc = FloatArray(outLen)
        val normAcc = FloatArray(outLen)
        val window = FloatArray(winSize) { idx ->
            (0.5 * (1.0 - cos(2.0 * Math.PI * idx / (winSize - 1)))).toFloat()
        }

        var inPos = 0
        var outPos = 0
        while (inPos + winSize < input.size && outPos + winSize < outLen) {
            for (i in 0 until winSize) {
                val w = window[i]
                outAcc[outPos + i] += input[inPos + i] * w
                normAcc[outPos + i] += w
            }
            inPos += hopIn
            outPos += hopOut
        }

        val result = ShortArray(min(outPos + winSize, outLen))
        for (i in result.indices) {
            val n = if (normAcc[i] > 0.001f) normAcc[i] else 1f
            result[i] = (outAcc[i] / n).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    private fun linearResample(input: ShortArray, ratio: Double): ShortArray {
        val outLen = max(1, (input.size / ratio).toInt())
        val output = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = srcPos - idx0
            val sample = input[idx0] * (1.0 - frac) + input[idx1] * frac
            output[i] = sample.roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return output
    }

    fun decodeAudioFileToPcm(file: File): DecodedPcm? {
        readWavFile(file)?.let { return it }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.position(bufferInfo.offset)
                        outBuf.get(chunk)
                        outBuf.clear()
                        if (chunk.isNotEmpty()) {
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }

            DecodedPcm(pcmOut.toByteArray(), sampleRate, channels.coerceIn(1, 2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode audio file", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    fun probeAudioDurationMs(file: File): Long {
        readWavFile(file)?.let {
            return calculatePcmDurationMs(it.pcmBytes.size.toLong(), it.sampleRateHz, it.channels)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L
        } catch (_: Exception) {
            1000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun extractWaveformPeaks(file: File, barCount: Int = 36): List<Float> {
        val wav = readWavFile(file) ?: return List(barCount) { idx ->
            (0.25f + 0.45f * abs(kotlin.math.sin(idx * 0.55))).toFloat()
        }
        val pcm = wav.pcmBytes
        val totalSamples = pcm.size / 2
        if (totalSamples < barCount) {
            return List(barCount) { 0.2f }
        }
        val step = max(1, totalSamples / barCount)
        val peaks = ArrayList<Float>(barCount)
        for (b in 0 until barCount) {
            val start = b * step
            val end = min(totalSamples, start + step)
            var maxAmp = 0
            for (s in start until end step max(1, step / 24)) {
                val byteIdx = s * 2
                val sample = ((pcm[byteIdx + 1].toInt() shl 8) or (pcm[byteIdx].toInt() and 0xFF))
                    .toShort().toInt()
                val a = abs(sample)
                if (a > maxAmp) maxAmp = a
            }
            peaks.add((maxAmp / 32768f).coerceIn(0.08f, 1f))
        }
        return peaks
    }

    data class AcousticVoiceFingerprint(
        val fundamentalFreqHz: Float,
        val spectralBrightness: Float,
        val rmsEnergy: Float,
        val registerLabelEn: String,
        val registerLabelBn: String
    )

    fun analyzeVoiceSample(file: File): AcousticVoiceFingerprint {
        val decoded = decodeAudioFileToPcm(file)
            ?: return AcousticVoiceFingerprint(
                fundamentalFreqHz = 155f,
                spectralBrightness = 0.45f,
                rmsEnergy = 0.2f,
                registerLabelEn = "Balanced Mid Vocal",
                registerLabelBn = "মাঝারি ভোকাল"
            )

        val pcm = decoded.pcmBytes
        val totalFrames = pcm.size / (2 * decoded.channels)
        if (totalFrames < 512) {
            return AcousticVoiceFingerprint(
                fundamentalFreqHz = 155f,
                spectralBrightness = 0.45f,
                rmsEnergy = 0.2f,
                registerLabelEn = "Balanced Mid Vocal",
                registerLabelBn = "মাঝারি ভোকাল"
            )
        }

        // Convert to mono float samples [-1f, 1f] (cap to first 12 seconds for fast analysis)
        val maxSamples = min(totalFrames, decoded.sampleRateHz * 12)
        val mono = FloatArray(maxSamples)
        val stepBytes = 2 * decoded.channels
        for (i in 0 until maxSamples) {
            val idx = i * stepBytes
            val sample = ((pcm[idx + 1].toInt() shl 8) or (pcm[idx].toInt() and 0xFF)).toShort()
            mono[i] = sample / 32768f
        }

        // Compute RMS energy & Zero-Crossing Rate (brightness)
        var sumSq = 0.0
        var zeroCrossings = 0
        for (i in 1 until mono.size) {
            val s = mono[i]
            sumSq += s * s
            if ((s >= 0f && mono[i - 1] < 0f) || (s < 0f && mono[i - 1] >= 0f)) {
                zeroCrossings++
            }
        }
        val rms = kotlin.math.sqrt(sumSq / mono.size).toFloat().coerceIn(0f, 1f)
        val zcrPerSec = zeroCrossings.toFloat() * decoded.sampleRateHz / mono.size.toFloat()
        val brightness = ((zcrPerSec - 400f) / 2600f).coerceIn(0.08f, 0.95f)

        // Autocorrelation F0 estimation over active voiced frames (75 Hz .. 340 Hz)
        val sr = decoded.sampleRateHz.coerceAtLeast(8000)
        val minLag = max(16, sr / 340)
        val maxLag = min(sr / 75, 1024)
        val frameSize = max(maxLag * 2, 1024)
        val f0Estimates = ArrayList<Float>()

        var offset = 0
        while (offset + frameSize < mono.size && f0Estimates.size < 40) {
            var frameEnergy = 0.0
            for (k in 0 until frameSize) {
                val v = mono[offset + k]
                frameEnergy += v * v
            }
            val frameRms = kotlin.math.sqrt(frameEnergy / frameSize)
            if (frameRms > 0.025) {
                var bestLag = -1
                var bestCorr = 0.0
                for (lag in minLag..maxLag) {
                    var num = 0.0
                    var denA = 0.0
                    var denB = 0.0
                    val limit = frameSize - lag
                    for (j in 0 until limit step 2) {
                        val a = mono[offset + j].toDouble()
                        val b = mono[offset + j + lag].toDouble()
                        num += a * b
                        denA += a * a
                        denB += b * b
                    }
                    val den = kotlin.math.sqrt(denA * denB)
                    if (den > 1e-6) {
                        val corr = num / den
                        if (corr > bestCorr) {
                            bestCorr = corr
                            bestLag = lag
                        }
                    }
                }
                if (bestLag > 0 && bestCorr > 0.48) {
                    f0Estimates.add(sr.toFloat() / bestLag.toFloat())
                }
            }
            offset += frameSize / 2
        }

        val medianF0 = if (f0Estimates.isNotEmpty()) {
            val sorted = f0Estimates.sorted()
            sorted[sorted.size / 2].coerceIn(80f, 320f)
        } else {
            155f
        }

        val (labelEn, labelBn) = when {
            medianF0 < 142f -> "Deep Warm Vocal" to "গভীর ও ভরাট কণ্ঠ"
            medianF0 <= 195f -> "Balanced Mid Vocal" to "মাঝারি প্রাকৃতিক কণ্ঠ"
            else -> "Bright High Vocal" to "উজ্জ্বল ও স্পষ্ট কণ্ঠ"
        }

        return AcousticVoiceFingerprint(
            fundamentalFreqHz = medianF0,
            spectralBrightness = brightness,
            rmsEnergy = rms,
            registerLabelEn = labelEn,
            registerLabelBn = labelBn
        )
    }

    fun applyAcousticTimbreMatch(
        pcm16: ByteArray,
        sourceRateHz: Int,
        targetRateHz: Int,
        targetF0Hz: Float,
        baseVoiceF0Hz: Float,
        targetBrightness: Float
    ): ByteArray {
        val pitchRatio = (targetF0Hz / baseVoiceF0Hz.coerceAtLeast(80f)).coerceIn(0.72f, 1.38f)
        val pitched = processPcm(
            pcm16 = pcm16,
            sourceRateHz = sourceRateHz,
            targetRateHz = targetRateHz,
            speed = 1.0f,
            pitch = pitchRatio
        )
        if (pitched.size < 4) return pitched

        // Apply subtle single-pole warmth/presence shaping based on analyzed vocal brightness
        val sampleCount = pitched.size / 2
        val samples = ShortArray(sampleCount)
        ByteBuffer.wrap(pitched).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        val warmthMix = (0.5f - targetBrightness).coerceIn(-0.25f, 0.28f)
        var lowState = 0f
        for (i in samples.indices) {
            val x = samples[i].toFloat()
            lowState += 0.16f * (x - lowState)
            val shaped = if (warmthMix >= 0f) {
                // Boost vocal warmth/body for deeper voices
                x + warmthMix * lowState
            } else {
                // Gentle crisp presence lift for brighter voices
                x + (-warmthMix) * (x - lowState)
            }
            samples[i] = shaped.roundToInt().coerceIn(-32768, 32767).toShort()
        }

        val outBytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        return outBytes
    }
}
