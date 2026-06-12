package com.korvus.pocketvoice.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RemoteVoiceServer(
    private val baseUrl: String,
    private val pitchShift: Int = 0,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun base() = baseUrl.trim().trimEnd('/')

    suspend fun convert(source: File): ByteArray = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("source", source.name,
                source.asRequestBody("audio/wav".toMediaType()))
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
