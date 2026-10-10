// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.playback.smart.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.sampled.AudioSystem
import kotlin.math.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/** PCM measurements, not metadata guesses. Failed/uncertain analysis stays on the source planner's lower tiers. */
class SmartAudio(private val directory: File = File(defaultDataDirectory(), "automix")) {
    private val providers = ConcurrentHashMap<String, String>()
    fun providers(trackId: String): String = providers[trackId] ?: "native DSP"
    private fun remember(text: String, trackId: String): TrackAnalysis {
        providers[trackId] = (Json.parseToJsonElement(text).jsonObject["analysisProviders"] as? JsonPrimitive)?.contentOrNull ?: "native DSP"
        return TrackFeatures.parse(text, trackId)
    }
    fun analyze(file: File, trackId: String, checkCancelled: () -> Unit): TrackAnalysis {
        check(TrackFeatures.available) { "Native Automix analysis is unavailable." }
        // preparePcm names immutable decoded files by the source identity. Its access-time
        // refresh must not invalidate an expensive analysis of unchanged PCM.
        val identity = "dsp-model-v2|" + file.canonicalPath + "|" + file.length()
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        directory.mkdirs()
        val cached = File(directory, "$key.json")
        if (cached.isFile && cached.length() < 2_000_000) runCatching { remember(cached.readText(), trackId) }.getOrNull()?.let { return it }
        val measured = AudioSystem.getAudioInputStream(file).use { input ->
            val format = input.format
            require(format.sampleSizeInBits == 16 && !format.isBigEndian && format.channels in 1..2)
            require(input.frameLength in 1..80_000_000) { "Track is too long for bounded Automix analysis." }
            val duration = input.frameLength / format.sampleRate.toDouble()
            require(duration <= 1800) { "Tracks longer than 30 minutes use the standard crossfade." }
            val samples = FloatArray(input.frameLength.toInt())
            val frameBytes = format.channels * 2
            val buffer = ByteArray(frameBytes * 8192); var cursor = 0
            while (true) {
                checkCancelled(); val count = input.read(buffer); if (count < 0) break
                require(count % frameBytes == 0)
                for (offset in 0 until count step frameBytes) {
                    var value = 0.0
                    for (channel in 0 until format.channels) {
                        val byte = offset + channel * 2
                        value += ((buffer[byte].toInt() and 255) or (buffer[byte + 1].toInt() shl 8)).toShort().toInt() / 32768.0
                    }
                    require(cursor < samples.size) { "PCM frame count differs from the WAV header." }
                    samples[cursor++] = (value / format.channels).toFloat()
                }
            }
            require(cursor == samples.size) { "PCM track is truncated." }
            checkCancelled()
            val reduced = TrackFeatures.resample(samples, format.sampleRate.toDouble())
            checkCancelled(); TrackFeatures.analyze(reduced, duration, trackId)
        }
        checkCancelled()
        val neural = neuralAnalysis(file, measured.first, checkCancelled)
        checkCancelled()
        val text = buildJsonObject {
            Json.parseToJsonElement(measured.second).jsonObject.forEach { (key, value) -> put(key, value) }
            put("bpm", neural.analysis.bpm); put("beatInterval", neural.analysis.beatInterval)
            put("beatConfidence", neural.analysis.beatConfidence); put("firstBeat", neural.analysis.firstBeat)
            put("downbeats", JsonArray(neural.analysis.downbeats.map(::JsonPrimitive)))
            put("vocalActivityMask", JsonArray(neural.analysis.vocalActivityMask.map(::JsonPrimitive)))
            put("analysisProviders", listOfNotNull("native DSP", "Beat This! grid".takeIf { neural.beatGrid }, "open-unmix vocal mask".takeIf { neural.vocalMask }).joinToString(" + "))
        }.toString()
        val temp = Files.createTempFile(directory.toPath(), "analysis-", ".json")
        try { Files.writeString(temp, text); Files.move(temp, cached.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temp) }
        return remember(text, trackId)
    }
}

data class SourceTiming(val cueMs: Long, val stretchMs: Long, val ratio: Double, val originalDurationMs: Long) {
    fun position(outputMs: Long): Long = (cueMs + min(outputMs, stretchMs) * ratio + (outputMs - stretchMs).coerceAtLeast(0)).toLong().coerceIn(0, originalDurationMs)
}
data class SmartPrepared(val file: File, val plan: TransitionPlan, val timing: SourceTiming)

