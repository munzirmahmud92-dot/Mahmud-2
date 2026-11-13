package com.example.data.remote

import android.util.Log
import com.example.data.local.VoiceProfileEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface CloningAvailability {
    data object NotConfigured : CloningAvailability
    data object Offline : CloningAvailability
    data class Ready(val endpointUrl: String) : CloningAvailability
}

@JsonClass(generateAdapter = true)
data class VoiceCloneApiResponse(
    val voiceId: String? = null,
    val status: String? = null,
    val message: String? = null,
    val audioUrl: String? = null
)

class VoiceReplicationRepository {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }

    fun evaluateAvailability(
        cloningApiBaseUrl: String,
        isNetworkAvailable: Boolean,
        offlineOnlyMode: Boolean
    ): CloningAvailability {
        val trimmed = cloningApiBaseUrl.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("https://")) {
            return CloningAvailability.NotConfigured
        }
        if (offlineOnlyMode || !isNetworkAvailable) {
            return CloningAvailability.Offline
        }
        return CloningAvailability.Ready(trimmed)
    }

    suspend fun uploadAuthorizedVoiceSample(
        endpointUrl: String,
        profile: VoiceProfileEntity
    ): Result<VoiceCloneApiResponse> = withContext(Dispatchers.IO) {
        try {
            if (!endpointUrl.startsWith("https://")) {
                return@withContext Result.failure(
                    IllegalArgumentException("Voice replication endpoint must use HTTPS.")
                )
            }
            if (!profile.consentGranted) {
                return@withContext Result.failure(
                    IllegalStateException("Explicit voice-owner consent is required before uploading.")
                )
            }
            val sampleFile = File(profile.sampleFilePath)
            if (!sampleFile.exists() || sampleFile.length() == 0L) {
                return@withContext Result.failure(
                    IllegalStateException("Voice sample file is missing.")
                )
            }

            val mediaType = if (sampleFile.extension.equals("mp3", ignoreCase = true)) {
                "audio/mpeg".toMediaType()
            } else {
                "audio/wav".toMediaType()
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("profileName", profile.profileName)
                .addFormDataPart("speakerName", profile.speakerName)
                .addFormDataPart("languageCode", profile.languageCode)
                .addFormDataPart("consentGranted", profile.consentGranted.toString())
                .addFormDataPart("consentTimestamp", profile.consentTimestamp.toString())
                .addFormDataPart("consentStatement", profile.consentStatement)
                .addFormDataPart(
                    "sample",
                    sampleFile.name,
                    sampleFile.asRequestBody(mediaType)
                )
                .build()

            val request = Request.Builder()
                .url(endpointUrl)
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Replication server returned HTTP ${response.code}: ${rawBody.take(180)}")
                    )
                }
                val parsed = runCatching {
                    moshi.adapter(VoiceCloneApiResponse::class.java).fromJson(rawBody)
                }.getOrNull() ?: VoiceCloneApiResponse(
                    status = "SUBMITTED",
                    message = "Voice sample submitted to replication endpoint."
                )
                Result.success(parsed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VoiceReplicationRepo", "Replication API call failed", e)
            Result.failure(e)
        }
    }

    suspend fun synthesizeWithClonedVoice(
        synthesizeEndpointUrl: String,
        remoteVoiceId: String,
        text: String,
        languageCode: String,
        outFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!synthesizeEndpointUrl.startsWith("https://")) {
                return@withContext Result.failure(IllegalArgumentException("HTTPS endpoint required."))
            }
            val payloadJson = """{"voiceId":"$remoteVoiceId","text":${org.json.JSONObject.quote(text)},"languageCode":"$languageCode"}"""
            val request = Request.Builder()
                .url(synthesizeEndpointUrl)
                .post(payloadJson.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Cloned voice synthesis failed (HTTP ${response.code}).")
                    )
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Empty audio response."))
                }
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(bytes)
                Result.success(outFile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
