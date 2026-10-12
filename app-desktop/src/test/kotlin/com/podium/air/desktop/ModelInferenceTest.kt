// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.music.bitchord.playback.smart.*
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.FloatBuffer
import kotlin.math.*
import kotlin.test.*

class ModelInferenceTest {
    private fun windows() { assumeTrue(System.getProperty("os.name").contains("Windows")); assertTrue(TrackFeatures.available) }
    @Test fun originalNativeMelFrontendFeedsTheBundledBeatGraph() {
        windows()
        val rate = MelSpectrogram.sampleRate.toInt()
        val pcm = FloatArray(rate * 20) { i ->
            val phase = i % (rate / 2)
            (if (phase < rate / 20) sin(2 * PI * 80 * phase / rate) * exp(-phase.toDouble() / (rate / 100)) else 0.0).toFloat()
        }
        val spectrogram = assertNotNull(MelSpectrogram.compute(pcm))
        assertEquals(128, spectrogram.mels); assertTrue(spectrogram.frames >= 990); assertTrue(spectrogram.values.all { it.isFinite() })
        assertNull(MelSpectrogram.compute(pcm, 16000.0))
        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            environment.createSession(ModelWeights.extract("beat_this_int8.onnx").absolutePath, options).use { session ->
                val shape = longArrayOf(1, spectrogram.frames.toLong(), spectrogram.mels.toLong())
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(spectrogram.values), shape).use { tensor ->
                    session.run(mapOf(session.inputNames.first() to tensor)).use { output ->
                        assertEquals(2, output.size())
                        repeat(2) { index ->
                            val values = (output.get(index) as OnnxTensor).floatBuffer
                            assertEquals(spectrogram.frames, values.remaining())
                            while (values.hasRemaining()) assertTrue(values.get().isFinite())
                        }
                    }
                }
            }
        }
    }
    @Test fun bundledVocalGraphRunsAgainstTheOriginalStereoStft() {
        windows()
        val rate = 44100
        val left = FloatArray(rate * 3) { (sin(2 * PI * 440 * it / rate) * .15).toFloat() }
        val right = FloatArray(rate * 3) { (sin(2 * PI * 660 * it / rate) * .1).toFloat() }
        val tracker = VocalTracker()
        try {
            val values = assertNotNull(tracker.track(left, right, rate.toDouble()), "Bundled open-unmix inference must succeed.")
            assertTrue(values.size in 120..135)
            assertTrue(values.all { it.isFinite() && it in 0f..1f })
        } finally { tracker.release() }
    }
    @Test fun cancelledBeatAnalysisDoesNotConsumeAnotherModelWindow() {
        windows()
        val tracker = BeatTracker { throw CancellationException("Track changed") }
        try { assertFailsWith<CancellationException> { tracker.track(FloatArray(22050)) } }
        finally { tracker.release() }
    }
}
