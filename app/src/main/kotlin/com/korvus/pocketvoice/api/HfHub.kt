package com.korvus.pocketvoice.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class HubModel(
    val id: String,
    val author: String,
    val name: String,
    val downloads: Int,
    val likes: Int,
    val tags: List<String>,
    val updatedAt: String?,
)

object HfHub {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Ищет модели подходящие для voice cloning (GPT-SoVITS / RVC / OpenVoice ref-голоса). */
    suspend fun search(query: String, limit: Int = 50): List<HubModel> = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/api/models?search=${java.net.URLEncoder.encode(query, "UTF-8")}&full=false&limit=$limit&sort=downloads&direction=-1"
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IOException("HF HTTP ${resp.code}: ${body.take(200)}")
        val arr = json.parseToJsonElement(body).jsonArray
        arr.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val parts = id.split('/', limit = 2)
            val author = if (parts.size == 2) parts[0] else "—"
            val name = if (parts.size == 2) parts[1] else id
            HubModel(
                id = id,
                author = author,
                name = name,
                downloads = (o["downloads"]?.jsonPrimitive?.content ?: "0").toIntOrNull() ?: 0,
                likes = (o["likes"]?.jsonPrimitive?.content ?: "0").toIntOrNull() ?: 0,
                tags = (o["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content }).orEmpty(),
                updatedAt = o["lastModified"]?.jsonPrimitive?.content,
            )
        }
    }
}
