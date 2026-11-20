package com.kamusisyarat.speechpractice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight acoustic comparison for a reference recording and a child's recording.
 * It compares normalized short-window RMS and zero-crossing patterns after AAC/MP4 decoding.
 * This is intentionally an additional signal; STT text similarity remains part of the final score.
 */
object AudioSimilarity {
    fun score(reference: File, child: File): Int {
        val a = decode(reference)
        val b = decode(child)
        if (a.isEmpty() || b.isEmpty()) return 0

        val ra = features(a)
        val rb = features(b)
        if (ra.isEmpty() || rb.isEmpty()) return 0

        val n = minOf(ra.size, rb.size)
        val stepA = ra.size.toDouble() / n
        val stepB = rb.size.toDouble() / n
        var diff = 0.0
        for (i in 0 until n) {
            val fa = ra[(i * stepA).toInt().coerceAtMost(ra.lastIndex)]
            val fb = rb[(i * stepB).toInt().coerceAtMost(rb.lastIndex)]
            diff += abs(fa.first - fb.first) + abs(fa.second - fb.second)
        }
        val normalized = diff / n
        return (100.0 - normalized * 100.0).toInt().coerceIn(0, 100)
    }

    private fun features(samples: FloatArray): List<Pair<Double, Double>> {
        val frame = 1024
        val out = ArrayList<Pair<Double, Double>>()
        var p = 0
        while (p + frame <= samples.size) {
            var energy = 0.0
            var crossings = 0
            var prev = samples[p]
            for (i in 0 until frame) {
                val x = samples[p + i].toDouble()
                energy += x * x
                if ((prev >= 0f) != (samples[p + i] >= 0f)) crossings++
                prev = samples[p + i]
            }
            out += Pair(sqrt(energy / frame), crossings.toDouble() / frame)
            p += frame / 2
        }
        if (out.isEmpty()) return out
        val maxEnergy = out.maxOf { it.first }.coerceAtLeast(1e-9)
        return out.map { Pair(it.first / maxEnergy, it.second) }
    }

    private fun decode(file: File): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    break
                }
            }
            if (track < 0) return FloatArray(0)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val output = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val timeout = 10_000L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(timeout)
                    if (inIndex >= 0) {
                        val input = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex, 0, size, extractor.sampleTime, extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, timeout)
                when {
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        buffer.rewind()
                        if (info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buffer.get(bytes)
                            var i = 0
                            while (i + 1 < bytes.size) {
                                val v = (bytes[i].toInt() and 0xff) or
                                    (bytes[i + 1].toInt() shl 8)
                                output += (if (v > 32767) v - 65536 else v) / 32768f
                                i += 2
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                }
            }
            codec.stop()
            codec.release()
            extractor.release()
            return output.toFloatArray()
        } catch (_: Exception) {
            try { extractor.release() } catch (_: Exception) {}
            return FloatArray(0)
        }
    }
}
