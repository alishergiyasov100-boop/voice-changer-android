package com.korvus.pocketvoice.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var outFile: File? = null

    fun start(): File {
        stop()  // safety
        val dir = File(ctx.cacheDir, "rec").apply { mkdirs() }
        val f = File(dir, "src_${System.currentTimeMillis()}.m4a")
        outFile = f
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx)
                else MediaRecorder()
        r.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(f.absolutePath)
            prepare()
            start()
        }
        rec = r
        return f
    }

    fun stop(): File? {
        val r = rec ?: return null
        val f = outFile
        try {
            r.stop()
        } catch (_: Throwable) {}
        try { r.release() } catch (_: Throwable) {}
        rec = null
        return f
    }

    fun isRecording(): Boolean = rec != null
}
