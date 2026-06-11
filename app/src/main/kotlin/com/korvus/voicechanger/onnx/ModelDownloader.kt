package com.korvus.voicechanger.onnx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Качает OpenVoice-ONNX-v2 с Hugging Face (Hinotsuba/OpenVoice-ONNX-v2).
 * Файлы лежат в filesDir/models/.
 */
class ModelDownloader(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.MINUTES)
        .build()

    val files = mapOf(
        "tone_extract.onnx" to FileSpec("tone_extract.onnx", BASE_URL + "tone_extract.onnx", 3_528_000L),
        "tone_color.onnx" to FileSpec("tone_color.onnx", BASE_URL + "tone_color.onnx", 164_624_000L),
        "tone_config.json" to FileSpec("tone_config.json", BASE_URL + "tone_config.json", 838L),
    )

    data class FileSpec(val name: String, val url: String, val expectedSize: Long)

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun fileOf(name: String): File = File(dir, name)
    fun isReady(): Boolean = files.values.all { File(dir, it.name).length() >= it.expectedSize * 0.95 }

    suspend fun ensureDownloaded() = withContext(Dispatchers.IO) {
        if (isReady()) { _state.value = DownloadState.Ready; return@withContext }
        val total = files.values.sumOf { it.expectedSize }
        var done = 0L
        for ((idx, spec) in files.values.withIndex()) {
            val f = File(dir, spec.name)
            // если файл уже полностью скачан — скип
            if (f.length() >= spec.expectedSize * 0.99) { done += spec.expectedSize; continue }
            try {
                downloadWithResume(idx, spec, f, done, total)
                done += spec.expectedSize
            } catch (t: Throwable) {
                _state.value = DownloadState.Error("${spec.name}: ${t.message}")
                return@withContext
            }
        }
        _state.value = DownloadState.Ready
    }

    private suspend fun downloadWithResume(idx: Int, spec: FileSpec, f: File, accDone: Long, total: Long) {
        val maxAttempts = 6
        var attempt = 0
        var lastErr: Throwable? = null
        while (attempt < maxAttempts) {
            attempt++
            val startFrom = if (f.exists()) f.length() else 0L
            if (startFrom >= spec.expectedSize * 0.99 && startFrom > 0) return
            try {
                val reqBuilder = Request.Builder().url(spec.url)
                if (startFrom > 0) reqBuilder.header("Range", "bytes=$startFrom-")
                val resp = client.newCall(reqBuilder.build()).execute()
                if (resp.code !in setOf(200, 206)) throw Exception("HTTP ${resp.code}")
                val body = resp.body ?: throw Exception("no body")
                val partial = resp.code == 206
                val contentLen = body.contentLength().takeIf { it > 0 } ?: (spec.expectedSize - startFrom)
                val expectedTotal = if (partial) startFrom + contentLen else contentLen

                val out = java.io.FileOutputStream(f, partial)  // append если 206
                out.use { fos ->
                    val src = body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    var fileDone = startFrom
                    while (src.read(buf).also { n = it } > 0) {
                        fos.write(buf, 0, n)
                        fileDone += n
                        _state.value = DownloadState.Downloading(
                            fileIndex = idx, fileName = spec.name,
                            fileDone = fileDone, fileTotal = expectedTotal,
                            overallDone = accDone + fileDone, overallTotal = total,
                        )
                    }
                }
                if (f.length() >= spec.expectedSize * 0.99) return
                // докачали меньше чем ожидали — попробуем ещё раз с того места
                lastErr = Exception("short read, got ${f.length()}/${spec.expectedSize}")
            } catch (t: Throwable) {
                lastErr = t
                _state.value = DownloadState.Downloading(
                    fileIndex = idx, fileName = "${spec.name} (retry ${attempt}/${maxAttempts})",
                    fileDone = f.length(), fileTotal = spec.expectedSize,
                    overallDone = accDone + f.length(), overallTotal = total,
                )
            }
            // backoff
            kotlinx.coroutines.delay((1000L * attempt).coerceAtMost(8000L))
        }
        throw lastErr ?: Exception("download failed after $maxAttempts attempts")
    }

    companion object {
        const val BASE_URL = "https://huggingface.co/Hinotsuba/OpenVoice-ONNX-v2/resolve/main/"
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val fileIndex: Int,
        val fileName: String,
        val fileDone: Long,
        val fileTotal: Long,
        val overallDone: Long,
        val overallTotal: Long,
    ) : DownloadState()
    object Ready : DownloadState()
    data class Error(val msg: String) : DownloadState()
}
