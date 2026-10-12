// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.playback.smart.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import kotlin.math.*
import kotlin.test.*

class SmartAudioTest {
    @Test fun pitchPreservingStretchChangesDurationWithoutDetuningStereo() {
        val rate = 16000
        val frames = rate * 4
        val stereo = FloatArray(frames * 2) { i -> (sin((i / 2) * 2 * Math.PI * 440 / rate) * 0.4 * if (i % 2 == 0) 1 else -1).toFloat() }
        for (ratio in listOf(0.96, 1.04)) {
            val outputFrames = (frames / ratio).toInt() - rate / 10
            val result = Wsola.stretch(stereo, 2, rate, ratio, outputFrames)
            assertEquals(outputFrames * 2, result.size)
            assertTrue(result.all { it.isFinite() && abs(it) <= 0.5 })
            val start = rate / 2; val end = outputFrames - rate / 2
            val rising = (start + 1 until end).count { result[(it - 1) * 2] <= 0 && result[it * 2] > 0 }
            val frequency = rising * rate.toDouble() / (end - start)
            assertTrue(abs(frequency - 440) < 3, "WSOLA detuned the input: $frequency")
            assertTrue((0 until outputFrames).all { abs(result[it * 2] + result[it * 2 + 1]) < 1e-6 })
        }
    }
    @Test fun cancellationAndInvalidPcmAreRealErrors() {
        assertFailsWith<IllegalArgumentException> { Wsola.stretch(FloatArray(100), 2, 16000, Double.NaN, 100) }
        var passes = 0
        assertFailsWith<java.util.concurrent.CancellationException> {
            Wsola.stretch(FloatArray(80000), 1, 16000, 1.03, 40000) { if (++passes > 2) throw java.util.concurrent.CancellationException() }
        }
    }
    @Test fun originalTrackClockSurvivesStretchedIntroAndNormalRemainder() {
        val timing = SourceTiming(10000, 5000, 1.04, 180000)
        assertEquals(10000, timing.position(0))
        assertEquals(12600, timing.position(2500))
        assertEquals(15200, timing.position(5000))
        assertEquals(20200, timing.position(10000))
        assertEquals(180000, timing.position(999999))
    }
    @Test fun nativeMeasurementsRunThroughThePackagedJniLibrary() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        assertTrue(TrackFeatures.available, "Windows package is missing its native analyzer")
        val rate = TrackFeatures.sampleRate.toInt()
        val samples = FloatArray(rate * 60) { i ->
            val t = i / rate.toDouble(); val beat = (t - 2).mod(0.5)
            if (t < 2 || t > 57) 0f else (0.05 * sin(t * 2 * Math.PI * 220) + if (beat < 0.055) 0.65 * exp(-beat * 70) * sin(beat * 2 * Math.PI * 90) else 0.0).toFloat()
        }
        val (analysis, json) = TrackFeatures.analyze(samples, 60.0, "fixture")
        assertTrue(abs(analysis.bpm - 120) < 3, "Measured BPM: " + analysis.bpm)
        assertTrue(analysis.contentEndTime in 56.0..58.0)
        assertTrue(analysis.energyCurve.isNotEmpty())
        assertEquals("fixture", TrackFeatures.parse(json, "fixture").trackId)
        val silence = TrackFeatures.analyze(FloatArray(rate * 5), 5.0, "silence").first
        assertTrue(silence.beatConfidence < MIN_BEATMATCH_CONFIDENCE)
    }
}
