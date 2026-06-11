package com.korvus.pocketvoice.api

import android.content.Context
import com.korvus.pocketvoice.data.Voice
import com.korvus.pocketvoice.data.VoiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

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

    /** Возвращает список аудио-файлов в repo через HF tree API. */
    suspend fun listAudioFiles(repoId: String): List<RepoFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<RepoFile>()
        listTree(repoId, "", out, depth = 0)
        out.filter {
            val ext = it.path.substringAfterLast('.').lowercase()
            ext in setOf("wav", "mp3", "ogg", "flac", "m4a")
        }.sortedBy { it.size }
    }

    private suspend fun listTree(repoId: String, path: String, out: MutableList<RepoFile>, depth: Int) {
        if (depth > 2) return  // не нырять слишком глубоко
        val pathPart = if (path.isEmpty()) "" else "/$path"
        val url = "https://huggingface.co/api/models/$repoId/tree/main$pathPart"
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) return
        val arr = try { json.parseToJsonElement(body).jsonArray } catch (_: Throwable) { return }
        for (el in arr) {
            val o = el.jsonObject
            val type = o["type"]?.jsonPrimitive?.content ?: continue
            val name = o["path"]?.jsonPrimitive?.content ?: continue
            val size = (o["size"]?.jsonPrimitive?.content ?: "0").toLongOrNull() ?: 0L
            when (type) {
                "file" -> out.add(RepoFile(path = name, size = size))
                "directory" -> listTree(repoId, name, out, depth + 1)
            }
        }
    }

    /** Скачивает файл из HF repo в VoiceStore. Возвращает созданный Voice. */
    suspend fun downloadAsVoice(
        ctx: Context,
        store: VoiceStore,
        repoId: String,
        path: String,
        displayName: String,
    ): Voice = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/$repoId/resolve/main/$path"
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) throw IOException("HF download ${resp.code}")
        val body = resp.body ?: throw IOException("no body")
        val id = UUID.randomUUID().toString()
        val ext = path.substringAfterLast('.', "wav").lowercase()
        val voicesDir = File(ctx.filesDir, "voices").apply { mkdirs() }
        val dst = File(voicesDir, "$id.$ext")
        dst.outputStream().use { out -> body.byteStream().copyTo(out) }
        val v = Voice(
            id = id, name = displayName,
            emoji = "🎙",
            filename = "$id.$ext",
            sizeBytes = dst.length(),
            builtin = false,
        )
        store.addExisting(v)
        v
    }
}

data class RepoFile(val path: String, val size: Long)
