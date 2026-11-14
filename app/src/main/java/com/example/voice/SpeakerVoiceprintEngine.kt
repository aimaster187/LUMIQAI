package com.example.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class SpeakerVoiceprint(
    val embedding: FloatArray,
    val f0Hz: Float,
    val voicedFrames: Int,
    val enrolledAtMs: Long = System.currentTimeMillis()
)

data class SpeakerVerificationResult(
    val isMatch: Boolean,
    val similarity: Float,
    val probeF0Hz: Float,
    val enrolledF0Hz: Float
)

object SpeakerVoiceprintEngine {

    const val SAMPLE_RATE = 16_000
    const val FRAME_SIZE = 512
    const val HOP_SIZE = 256
    const val MEL_BANDS = 12
    const val MATCH_THRESHOLD = 0.86f

    @SuppressLint("MissingPermission")
    suspend fun recordPcmSample(durationMs: Int = 2600): Result<ShortArray> = withContext(Dispatchers.IO) {
        val totalSamples = (SAMPLE_RATE * durationMs) / 1000
        val minBufBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes <= 0) {
            return@withContext Result.failure(IllegalStateException("AudioRecord unavailable on this device."))
        }
        val bufBytes = maxOf(minBufBytes * 2, FRAME_SIZE * 4)
        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(IllegalStateException("Microphone AudioRecord failed to initialize."))
            }
            val output = ShortArray(totalSamples)
            val chunk = ShortArray(FRAME_SIZE)
            var collected = 0
            recorder.startRecording()
            while (collected < totalSamples) {
                val read = recorder.read(chunk, 0, minOf(chunk.size, totalSamples - collected))
                if (read < 0) {
                    return@withContext Result.failure(IllegalStateException("Microphone read error ($read)."))
                }
                if (read > 0) {
                    chunk.copyInto(output, destinationOffset = collected, startIndex = 0, endIndex = read)
                    collected += read
                }
            }
            Result.success(output)
        } catch (se: SecurityException) {
            Result.failure(IllegalStateException("Microphone permission required for speaker recognition.", se))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
        }
    }

    fun extractVoiceprint(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): Result<SpeakerVoiceprint> {
        if (samples.size < FRAME_SIZE * 3) {
            return Result.failure(IllegalArgumentException("Audio clip too short. Speak for at least 2 seconds."))
        }

        val floatSamples = FloatArray(samples.size) { samples[it] / 32768f }
        val hann = FloatArray(FRAME_SIZE) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat()
        }

        val melSum = DoubleArray(MEL_BANDS)
        var f0Sum = 0.0
        var f0Count = 0
        var zcrSum = 0.0
        var centroidSum = 0.0
        var voicedFrames = 0

        val minLag = (sampleRate / 340).coerceAtLeast(16)
        val maxLag = (sampleRate / 70).coerceAtMost(FRAME_SIZE - 2)
        val binCount = FRAME_SIZE / 2
        val fftFreqHz = DoubleArray(binCount) { k -> k.toDouble() * sampleRate / FRAME_SIZE }

        var offset = 0
        while (offset + FRAME_SIZE <= floatSamples.size) {
            var energy = 0.0
            for (i in 0 until FRAME_SIZE) {
                val s = floatSamples[offset + i].toDouble()
                energy += s * s
            }
            val rms = sqrt(energy / FRAME_SIZE)
            if (rms > 0.015) {
                voicedFrames++

                // Zero-crossing rate
                var zc = 0
                for (i in 1 until FRAME_SIZE) {
                    val prev = floatSamples[offset + i - 1]
                    val curr = floatSamples[offset + i]
                    if ((prev >= 0f && curr < 0f) || (prev < 0f && curr >= 0f)) zc++
                }
                zcrSum += zc.toDouble() / FRAME_SIZE

                // Fundamental frequency (F0) via normalized autocorrelation
                var bestLag = -1
                var bestCorr = 0.0
                for (lag in minLag..maxLag) {
                    var num = 0.0
                    var denA = 0.0
                    var denB = 0.0
                    val limit = FRAME_SIZE - lag
                    for (i in 0 until limit) {
                        val a = floatSamples[offset + i].toDouble()
                        val b = floatSamples[offset + i + lag].toDouble()
                        num += a * b
                        denA += a * a
                        denB += b * b
                    }
                    val den = sqrt(denA * denB)
                    if (den > 1e-9) {
                        val corr = num / den
                        if (corr > bestCorr) {
                            bestCorr = corr
                            bestLag = lag
                        }
                    }
                }
                if (bestLag > 0 && bestCorr > 0.45) {
                    f0Sum += sampleRate.toDouble() / bestLag
                    f0Count++
                }

                // Spectrum magnitude (windowed DFT over positive bins)
                val windowed = DoubleArray(FRAME_SIZE) { i ->
                    floatSamples[offset + i].toDouble() * hann[i]
                }
                val mag = computeHalfSpectrumMagnitude(windowed)

                var weightedFreq = 0.0
                var totalMag = 0.0
                for (k in 1 until binCount) {
                    val m = mag[k]
                    weightedFreq += fftFreqHz[k] * m
                    totalMag += m
                }
                if (totalMag > 1e-9) {
                    centroidSum += weightedFreq / totalMag
                }

                // 12-band Mel filterbank energies (100 Hz .. 7600 Hz)
                val melEnergies = computeMelBandLogEnergies(mag, sampleRate, FRAME_SIZE, MEL_BANDS)
                for (b in 0 until MEL_BANDS) {
                    melSum[b] += melEnergies[b]
                }
            }
            offset += HOP_SIZE
        }

        if (voicedFrames < 6) {
            return Result.failure(
                IllegalStateException("Not enough speech detected ($voicedFrames voiced frames). Speak clearly closer to the mic.")
            )
        }

        val avgF0 = if (f0Count > 0) (f0Sum / f0Count).toFloat() else 150f
        val avgZcr = (zcrSum / voicedFrames).toFloat()
        val avgCentroid = (centroidSum / voicedFrames).toFloat()

        // Construct 15-dim speaker acoustic embedding and L2-normalize
        val rawVec = FloatArray(3 + MEL_BANDS)
        rawVec[0] = ((avgF0 - 70f) / 270f).coerceIn(0f, 1.5f)
        rawVec[1] = (avgZcr * 4f).coerceIn(0f, 1.5f)
        rawVec[2] = (avgCentroid / 4000f).coerceIn(0f, 1.5f)
        for (b in 0 until MEL_BANDS) {
            rawVec[3 + b] = (melSum[b] / voicedFrames).toFloat()
        }

        val normalized = l2Normalize(rawVec)
        return Result.success(
            SpeakerVoiceprint(
                embedding = normalized,
                f0Hz = avgF0,
                voicedFrames = voicedFrames
            )
        )
    }

    fun verify(enrolled: SpeakerVoiceprint, probe: SpeakerVoiceprint): SpeakerVerificationResult {
        val sim = cosineSimilarity(enrolled.embedding, probe.embedding)
        return SpeakerVerificationResult(
            isMatch = sim >= MATCH_THRESHOLD,
            similarity = sim,
            probeF0Hz = probe.f0Hz,
            enrolledF0Hz = enrolled.f0Hz
        )
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom <= 1e-9) 0f else (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    fun encodeProfile(profile: SpeakerVoiceprint): String {
        val arr = JSONArray()
        profile.embedding.forEach { arr.put(it.toDouble()) }
        return JSONObject().apply {
            put("embedding", arr)
            put("f0Hz", profile.f0Hz.toDouble())
            put("voicedFrames", profile.voicedFrames)
            put("enrolledAtMs", profile.enrolledAtMs)
        }.toString()
    }

    fun decodeProfile(jsonRaw: String): SpeakerVoiceprint? {
        if (jsonRaw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(jsonRaw)
            val arr = obj.getJSONArray("embedding")
            val vec = FloatArray(arr.length()) { idx -> arr.getDouble(idx).toFloat() }
            SpeakerVoiceprint(
                embedding = vec,
                f0Hz = obj.optDouble("f0Hz", 0.0).toFloat(),
                voicedFrames = obj.optInt("voicedFrames", 0),
                enrolledAtMs = obj.optLong("enrolledAtMs", 0L)
            )
        }.getOrNull()
    }

    private fun computeHalfSpectrumMagnitude(samples: DoubleArray): DoubleArray {
        val n = samples.size
        val real = samples.copyOf()
        val imag = DoubleArray(n)
        fftRadix2(real, imag)
        val half = n / 2
        return DoubleArray(half) { k ->
            sqrt(real[k] * real[k] + imag[k] * imag[k])
        }
    }

    private fun fftRadix2(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wLenReal = cos(ang)
            val wLenImag = sin(ang)
            var i = 0
            while (i < n) {
                var wReal = 1.0
                var wImag = 0.0
                val halfLen = len / 2
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wReal - imag[i + k + halfLen] * wImag
                    val vI = real[i + k + halfLen] * wImag + imag[i + k + halfLen] * wReal
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI
                    val nextWR = wReal * wLenReal - wImag * wLenImag
                    wImag = wReal * wLenImag + wImag * wLenReal
                    wReal = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun computeMelBandLogEnergies(
        mag: DoubleArray,
        sampleRate: Int,
        fftSize: Int,
        bands: Int
    ): DoubleArray {
        val lowMel = hzToMel(100.0)
        val highMel = hzToMel((sampleRate / 2.0).coerceAtMost(7600.0))
        val melPoints = DoubleArray(bands + 2) { i ->
            lowMel + (highMel - lowMel) * i / (bands + 1)
        }
        val binIndices = IntArray(bands + 2) { i ->
            val hz = melToHz(melPoints[i])
            ((fftSize + 1) * hz / sampleRate).toInt().coerceIn(0, mag.size - 1)
        }

        val out = DoubleArray(bands)
        for (m in 1..bands) {
            val left = binIndices[m - 1]
            val center = binIndices[m]
            val right = binIndices[m + 1]
            var energy = 0.0
            if (center > left) {
                for (k in left until center) {
                    val w = (k - left).toDouble() / (center - left)
                    energy += mag[k] * mag[k] * w
                }
            }
            if (right > center) {
                for (k in center..right) {
                    val w = (right - k).toDouble() / (right - center).coerceAtLeast(1)
                    energy += mag[k] * mag[k] * w
                }
            }
            out[m - 1] = ln(energy + 1e-6)
        }
        return out
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sq = 0.0
        for (v in vec) sq += v * v
        val norm = sqrt(sq).toFloat()
        if (norm <= 1e-6f) return vec
        return FloatArray(vec.size) { i -> vec[i] / norm }
    }
}
