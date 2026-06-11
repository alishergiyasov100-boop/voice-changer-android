package com.korvus.voicechanger.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.korvus.voicechanger.audio.Pcm
import com.korvus.voicechanger.audio.Stft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class LocalConverter(ctx: Context) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val downloader = ModelDownloader(ctx)

    private var extractSession: OrtSession? = null
    private var colorSession: OrtSession? = null

    val downloadState get() = downloader.state
    fun isReady() = downloader.isReady()

    suspend fun ensureDownloaded() = downloader.ensureDownloaded()

    private suspend fun ensureLoaded(): Unit = withContext(Dispatchers.IO) {
        if (extractSession == null) {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
            }
            extractSession = env.createSession(downloader.fileOf("tone_extract.onnx").absolutePath, opts)
        }
        if (colorSession == null) {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
            }
            colorSession = env.createSession(downloader.fileOf("tone_color.onnx").absolutePath, opts)
        }
    }

    /** PCM mono @ 22050 → tone embedding [256]. */
    private suspend fun extractEmbedding(audio: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        ensureLoaded()
        val spec = Stft.magSpectrogram(audio)  // [frames][513]
        val frames = spec.size
        val bins = spec[0].size
        val flat = FloatArray(frames * bins)
        for (f in 0 until frames) System.arraycopy(spec[f], 0, flat, f * bins, bins)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), longArrayOf(1, frames.toLong(), bins.toLong()))
        try {
            val result = extractSession!!.run(mapOf("input" to tensor))
            val outTensor = result[0] as OnnxTensor
            val fb = outTensor.floatBuffer
            val arr = FloatArray(fb.remaining())
            fb.get(arr)
            // ожидаем 256 элементов
            if (arr.size != 256) throw IllegalStateException("extract output expected 256 floats, got ${arr.size}")
            arr
        } finally { tensor.close() }
    }

    /** Конвертит source audio под голос reference. Возвращает float PCM @ 22050. */
    suspend fun convert(sourceFile: File, referenceFile: File, tau: Float = 0.8f): FloatArray = withContext(Dispatchers.Default) {
        ensureLoaded()
        val srcAudio = Pcm.normalize(Pcm.decode(sourceFile))
        val refAudio = Pcm.normalize(Pcm.decode(referenceFile))
        val srcSE = extractEmbedding(srcAudio)        // [256]
        val dstSE = extractEmbedding(refAudio)        // [256]

        // tone_color ожидает audio shape [1, 513, frames] (axes swapped)
        val srcSpec = Stft.magSpectrogram(srcAudio)   // [frames][513]
        val frames = srcSpec.size
        val bins = srcSpec[0].size
        val swapped = FloatArray(frames * bins)
        // perm: [1, 513, frames] — outer dim 513 bins, inner dim frames
        for (b in 0 until bins) {
            for (f in 0 until frames) {
                swapped[b * frames + f] = srcSpec[f][b]
            }
        }

        val audioTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(swapped), longArrayOf(1, bins.toLong(), frames.toLong()))
        val lenTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(frames.toLong())), longArrayOf(1))
        val srcSeTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(srcSE), longArrayOf(1, 256, 1))
        val dstSeTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(dstSE), longArrayOf(1, 256, 1))
        val tauTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(tau)), longArrayOf(1))

        try {
            val out = colorSession!!.run(mapOf(
                "audio" to audioTensor,
                "audio_length" to lenTensor,
                "src_tone" to srcSeTensor,
                "dest_tone" to dstSeTensor,
                "tau" to tauTensor,
            ))
            val outTensor = out[0] as OnnxTensor
            val fb = outTensor.floatBuffer
            val arr = FloatArray(fb.remaining())
            fb.get(arr)
            arr
        } finally {
            audioTensor.close(); lenTensor.close()
            srcSeTensor.close(); dstSeTensor.close(); tauTensor.close()
        }
    }

    fun close() {
        try { extractSession?.close() } catch (_: Throwable) {}
        try { colorSession?.close() } catch (_: Throwable) {}
        extractSession = null; colorSession = null
    }
}