/** Near-unity WSOLA over the actual transition PCM. Original-rate remainder follows the overlap. */
internal object Wsola {
    fun stretch(input: FloatArray, channels: Int, rate: Int, ratio: Double, outputFrames: Int, checkCancelled: () -> Unit = {}): FloatArray {
        require(channels in 1..2 && rate in 8000..192000 && ratio.isFinite() && ratio in 0.9..1.1 && outputFrames > 0)
        require(input.size % channels == 0 && input.size / channels >= ceil(outputFrames * ratio).toInt())
        val inputFrames = input.size / channels
        val window = (rate * 0.04).roundToInt().coerceAtLeast(64)
        val hop = window / 2; val search = (rate * 0.008).roundToInt().coerceAtLeast(8)
        val output = FloatArray((outputFrames + window) * channels)
        val weight = FloatArray(outputFrames + window)
        var previous = 0
        fun correlation(candidate: Int, old: Int): Double {
            var dot = 0.0; var left = 0.0; var right = 0.0
            for (i in 0 until hop step 4) {
                for (ch in 0 until channels) {
                    val a = input[((old + hop + i).coerceAtMost(inputFrames - 1)) * channels + ch].toDouble()
                    val b = input[((candidate + i).coerceAtMost(inputFrames - 1)) * channels + ch].toDouble()
                    dot += a * b; left += a * a; right += b * b
                }
            }
            return if (left * right > 1e-16) dot / sqrt(left * right) else -1.0
        }
        for (position in 0 until outputFrames step hop) {
            checkCancelled()
            val expected = (position * ratio).roundToInt().coerceIn(0, (inputFrames - window).coerceAtLeast(0))
            var best = expected
            if (position > 0) {
                val start = (expected - search).coerceAtLeast(0); val end = (expected + search).coerceAtMost((inputFrames - window).coerceAtLeast(0))
                var score = correlation(expected, previous)
                for (candidate in start..end step 16) {
                    val next = correlation(candidate, previous) - abs(candidate - expected) * 1e-6
                    if (next > score) { score = next; best = candidate }
                }
                val coarse = best
                for (candidate in (coarse - 16).coerceAtLeast(start)..(coarse + 16).coerceAtMost(end)) {
                    val next = correlation(candidate, previous) - abs(candidate - expected) * 1e-6
                    if (next > score) { score = next; best = candidate }
                }
            }
            previous = best
            for (i in 0 until window) {
                val target = position + i
                val amplitude = (0.5 - 0.5 * cos(2 * Math.PI * i / (window - 1))).toFloat()
                weight[target] += amplitude
                for (ch in 0 until channels) output[target * channels + ch] += input[(best + i).coerceAtMost(inputFrames - 1) * channels + ch] * amplitude
            }
        }
        val result = FloatArray(outputFrames * channels)
        val cut = (outputFrames * ratio).roundToInt().coerceAtMost(inputFrames)
        val seam = (rate * 0.01).roundToInt().coerceAtMost(outputFrames)
        for (i in 0 until outputFrames) for (ch in 0 until channels) {
            val mixed = if (weight[i] > 1e-7) output[i * channels + ch] / weight[i] else input[(i * ratio).toInt().coerceAtMost(inputFrames - 1) * channels + ch]
            val blend = if (i >= outputFrames - seam) (i - (outputFrames - seam) + 1).toFloat() / seam else 0f
            val original = input[(cut - outputFrames + i).coerceIn(0, inputFrames - 1) * channels + ch]
            result[i * channels + ch] = mixed * (1f - blend) + original * blend
        }
        return result
    }
}

internal fun prepareSmartTransition(pcm: File, plan: TransitionPlan, checkCancelled: () -> Unit): SmartPrepared {
    val directory = File(defaultDataDirectory(), "automix/transitions").apply { mkdirs() }
    val target = Files.createTempFile(directory.toPath(), "transition-", ".wav").toFile()
    try {
        AudioSystem.getAudioInputStream(pcm).use { input ->
            val rate = input.format.sampleRate.toInt(); val channels = input.format.channels
            require(input.format.sampleSizeInBits == 16 && !input.format.isBigEndian && channels in 1..2)
            val total = input.frameLength; val frameBytes = channels * 2
            val cue = (plan.incomingCueTime * rate).roundToLong().coerceIn(0, (total - 1).coerceAtLeast(0))
            var skip = cue * frameBytes
            while (skip > 0) { checkCancelled(); val count = input.skip(skip); require(count > 0) { "Could not seek the incoming PCM." }; skip -= count }
            val ratio = plan.incomingPlaybackRate.coerceIn(0.9, 1.1)
            val outputFrames = (plan.fadeSeconds * rate).roundToInt().coerceIn(1, rate * 12)
            val consume = (outputFrames * ratio).roundToInt()
            require(cue + consume + rate / 10 < total) { "Incoming track has insufficient audio after its transition cue." }
            val prefixBytes = input.readNBytes(consume * frameBytes)
            require(prefixBytes.size == consume * frameBytes)
            val extra = input.readNBytes(rate / 10 * frameBytes)
            val prefix = prefixBytes + extra
            val samples = FloatArray(prefix.size / 2) { i -> (((prefix[i * 2].toInt() and 255) or (prefix[i * 2 + 1].toInt() shl 8)).toShort().toInt() / 32768f) }
            val stretched = if (abs(ratio - 1.0) < 1e-6) samples.copyOf(outputFrames * channels) else Wsola.stretch(samples, channels, rate, ratio, outputFrames, checkCancelled)
            PcmWaveWriter(target, rate, channels).use { wave ->
                val output = ByteArray(stretched.size * 2)
                for (i in stretched.indices) { val value = (stretched[i] * 32768).roundToInt().coerceIn(-32768, 32767); output[i * 2] = value.toByte(); output[i * 2 + 1] = (value shr 8).toByte() }
                wave.write(output); wave.write(extra)
                val buffer = ByteArray(8192)
                while (true) { checkCancelled(); val count = input.read(buffer); if (count < 0) break; if (count > 0) wave.write(buffer.copyOf(count)) }
            }
            checkCancelled()
            return SmartPrepared(target, plan, SourceTiming(cue * 1000 / rate, outputFrames * 1000L / rate, ratio, total * 1000 / rate))
        }
    } catch (error: Throwable) { target.delete(); throw error }
}
