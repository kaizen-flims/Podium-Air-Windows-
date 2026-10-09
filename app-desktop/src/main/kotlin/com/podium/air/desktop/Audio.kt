// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.podium.air.domain.QueueEntry
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

data class AudioState(val entry: QueueEntry? = null, val playing: Boolean = false, val loading: Boolean = false,
    val positionMs: Long = 0, val durationMs: Long = 0, val error: String? = null, val fading: Boolean = false)
interface AudioEngine : AutoCloseable {
    val state: StateFlow<AudioState>
    var onEnd: (String) -> Unit
    var onAdvance: (QueueEntry) -> Unit
    fun open(entry: QueueEntry, play: Boolean = true)
    fun setUpcoming(entry: QueueEntry?)
    fun toggle()
    fun pause()
    fun seek(ms: Long)
    fun configure(preferences: Preferences)
    fun stop()
}
object FxRuntime {
    private val started = AtomicBoolean(false)
    fun start() { if (started.compareAndSet(false, true)) Platform.startup { Platform.setImplicitExit(false) } }
    fun dispatch(block: () -> Unit) { start(); if (Platform.isFxApplicationThread()) block() else Platform.runLater(block) }
    fun exit() { if (started.get()) Platform.exit() }
}

/** All MediaPlayer lifecycle and controls are confined to the JavaFX application thread. */
class JavaFxAudioEngine : AudioEngine {
    private val mutable = MutableStateFlow(AudioState())
    override val state: StateFlow<AudioState> = mutable
    override var onEnd: (String) -> Unit = {}
    override var onAdvance: (QueueEntry) -> Unit = {}
    private var player: MediaPlayer? = null
    private var standby: MediaPlayer? = null
    private var outgoing: MediaPlayer? = null
    private var active: QueueEntry? = null
    private var upcoming: QueueEntry? = null
    private var standbyKey: String? = null
    private var preferences = Preferences()
    private var fadeElapsed = 0.0
    private var fadeLength = 0.0
    private var previousTick = System.nanoTime()
    private val closed = AtomicBoolean(false)
    private var ticker: Timeline? = null
    init {
        FxRuntime.dispatch {
            ticker = Timeline(KeyFrame(Duration.millis(100.0), EventHandler<ActionEvent> { tick() })).apply {
                cycleCount = Timeline.INDEFINITE; play()
            }
        }
    }
    private fun command(block: () -> Unit) { if (!closed.get()) FxRuntime.dispatch { if (!closed.get()) runCatching(block).onFailure { report(it) } } }
    private fun report(error: Throwable) { mutable.value = mutable.value.copy(loading = false, playing = false, error = error.message ?: "Audio playback failed") }
    override fun open(entry: QueueEntry, play: Boolean) = command {
        disposePlayers(); active = entry
        mutable.value = AudioState(entry = entry, loading = true)
        val path = entry.song.localPath ?: error("Only local media is available in this build")
        require(File(path).isFile) { "File is missing: $path. Reimport it or remove it from your library." }
        val newPlayer = makePlayer(entry)
        player = newPlayer
        newPlayer.setOnReady {
            if (player === newPlayer && !closed.get()) {
                applyPreferences(newPlayer)
                mutable.value = mutable.value.copy(loading = false, durationMs = finiteMs(newPlayer.totalDuration))
                if (play) newPlayer.play()
            }
        }
    }
    private fun makePlayer(entry: QueueEntry): MediaPlayer {
        val media = Media(File(requireNotNull(entry.song.localPath)).toURI().toString())
        return MediaPlayer(media).apply {
            setOnError { if (player === this) report(error ?: IllegalStateException("Media could not be decoded")) }
            media.setOnError { if (player === this) report(media.error ?: IllegalStateException("Unsupported media")) }
            setOnPlaying { if (player === this) mutable.value = mutable.value.copy(playing = true, loading = false, error = null) }
            setOnPaused { if (player === this) mutable.value = mutable.value.copy(playing = false) }
            setOnEndOfMedia {
                if (player === this && !closed.get()) {
                    finishFade()
                    mutable.value = mutable.value.copy(playing = false, positionMs = finiteMs(totalDuration))
                    onEnd(entry.key)
                }
            }
        }
    }
    override fun setUpcoming(entry: QueueEntry?) = command {
        if (upcoming?.key != entry?.key) { standby?.dispose(); standby = null; standbyKey = null }
        upcoming = entry
    }
    private fun prepareNext() {
        val next = upcoming ?: return
        if (standby != null || next.key == active?.key || outgoing != null) return
        // Failure to predecode leaves the current track playing. Opening the next track reports its error normally.
        runCatching {
            require(File(requireNotNull(next.song.localPath)).isFile)
            standby = makePlayer(next).apply { volume = 0.0; setOnReady { applyPreferences(this); volume = 0.0 } }
            standbyKey = next.key
        }
    }
    private fun beginFade() {
        val next = upcoming ?: return
        val incoming = standby ?: return
        val current = player ?: return
        if (incoming.status != MediaPlayer.Status.READY || standbyKey != next.key) return
        fadeLength = minOf(preferences.crossfadeSeconds * 1000.0,
            (current.totalDuration.toMillis() - current.currentTime.toMillis()).coerceAtLeast(1.0),
            incoming.totalDuration.toMillis() / 2).coerceAtLeast(1.0)
        fadeElapsed = 0.0
        outgoing = current; player = incoming; active = next; standby = null; standbyKey = null
        mutable.value = AudioState(entry = next, playing = true, durationMs = finiteMs(incoming.totalDuration), fading = true)
        incoming.volume = 0.0; incoming.play()
        onAdvance(next)
    }
    private fun tick() {
        if (closed.get()) return
        val now = System.nanoTime()
        val delta = ((now - previousTick) / 1_000_000.0).coerceIn(0.0, 500.0)
        previousTick = now
        val current = player ?: return
        val playing = current.status == MediaPlayer.Status.PLAYING
        if (outgoing != null && playing) {
            fadeElapsed += delta
            val progress = (fadeElapsed / fadeLength).coerceIn(0.0, 1.0)
            current.volume = preferences.volume.toDouble() * sin(progress * Math.PI / 2)
            outgoing?.volume = preferences.volume.toDouble() * cos(progress * Math.PI / 2)
            if (progress >= 1) finishFade()
        } else if (playing && preferences.crossfadeSeconds > 0 && preferences.speed == 1f) {
            val remaining = finiteMs(current.totalDuration) - finiteMs(current.currentTime)
            if (remaining in 1..15000) prepareNext()
            if (remaining in 1..(preferences.crossfadeSeconds * 1000L)) beginFade()
        }
        val actual = player ?: return
        mutable.value = mutable.value.copy(positionMs = finiteMs(actual.currentTime), durationMs = finiteMs(actual.totalDuration),
            playing = actual.status == MediaPlayer.Status.PLAYING, fading = outgoing != null)
    }
    private fun finishFade() { outgoing?.dispose(); outgoing = null; player?.volume = preferences.volume.toDouble(); mutable.value = mutable.value.copy(fading = false) }
    override fun toggle() = command { player?.let { if (it.status == MediaPlayer.Status.PLAYING) { it.pause(); outgoing?.pause() } else { it.play(); outgoing?.play() } } }
    override fun pause() = command { player?.pause(); outgoing?.pause() }
    override fun seek(ms: Long) = command {
        finishFade(); standby?.dispose(); standby = null; standbyKey = null
        player?.let { it.seek(Duration.millis(ms.coerceIn(0, finiteMs(it.totalDuration)).toDouble())) }
    }
    override fun configure(preferences: Preferences) = command {
        this.preferences = preferences
        if (preferences.crossfadeSeconds == 0 || preferences.speed != 1f) finishFade()
        listOfNotNull(player, outgoing).forEach(::applyPreferences)
    }
    private fun applyPreferences(target: MediaPlayer) {
        target.volume = preferences.volume.coerceIn(0f, 1f).toDouble()
        target.rate = preferences.speed.toDouble()
        val equalizer = target.audioEqualizer
        equalizer.isEnabled = preferences.equalizer.any { it != 0.0 }
        equalizer.bands.forEachIndexed { index, band -> band.gain = preferences.equalizer.getOrElse(index) { 0.0 }.coerceIn(-12.0, 12.0) }
    }
    override fun stop() = command { disposePlayers(); active = null; upcoming = null; mutable.value = AudioState() }
    private fun disposePlayers() { player?.dispose(); standby?.dispose(); outgoing?.dispose(); player = null; standby = null; outgoing = null; standbyKey = null }
    override fun close() {
        if (closed.compareAndSet(false, true)) FxRuntime.dispatch { ticker?.stop(); disposePlayers(); mutable.value = AudioState() }
    }
}
private fun finiteMs(duration: Duration): Long = if (duration.isUnknown || duration.isIndefinite) 0 else duration.toMillis().toLong().coerceAtLeast(0)
