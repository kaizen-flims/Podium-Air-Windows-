// SPDX-License-Identifier: AGPL-3.0-or-later
// Windows adapter for Orchard/BitChord's native analysis ABI; original native notices retained.
package com.music.bitchord.playback.smart

import com.podium.air.desktop.defaultDataDirectory
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object TrackFeatures {
    val available: Boolean = runCatching {
        val explicit = System.getProperty("podium.analysis.library")
        if (explicit != null) System.load(File(explicit).absolutePath)
        else {
            val bytes = requireNotNull(javaClass.getResourceAsStream("/windows/PodiumAnalysis.dll")) {
                "The native Automix analyzer is missing from this installation."
            }.use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val folder = File(defaultDataDirectory(), "platform/analysis-$hash").apply { mkdirs() }
            val library = File(folder, "PodiumAnalysis.dll")
            if (!library.isFile || !library.readBytes().contentEquals(bytes)) {
                val temp = Files.createTempFile(folder.toPath(), "analysis-", ".dll")
                try { Files.write(temp, bytes); Files.move(temp, library.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                finally { Files.deleteIfExists(temp) }
            }
            System.load(library.absolutePath)
        }
    }.isSuccess
    val sampleRate: Double get() = if (available) nativeSampleRate() else 11025.0
    fun analyze(samples: FloatArray, duration: Double, trackId: String): Pair<TrackAnalysis, String> {
        check(available) { "Native Automix analysis could not be loaded." }
        require(samples.isNotEmpty() && samples.size <= 20_000_000 && samples.all { it.isFinite() })
        require(duration.isFinite() && duration > 0)
        val text = nativeAnalyze(samples, sampleRate, duration)
        return parse(text, trackId) to text
    }
    fun resample(samples: FloatArray, rate: Double): FloatArray {
        check(available)
        require(rate.isFinite() && rate in 8000.0..192000.0 && samples.size <= 80_000_000)
        return nativeResample(samples, rate, sampleRate)
    }
    fun parse(text: String, trackId: String): TrackAnalysis {
        val root = Json.parseToJsonElement(text).jsonObject
        fun number(name: String): Double = (root[name] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() } ?: 0.0
        fun numbers(name: String) = (root[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) }
        fun energy(name: String) = (root[name] as? JsonArray).orEmpty().mapNotNull { element ->
            val point = element as? JsonObject ?: return@mapNotNull null
            val time = (point["t"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val value = (point["e"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (time.isFinite() && value.isFinite()) EnergySample(time, value) else null
        }
        fun candidates(name: String) = (root[name] as? JsonArray).orEmpty().mapNotNull { element ->
            val point = element as? JsonObject ?: return@mapNotNull null
            val time = (point["t"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (!time.isFinite()) return@mapNotNull null
            MixCandidate(time, (point["s"] as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) ?: 0.0,
                (point["y"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
        return TrackAnalysis(status = TrackAnalysis.STATUS_READY, trackId = trackId, duration = number("duration"),
            bpm = number("bpm"), beatInterval = number("beatInterval"), firstBeat = number("firstBeat"),
            beatConfidence = number("beatConfidence"), key = (root["key"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            keyConfidence = number("keyConfidence"), downbeats = numbers("downbeats"), phraseBoundaries = numbers("phraseBoundaries"),
            audibleStartTime = number("audibleStartTime"), pickupTime = number("pickupTime"), introEndTime = number("introEndTime"),
            contentEndTime = number("contentEndTime"), outroStartTime = number("outroStartTime"), mixInTime = number("mixInTime"),
            mixOutTime = number("mixOutTime"), mixInCandidates = candidates("mixInCandidates"), mixOutCandidates = candidates("mixOutCandidates"),
            energyCurve = energy("energyCurve"), lowEnergyCurve = energy("lowEnergyCurve"), vocalActivityMask = numbers("vocalActivityMask"),
            vocalProbability = number("vocalProbability"))
    }
    @JvmStatic private external fun nativeAnalyze(samples: FloatArray, sampleRate: Double, duration: Double): String
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeResample(samples: FloatArray, inputRate: Double, outputRate: Double): FloatArray
}
