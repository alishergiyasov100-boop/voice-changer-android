package com.korvus.voicechanger.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * STFT для linear magnitude spectrogram (как hop-256 win-1024 в OpenVoice).
 * Возвращает массив frame'ов, каждый frame — массив length (fft_size/2+1) = 513.
 */
object Stft {

    fun magSpectrogram(
        audio: FloatArray,
        fftSize: Int = 1024,
        hopSize: Int = 256,
        winSize: Int = 1024,
    ): Array<FloatArray> {
        // Padding (center=true как в librosa): отражаем по краям на fftSize/2.
        val pad = fftSize / 2
        val padded = FloatArray(audio.size + 2 * pad)
        // Reflect padding
        for (i in 0 until pad) padded[i] = audio.getOrElse(pad - i) { 0f }
        for (i in audio.indices) padded[i + pad] = audio[i]
        for (i in 0 until pad) padded[audio.size + pad + i] = audio.getOrElse(audio.size - 2 - i) { 0f }

        val window = hannWindow(winSize)
        val numFrames = 1 + (padded.size - fftSize) / hopSize
        val nBins = fftSize / 2 + 1
        val result = Array(numFrames) { FloatArray(nBins) }

        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        for (f in 0 until numFrames) {
            val offset = f * hopSize
            for (i in 0 until winSize) {
                re[i] = padded[offset + i] * window[i]
                im[i] = 0f
            }
            for (i in winSize until fftSize) { re[i] = 0f; im[i] = 0f }
            fft(re, im)
            for (b in 0 until nBins) {
                val mag = sqrt(re[b] * re[b] + im[b] * im[b])
                result[f][b] = mag
            }
        }
        return result
    }

    private fun hannWindow(n: Int): FloatArray {
        val w = FloatArray(n)
        for (i in 0 until n) {
            w[i] = 0.5f - 0.5f * cos(2.0 * PI * i / (n - 1)).toFloat()
        }
        return w
    }

    /** In-place radix-2 Cooley-Tukey FFT. n должен быть степенью 2. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "fft size must be power of 2" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
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
            val wIm = kotlin.math.sin(ang).toFloat()
            val half = len shr 1
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
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
}
