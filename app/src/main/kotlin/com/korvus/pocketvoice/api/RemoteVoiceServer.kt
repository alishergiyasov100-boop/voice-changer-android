package com.korvus.pocketvoice.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Клиент к Kaggle/Colab-серверу (FastAPI на cloudflared tunnel). */
class RemoteVoiceServer(
    private val baseUrl: String,
    private val diffusionSteps: Int = 25,
    private val lengthAdjust: Float = 1.0f,
    private val cfgRate: Float = 0.7f,
    private val pitchShift: Int = 0,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun base() = baseUrl.trim().trimEnd('/')

    /** POST /convert (source + reference + params). Возвращает байты WAV. */
    suspend fun convert(source: File, reference: File): ByteArray = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", source.name,
                source.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("reference", reference.name,
                reference.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("diffusion_steps", diffusionSteps.toString())
            .addFormDataPart("length_adjust", lengthAdjust.toString())
            .addFormDataPart("inference_cfg_rate", cfgRate.toString())
            .addFormDataPart("pitch_shift", pitchShift.toString())
            .build()
        val req = Request.Builder().url(base() + "/convert").post(body).build()
        val resp = client.newCall(req).execute()
        val payload = resp.body?.bytes() ?: throw IOException("no body")
        if (!resp.isSuccessful) {
            val text = String(payload).take(500)
            throw IOException("server HTTP ${resp.code}: $text")
        }
        payload
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = client.newCall(Request.Builder().url(base() + "/health").build()).execute()
            resp.isSuccessful
        } catch (_: Throwable) { false }
    }
}
