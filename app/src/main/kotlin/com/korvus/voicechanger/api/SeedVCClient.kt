package com.korvus.voicechanger.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val json = Json { ignoreUnknownKeys = true }

class SeedVCClient(
    private val space: String,         // например "username/Seed-VC"
    private val steps: Int = 25,
    private val lengthAdjust: Float = 1.0f,
    private val cfgRate: Float = 0.7f,
    private val pitchShift: Int = 0,
    private val useF0: Boolean = false,
    private val autoF0: Boolean = true,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun base(): String {
        val s = space.trim().trim('/')
        val slug = s.replace('/', '-').lowercase()
        return "https://$slug.hf.space"
    }

    private fun extOf(name: String) = name.substringAfterLast('.', "mp3").lowercase()
    private fun mimeOf(name: String): String = when (extOf(name)) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    private suspend fun upload(file: File): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files", file.name, file.asRequestBody(mimeOf(file.name).toMediaType()))
            .build()
        val req = Request.Builder().url(base() + "/gradio_api/upload").post(body).build()
        val resp = client.newCall(req).execute()
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IOException("upload HTTP ${resp.code}: ${text.take(200)}")
        // ответ: ["/tmp/gradio/abc/file.mp3"]
        val arr = json.parseToJsonElement(text).jsonArray
        arr[0].jsonPrimitive.content
    }

    /** Возвращает URL результирующего аудио (full WAV). */
    suspend fun convert(source: File, reference: File): String = withContext(Dispatchers.IO) {
        val srcPath = upload(source)
        val refPath = upload(reference)

        val payload = buildJsonObject {
            put("data", buildJsonArray {
                add(audioRef(srcPath))
                add(audioRef(refPath))
                add(buildJsonObject { put("value", steps.toString()) })  // int как str — норм
                // Тут проще — кидаем как примитивы:
            })
        }
        // Чёткая форма для gradio — массив значений нужных типов:
        val cleanPayload = buildJsonObject {
            put("data", buildJsonArray {
                add(audioRef(srcPath))
                add(audioRef(refPath))
                add(intVal(steps))
                add(floatVal(lengthAdjust))
                add(floatVal(cfgRate))
                add(boolVal(useF0))
                add(boolVal(autoF0))
                add(intVal(pitchShift))
            })
        }

        // 1) call → event_id
        val callReq = Request.Builder()
            .url(base() + "/gradio_api/call/voice_conversion")
            .post(cleanPayload.toString().toRequestBody(JSON_MEDIA))
            .build()
        val callResp = client.newCall(callReq).execute()
        val callText = callResp.body?.string().orEmpty()
        if (!callResp.isSuccessful) throw IOException("call HTTP ${callResp.code}: ${callText.take(300)}")
        val eventId = json.parseToJsonElement(callText).jsonObject["event_id"]?.jsonPrimitive?.content
            ?: throw IOException("no event_id: ${callText.take(200)}")

        // 2) poll SSE stream до complete
        return@withContext pollResult(eventId)
    }

    private fun audioRef(path: String) = buildJsonObject {
        put("path", path)
        put("meta", buildJsonObject { put("_type", "gradio.FileData") })
    }
    private fun intVal(v: Int): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.JsonPrimitive(v)
    private fun floatVal(v: Float): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.JsonPrimitive(v)
    private fun boolVal(v: Boolean): kotlinx.serialization.json.JsonElement =
        kotlinx.serialization.json.JsonPrimitive(v)

    private suspend fun pollResult(eventId: String, maxWaitMs: Long = 180_000): String {
        val url = base() + "/gradio_api/call/voice_conversion/$eventId"
        val req = Request.Builder().url(url).get().build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw IOException("poll HTTP ${resp.code}")
        val src = resp.body?.source() ?: throw IOException("no body")
        val started = System.currentTimeMillis()
        val sb = StringBuilder()
        while (!src.exhausted()) {
            if (System.currentTimeMillis() - started > maxWaitMs)
                throw IOException("timeout waiting for SeedVC")
            val line = src.readUtf8Line() ?: break
            if (line.startsWith("event:")) {
                val eventName = line.substringAfter("event:").trim()
                // следующая строка должна быть data:
                val dataLine = src.readUtf8Line() ?: continue
                if (!dataLine.startsWith("data:")) continue
                val dataStr = dataLine.substringAfter("data:").trim()
                when (eventName) {
                    "complete" -> {
                        // полная JSON-структура, ищем url full wav
                        val arr = json.parseToJsonElement(dataStr).jsonArray
                        // arr[1] — full wav (см. UI Seed-VC), arr[0] — stream mp3
                        val full = arr.getOrNull(1) ?: arr[0]
                        val obj = full.jsonObject
                        return obj["url"]?.jsonPrimitive?.contentOrNull
                            ?: obj["path"]?.jsonPrimitive?.contentOrNull?.let { base() + "/gradio_api/file=$it" }
                            ?: throw IOException("no url in complete: $dataStr")
                    }
                    "error" -> throw IOException("SeedVC error: $dataStr")
                    "generating" -> sb.appendLine(dataStr)  // progress, ignored
                }
            }
            delay(20)
        }
        throw IOException("stream ended without complete")
    }
}
