package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.AudioClipEntity
import com.example.data.local.AudioFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AudioExporter {

    suspend fun prepareExportFile(
        context: Context,
        clip: AudioClipEntity,
        targetFormat: AudioFormat,
        bitrateKbps: Int
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(clip.filePath)
            require(sourceFile.exists() && sourceFile.length() > 0L) {
                "Source audio file does not exist."
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = clip.title
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .trim('_')
                .ifBlank { "munzir_audio_${clip.id}" }
                .take(40)
            val outFile = File(exportDir, "${safeName}.${targetFormat.extension}")

            if (clip.format.equals(targetFormat.name, ignoreCase = true) &&
                sourceFile.extension.equals(targetFormat.extension, ignoreCase = true)
            ) {
                sourceFile.copyTo(outFile, overwrite = true)
                return@runCatching outFile
            }

            val decoded = AudioProcessor.decodeAudioFileToPcm(sourceFile)
                ?: error("Could not decode source audio for conversion.")

            when (targetFormat) {
                AudioFormat.WAV -> AudioProcessor.writePcmAsWav(
                    pcm16 = decoded.pcmBytes,
                    sampleRateHz = decoded.sampleRateHz,
                    channels = decoded.channels,
                    outFile = outFile
                )
                AudioFormat.MP3 -> AudioProcessor.writePcmAsMp3(
                    pcm16 = decoded.pcmBytes,
                    sampleRateHz = decoded.sampleRateHz,
                    channels = decoded.channels,
                    bitrateKbps = bitrateKbps,
                    outFile = outFile
                )
            }
            outFile
        }
    }

    suspend fun saveClipToMediaStore(
        context: Context,
        clip: AudioClipEntity,
        targetFormat: AudioFormat,
        bitrateKbps: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        val prepared = prepareExportFile(context, clip, targetFormat, bitrateKbps)
        prepared.mapCatching { file ->
            val resolver = context.contentResolver
            val displayName = "${file.nameWithoutExtension}_${System.currentTimeMillis()}.${targetFormat.extension}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, targetFormat.mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/MunzirVoiceCloner")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create MediaStore audio entry.")
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Unable to write to MediaStore stream.")
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                displayName
            } else {
                val targetDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                    "MunzirVoiceCloner"
                ).apply { mkdirs() }
                val dest = File(targetDir, displayName)
                file.copyTo(dest, overwrite = true)
                dest.name
            }
        }
    }

    suspend fun writeClipToUri(
        context: Context,
        clip: AudioClipEntity,
        targetFormat: AudioFormat,
        bitrateKbps: Int,
        destUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val prepared = prepareExportFile(context, clip, targetFormat, bitrateKbps)
        prepared.mapCatching { file ->
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("Unable to open destination URI.")
            Unit
        }
    }

    suspend fun createShareIntent(
        context: Context,
        clip: AudioClipEntity,
        targetFormat: AudioFormat,
        bitrateKbps: Int
    ): Result<Intent> {
        val prepared = prepareExportFile(context, clip, targetFormat, bitrateKbps)
        return prepared.mapCatching { file ->
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            Intent(Intent.ACTION_SEND).apply {
                type = targetFormat.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, clip.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
