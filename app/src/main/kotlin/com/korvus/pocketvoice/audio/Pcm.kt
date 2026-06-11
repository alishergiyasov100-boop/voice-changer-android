package com.korvus.pocketvoice.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Mono PCM float audio at fixed 22050 Hz (для OpenVoice). */
object Pcm {
    const val SR = 22050

    /** Декодит любой формат (m4a/mp3/wav/ogg) → float mono 22050. Конвертит частоту дискретизации линейно. */
    fun decode(file: File): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIdx = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIdx = i; inputFormat = fmt; break
            }
        }
        require(trackIdx >= 0) { "no audio track in ${file.name}" }
        extractor.selectTrack(trackIdx)
        val format = inputFormat!!
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmBytes = ArrayList<Byte>(srcSampleRate * 2)
        val bufInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        val timeoutUs = 5000L
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIdx = codec.dequeueInputBuffer(timeoutUs)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val n = extractor.readSampleData(inBuf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(bufInfo, timeoutUs)
            if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)!!
                outBuf.position(bufInfo.offset)
                outBuf.limit(bufInfo.offset + bufInfo.size)
                val arr = ByteArray(bufInfo.size)
                outBuf.get(arr)
                for (b in arr) pcmBytes.add(b)
                codec.releaseOutputBuffer(outIdx, false)
                if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true
            } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (sawInputEOS) sawOutputEOS = true
            }
        }
        codec.stop(); codec.release(); extractor.release()

        // PCM 16-bit signed interleaved → float mono
        val raw = pcmBytes.toByteArray()
        val shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val sCount = shorts.remaining()
        val perChan = sCount / channels
        val mono = FloatArray(perChan)
        var idx = 0
        for (i in 0 until perChan) {
            var sum = 0
            for (c in 0 until channels) sum += shorts.get(idx++).toInt()
            mono[i] = (sum.toFloat() / channels) / 32768f
        }
        return if (srcSampleRate == SR) mono else resampleLinear(mono, srcSampleRate, SR)
    }

    /** Простой linear-interp resampler. Достаточно для voice conversion с погрешностью. */
    fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = (input.size / ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val src = i * ratio
            val s0 = src.toInt().coerceIn(0, input.size - 1)
            val s1 = (s0 + 1).coerceAtMost(input.size - 1)
            val frac = (src - s0).toFloat()
            out[i] = input[s0] * (1 - frac) + input[s1] * frac
        }
        return out
    }

    /** Записать float mono → WAV PCM 16-bit для AudioTrack/MediaPlayer. */
    fun writeWav(audio: FloatArray, file: File, sampleRate: Int = SR) {
        val pcm16 = ByteArray(audio.size * 2)
        val bb = ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN)
        for (s in audio) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            bb.putShort(v)
        }
        val dataSize = pcm16.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)         // fmt chunk size
        header.putShort(1)        // PCM
        header.putShort(1)        // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // byte rate
        header.putShort(2)        // block align
        header.putShort(16)       // bits/sample
        header.put("data".toByteArray())
        header.putInt(dataSize)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(header.array())
            raf.write(pcm16)
        }
    }

    fun peak(audio: FloatArray): Float {
        var m = 0f
        for (s in audio) { val a = abs(s); if (a > m) m = a }
        return m
    }
    fun normalize(audio: FloatArray, target: Float = 0.95f): FloatArray {
        val p = peak(audio)
        if (p < 1e-6f) return audio
        val k = target / p
        return FloatArray(audio.size) { audio[it] * k }
    }
}
