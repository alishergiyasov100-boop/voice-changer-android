package com.korvus.pocketvoice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Realtime on-device voice FX: phase-vocoder pitch shift + formant shift via spectral envelope warp.
 *
 * Pipeline: AudioRecord (16kHz PCM16) → 1024-FFT с hop 256 → bin-remap для pitch+formant
 * → IFFT → overlap-add → AudioTrack output. Latency ≈ FFT+hop time ~70 мс.
 */
class LiveDsp {

    @Volatile var pitchSemitones: Float = 12f  // +12 = октава выше = "Miku-style"
    @Volatile var formantShift: Float = 1.3f   // >1 = меньше резонансы (более «детский»)
    @Volatile var wetLevel: Float = 1.0f       // 0 = bypass, 1 = full effect

    private var job: Job? = null
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onError: (Throwable) -> Unit = {}) {
        if (job?.isActive == true) return
        running = true
        job = scope.launch(Dispatchers.IO) {
            try {
                runLoop()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = running

    @SuppressLint("MissingPermission")
    private fun runLoop() {
        val sr = SAMPLE_RATE
        val minRec = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val minPlay = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recBuf = maxOf(minRec, FFT_SIZE * 4)
        val playBuf = maxOf(minPlay, FFT_SIZE * 4)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,  // не VOICE_COMMUNICATION — иначе AudioTrack уходит в ушной динамик
            sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recBuf,
        )
        val play = AudioTrack(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            playBuf,
            AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
        )

        try {
            rec.startRecording()
            play.play()

            val window = hannWindow(FFT_SIZE)
            // COLA для Hann hop=N/4: sum(w[n-kH]^2) ≈ 1.5 — компенсируем double-windowing
            val outScale = 2f / 3f

            val inputBuf = FloatArray(FFT_SIZE)
            val outputBuf = FloatArray(FFT_SIZE)
            val overlap = FloatArray(FFT_SIZE)
            val re = FloatArray(FFT_SIZE)
            val im = FloatArray(FFT_SIZE)
            val newRe = FloatArray(FFT_SIZE)
            val newIm = FloatArray(FFT_SIZE)
            val mag = FloatArray(FFT_SIZE / 2 + 1)
            val pha = FloatArray(FFT_SIZE / 2 + 1)
            val newMag = FloatArray(FFT_SIZE / 2 + 1)
            val phaseSum = FloatArray(FFT_SIZE / 2 + 1)
            val lastPhase = FloatArray(FFT_SIZE / 2 + 1)

            val readBuf = ShortArray(HOP_SIZE)
            val writeBuf = ShortArray(HOP_SIZE)

            // Warm up: fill input buffer initially
            while (running) {
                val n = rec.read(readBuf, 0, HOP_SIZE)
                if (n <= 0) continue

                // Diagnostic pass-through: pitch=0 + formant=1.0 → mic→speaker без DSP
                if (kotlin.math.abs(pitchSemitones) < 0.1f && kotlin.math.abs(formantShift - 1f) < 0.05f) {
                    play.write(readBuf, 0, HOP_SIZE)
                    continue
                }

                // Shift inputBuf left, append new samples at end
                System.arraycopy(inputBuf, HOP_SIZE, inputBuf, 0, FFT_SIZE - HOP_SIZE)
                for (i in 0 until HOP_SIZE) {
                    inputBuf[FFT_SIZE - HOP_SIZE + i] = readBuf[i] / 32768f
                }

                // Forward FFT с окном
                for (i in 0 until FFT_SIZE) {
                    re[i] = inputBuf[i] * window[i]
                    im[i] = 0f
                }
                fft(re, im)

                // Extract mag + phase
                val nBins = FFT_SIZE / 2 + 1
                for (k in 0 until nBins) {
                    val r = re[k]; val ii = im[k]
                    mag[k] = sqrt(r * r + ii * ii)
                    pha[k] = kotlin.math.atan2(ii, r)
                }

                val pitchRatio = 2.0.pow(pitchSemitones / 12.0).toFloat()
                val formantRatio = formantShift

                // Расчёт smoothed envelope для formant preservation/shift
                // Cheap: усреднение по 16 бинам = ~250 Hz polish
                val envSrc = FloatArray(nBins)
                val envSmoothK = 8
                for (k in 0 until nBins) {
                    var s = 0f; var c = 0
                    for (d in -envSmoothK..envSmoothK) {
                        val kk = k + d
                        if (kk in 0 until nBins) { s += mag[kk]; c++ }
                    }
                    envSrc[k] = if (c > 0) s / c else mag[k]
                }
                // Source residual = mag / envelope (минимизирует формант)
                val residual = FloatArray(nBins)
                for (k in 0 until nBins) {
                    residual[k] = if (envSrc[k] > 1e-6f) mag[k] / envSrc[k] else 0f
                }

                // Pitch shift: остаточный спектр сдвигаем по pitchRatio
                // Formant shift: envelope warp по formantRatio
                for (k in 0 until nBins) newMag[k] = 0f
                for (k in 0 until nBins) {
                    val srcBin = (k / pitchRatio)
                    val srcI = srcBin.toInt()
                    if (srcI in 0 until nBins - 1) {
                        val frac = srcBin - srcI
                        val resMag = residual[srcI] * (1 - frac) + residual[srcI + 1] * frac
                        // Envelope теперь берём из warp formantRatio
                        val envBin = k / formantRatio
                        val envI = envBin.toInt()
                        val envMag = if (envI in 0 until nBins - 1) {
                            val fr = envBin - envI
                            envSrc[envI] * (1 - fr) + envSrc[envI + 1] * fr
                        } else if (envI in 0 until nBins) envSrc[envI] else 0f
                        newMag[k] += resMag * envMag
                    }
                }

                // Phase: advance for pitch-shifted bins
                for (k in 0 until nBins) {
                    val srcK = (k / pitchRatio).toInt().coerceIn(0, nBins - 1)
                    val deltaPhase = pha[srcK] - lastPhase[srcK]
                    lastPhase[srcK] = pha[srcK]
                    // True phase advance per hop
                    val expected = 2f * PI.toFloat() * HOP_SIZE * srcK / FFT_SIZE
                    var dev = deltaPhase - expected
                    // Wrap dev to [-PI, PI]
                    dev -= (2f * PI.toFloat()) * kotlin.math.round(dev / (2f * PI.toFloat()))
                    val trueFreq = (srcK + dev / (2f * PI.toFloat() * HOP_SIZE / FFT_SIZE)) * pitchRatio
                    phaseSum[k] += 2f * PI.toFloat() * HOP_SIZE * trueFreq / FFT_SIZE
                    // Compose new bin
                    newRe[k] = newMag[k] * cos(phaseSum[k])
                    newIm[k] = newMag[k] * sin(phaseSum[k])
                }
                // Mirror Hermitian
                for (k in 1 until FFT_SIZE / 2) {
                    newRe[FFT_SIZE - k] = newRe[k]
                    newIm[FFT_SIZE - k] = -newIm[k]
                }

                // Inverse FFT
                ifft(newRe, newIm)

                // Apply window again + OLA
                for (i in 0 until FFT_SIZE) {
                    outputBuf[i] = newRe[i] * window[i] * outScale + overlap[i]
                }

                // Output first HOP_SIZE samples, mix wet/dry
                for (i in 0 until HOP_SIZE) {
                    val wet = outputBuf[i].coerceIn(-1f, 1f)
                    val dry = inputBuf[FFT_SIZE - HOP_SIZE + i]  // последние HOP свежие samples
                    val mixed = wet * wetLevel + dry * (1f - wetLevel)
                    writeBuf[i] = (mixed.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                }
                play.write(writeBuf, 0, HOP_SIZE)

                // Shift overlap
                System.arraycopy(outputBuf, HOP_SIZE, overlap, 0, FFT_SIZE - HOP_SIZE)
                for (i in FFT_SIZE - HOP_SIZE until FFT_SIZE) overlap[i] = 0f
            }
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            runCatching { play.stop() }
            runCatching { play.release() }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val FFT_SIZE = 1024
        const val HOP_SIZE = 256  // 75% overlap
    }
}

private fun hannWindow(n: Int): FloatArray {
    val w = FloatArray(n)
    for (i in 0 until n) w[i] = 0.5f - 0.5f * cos(2.0 * PI * i / (n - 1)).toFloat()
    return w
}

private fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    require(n and (n - 1) == 0)
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j xor bit
        if (i < j) {
            var t = re[i]; re[i] = re[j]; re[j] = t
            t = im[i]; im[i] = im[j]; im[j] = t
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wRe = cos(ang).toFloat()
        val wIm = sin(ang).toFloat()
        val half = len shr 1
        var i = 0
        while (i < n) {
            var curRe = 1f; var curIm = 0f
            for (k in 0 until half) {
                val tRe = curRe * re[i + k + half] - curIm * im[i + k + half]
                val tIm = curRe * im[i + k + half] + curIm * re[i + k + half]
                re[i + k + half] = re[i + k] - tRe
                im[i + k + half] = im[i + k] - tIm
                re[i + k] = re[i + k] + tRe
                im[i + k] = im[i + k] + tIm
                val nRe = curRe * wRe - curIm * wIm
                val nIm = curRe * wIm + curIm * wRe
                curRe = nRe; curIm = nIm
            }
            i += len
        }
        len = len shl 1
    }
}

private fun ifft(re: FloatArray, im: FloatArray) {
    // ifft(x) = conj(fft(conj(x))) / N
    for (i in im.indices) im[i] = -im[i]
    fft(re, im)
    val n = re.size.toFloat()
    for (i in re.indices) {
        re[i] = re[i] / n
        im[i] = -im[i] / n
    }
}
