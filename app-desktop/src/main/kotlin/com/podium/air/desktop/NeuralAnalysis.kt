// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.playback.smart.*
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.math.*

internal data class NeuralEvidence(val analysis: TrackAnalysis, val beatGrid: Boolean, val vocalMask: Boolean)

/** Same head/tail model strategy as the Android analyzer, with bounded PCM regions. */
internal fun neuralAnalysis(file: File, dsp: TrackAnalysis, checkCancelled: () -> Unit): NeuralEvidence {
    val beats = BeatTracker(checkCancelled)
    val vocals = VocalTracker(checkCancelled)
    data class Region(val grid: BeatTracker.Grid?, val vocal: FloatArray?, val vocalStart: Double)
    fun region(start: Double): Region {
        checkCancelled()
        val stereo = AudioSystem.getAudioInputStream(file).use { input ->
            val rate = input.format.sampleRate.toDouble(); val channels = input.format.channels; val frameBytes = channels * 2
            val skipFrames = (start * rate).roundToLong().coerceIn(0, input.frameLength)
            var skip = skipFrames * frameBytes
            while (skip > 0) { checkCancelled(); val count = input.skip(skip); require(count > 0); skip -= count }
            val count = min((BeatTracker.WINDOW_SECONDS * rate).roundToInt(), (input.frameLength - skipFrames).toInt())
            val bytes = input.readNBytes(count * frameBytes); require(bytes.size == count * frameBytes)
            val left = FloatArray(count); val right = FloatArray(count)
            for (frame in 0 until count) {
                val at = frame * frameBytes
                fun value(index: Int) = ((bytes[index].toInt() and 255) or (bytes[index + 1].toInt() shl 8)).toShort().toInt() / 32768f
                left[frame] = value(at); right[frame] = if (channels == 2) value(at + 2) else left[frame]
            }
            Triple(left, right, rate)
        }
        checkCancelled()
        val mono = FloatArray(stereo.first.size) { (stereo.first[it] + stereo.second[it]) * .5f }
        val melPcm = MelSpectrogram.resample(mono, stereo.third)
        val grid = melPcm?.let { beats.track(it, start) }
        checkCancelled()
        val vocalSeconds = (VocalTracker.FIXED_FRAMES - 2) * VocalSpectrogram.hop / VocalSpectrogram.sampleRate
        val frames = min(stereo.first.size, (vocalSeconds * stereo.third).toInt())
        // For the outgoing tail, keep the very end of the analyzed region, where mixing occurs.
        val vocalOffset = if (start > 0) (stereo.first.size - frames).coerceAtLeast(0) else 0
        val mask = if (frames > 0) vocals.track(stereo.first.copyOfRange(vocalOffset, vocalOffset + frames), stereo.second.copyOfRange(vocalOffset, vocalOffset + frames), stereo.third) else null
        checkCancelled()
        return Region(grid, mask, start + vocalOffset / stereo.third)
    }
    try {
        val head = region(0.0)
        val tailStart = max(0.0, dsp.duration - BeatTracker.WINDOW_SECONDS)
        val tail = if (tailStart > BeatTracker.WINDOW_SECONDS / 2) region(tailStart) else null
        checkCancelled()
        val leading = tail?.grid ?: head.grid
        val masks = listOfNotNull(head, tail).filter { it.vocal != null }
        val measured = if (masks.isEmpty()) dsp.vocalActivityMask else dsp.energyCurve.mapIndexed { index, point ->
            // Preserve the DSP estimate wherever no model actually measured the PCM.
            var value = dsp.vocalActivityMask.getOrElse(index) { .5 }
            for (part in masks) {
                val frame = floor((point.time - part.vocalStart) * VocalSpectrogram.frameRate).toInt()
                part.vocal?.getOrNull(frame)?.takeIf { it.isFinite() }?.let { value = it.toDouble().coerceIn(0.0, 1.0) }
            }
            value
        }
        return NeuralEvidence(dsp.copy(
            bpm = leading?.bpm ?: dsp.bpm, beatInterval = leading?.beatInterval ?: dsp.beatInterval,
            beatConfidence = leading?.beatConfidence ?: dsp.beatConfidence,
            downbeats = (head.grid?.downbeats.orEmpty() + tail?.grid?.downbeats.orEmpty()).distinct().sorted().ifEmpty { dsp.downbeats },
            firstBeat = head.grid?.firstBeat ?: dsp.firstBeat, vocalActivityMask = measured,
        ), leading != null, masks.isNotEmpty())
    } finally { beats.release(); vocals.release() }
}
