// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.podium.air.domain.QueueEntry
import com.music.bitchord.playback.smart.*
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

data class AudioState(val entry: QueueEntry? = null, val playing: Boolean = false, val loading: Boolean = false,
    val positionMs: Long = 0, val durationMs: Long = 0, val error: String? = null, val fading: Boolean = false, val session: Long = 0, val completed: Boolean = false, val automixStatus: String? = null)
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
class JavaFxAudioEngine(val mediaFiles: MediaFiles = MediaFiles()) : AudioEngine {
    private val decoding = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var openJob: Job? = null
    private var standbyJob: Job? = null
    private var generation = 0L
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
    private val smartAudio = SmartAudio()
    private var nextPlan: TransitionPlan? = null
    private var standbyTiming: SourceTiming? = null
    private var currentTiming: SourceTiming? = null
    private var activeBaseFile: File? = null
    private var standbyBaseFile: File? = null
    private var currentTemp: File? = null
    private var standbyTemp: File? = null
    private var outgoingTemp: File? = null
    private var renderingPlan: TransitionPlan? = null
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
        openJob?.cancel(); standbyJob?.cancel(); generation++
        disposePlayers(); active = entry
        mutable.value = AudioState(entry = entry, loading = true, session = generation)
        val request = generation
        openJob = decoding.launch {
            try {
                val file = mediaFiles.prepare(File(requireNotNull(entry.song.localPath) { "Only local media is available." })) { ensureActive() }
                command {
                    if (request == generation && active?.key == entry.key) {
                        val newPlayer = makePlayer(entry, file)
                        activeBaseFile = file
                        player = newPlayer
                        newPlayer.setOnReady {
                            if (player === newPlayer && !closed.get()) {
                                applyPreferences(newPlayer)
                                mutable.value = mutable.value.copy(loading = false, durationMs = finiteMs(newPlayer.totalDuration))
                                if (play) newPlayer.play()
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { command { if (request == generation) report(error) } }
        }
    }
    private fun makePlayer(entry: QueueEntry, file: File): MediaPlayer {
        val media = Media(file.toURI().toString())
        return MediaPlayer(media).apply {
            setOnError { if (player === this) report(error ?: IllegalStateException("Media could not be decoded")) }
            media.setOnError { if (player === this) report(media.error ?: IllegalStateException("Unsupported media")) }
            setOnPlaying { if (player === this) mutable.value = mutable.value.copy(playing = true, loading = false, error = null) }
            setOnPaused { if (player === this) mutable.value = mutable.value.copy(playing = false) }
            setOnEndOfMedia {
                if (player === this && !closed.get()) {
                    finishFade()
                    mutable.value = mutable.value.copy(playing = false, positionMs = currentTiming?.originalDurationMs ?: finiteMs(totalDuration), completed = true)
                    onEnd(entry.key)
                }
            }
        }
    }
    override fun setUpcoming(entry: QueueEntry?) = command {
        if (upcoming?.key != entry?.key) {
            standbyJob?.cancel(); standbyJob = null
            standby?.dispose(); standby = null; standbyKey = null
            standbyTemp?.delete(); standbyTemp = null; standbyTiming = null; nextPlan = null; standbyBaseFile = null
        }
        upcoming = entry
        if (preferences.crossfadeSeconds > 0 || preferences.automix) prepareNext()
    }
    private fun prepareNext() {
        val next = upcoming ?: return
        if (standby != null || standbyJob?.isActive == true || next.key == active?.key || outgoing != null) return
        val request = generation
        val currentEntry = active
        val settings = preferences
        standbyJob = decoding.launch {
            try {
                var file = mediaFiles.prepare(File(requireNotNull(next.song.localPath))) { ensureActive() }
                var prepared: SmartPrepared? = null
                var status: String? = null
                var plan: TransitionPlan? = null
                var base = file
                if (settings.automix && settings.speed == 1f && currentEntry != null) {
                    command { if (request == generation) mutable.value = mutable.value.copy(automixStatus = "Analyzing the current and next track…") }
                    try {
                        val currentPcm = mediaFiles.preparePcm(File(requireNotNull(currentEntry.song.localPath))) { ensureActive() }
                        val nextPcm = mediaFiles.preparePcm(File(requireNotNull(next.song.localPath))) { ensureActive() }
                        val first = smartAudio.analyze(currentPcm, currentEntry.song.videoId) { ensureActive() }
                        val second = smartAudio.analyze(nextPcm, next.song.videoId) { ensureActive() }
                        fun info(entry: QueueEntry, analysis: TrackAnalysis) = TransitionTrackInfo(entry.song.videoId, (analysis.duration * 1000).toLong(), entry.song.title, entry.song.artist, entry.song.albumName.orEmpty())
                        var computed = planTransition(first, second, info(currentEntry, first), info(next, second), duration = first.duration,
                            fadeSeconds = settings.crossfadeSeconds.takeIf { it > 0 }?.toDouble() ?: 6.0, mode = CrossfadeMode.SMART)
                        // The source policy permits tempo correction only with measured confidence on both grids.
                        if (assessTransitionTier(first, second).tier != TransitionTier.BEATMATCHED) computed = computed.copy(incomingPlaybackRate = 1.0)
                        plan = computed
                        if (!computed.blocked) { prepared = prepareSmartTransition(nextPcm, computed) { ensureActive() }; file = prepared.file }
                        base = nextPcm
                        status = if (computed.blocked) "Automix: " + computed.reason else "Automix ready • " + first.bpm.toInt() + " → " + second.bpm.toInt() + " BPM • " + computed.transitionStyle.name.lowercase().replace('_', ' ')
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { base = file; plan = null; status = "Automix uses standard fade: " + error.message }
                }
                val owned = prepared
                var accepted = false
                try {
                FxRuntime.dispatch {
                    if (!closed.get() && request == generation && upcoming?.key == next.key && standby == null && preferences.automix == settings.automix && preferences.speed == settings.speed && preferences.crossfadeSeconds == settings.crossfadeSeconds) {
                        runCatching { makePlayer(next, file) }.onSuccess { candidate ->
                            standby = candidate.apply { volume = 0.0; setOnReady { applyPreferences(this); volume = 0.0 } }
                            standbyKey = next.key
                            standbyTemp = owned?.file; standbyTiming = owned?.timing; standbyBaseFile = base; nextPlan = plan
                            accepted = true
                            if (status != null) mutable.value = mutable.value.copy(automixStatus = status)
                        }
                    }
                    if (!accepted) owned?.file?.delete()
                }
                } catch (error: Throwable) { owned?.file?.delete(); throw error }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The active track keeps playing; opening this track reports its error. */ }
        }
    }
    private fun beginFade(plan: TransitionPlan? = null) {
        val next = upcoming ?: return
        val incoming = standby ?: return
        val current = player ?: return
        if (incoming.status != MediaPlayer.Status.READY || standbyKey != next.key) return
        fadeLength = minOf((plan?.fadeSeconds ?: preferences.crossfadeSeconds.takeIf { it > 0 }?.toDouble() ?: 6.0) * 1000.0,
            ((plan?.transitionEnd?.times(1000) ?: current.totalDuration.toMillis()) - (currentTiming?.position(finiteMs(current.currentTime)) ?: finiteMs(current.currentTime))).coerceAtLeast(1.0),
            incoming.totalDuration.toMillis() / 2).coerceAtLeast(1.0)
        fadeElapsed = 0.0
        outgoing = current; player = incoming; active = next; standby = null; standbyKey = null
        outgoingTemp = currentTemp; currentTemp = standbyTemp; standbyTemp = null
        currentTiming = standbyTiming; standbyTiming = null; activeBaseFile = standbyBaseFile; standbyBaseFile = null
        renderingPlan = plan; nextPlan = null
        mutable.value = AudioState(entry = next, playing = true, durationMs = currentTiming?.originalDurationMs ?: finiteMs(incoming.totalDuration), fading = true, session = generation,
            positionMs = currentTiming?.position(0) ?: 0, automixStatus = mutable.value.automixStatus)
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
            applyTransitionEqualizer(progress)
            if (progress >= 1) finishFade()
        } else if (playing && (preferences.crossfadeSeconds > 0 || preferences.automix) && preferences.speed == 1f) {
            val position = currentTiming?.position(finiteMs(current.currentTime)) ?: finiteMs(current.currentTime)
            val remaining = (currentTiming?.originalDurationMs ?: finiteMs(current.totalDuration)) - position
            if (preferences.automix || remaining in 1..15000) prepareNext()
            val plan = nextPlan
            if (preferences.automix && plan != null) {
                if (!plan.blocked && position >= plan.transitionStart * 1000 && position < plan.transitionEnd * 1000) beginFade(plan)
            } else if (remaining in 1..((preferences.crossfadeSeconds.takeIf { it > 0 } ?: 6) * 1000L)) beginFade()
        }
        val actual = player ?: return
        mutable.value = mutable.value.copy(positionMs = currentTiming?.position(finiteMs(actual.currentTime)) ?: finiteMs(actual.currentTime), durationMs = currentTiming?.originalDurationMs ?: finiteMs(actual.totalDuration),
            playing = actual.status == MediaPlayer.Status.PLAYING, fading = outgoing != null)
    }
    private fun applyTransitionEqualizer(progress: Double) {
        val plan = renderingPlan ?: return
        fun apply(target: MediaPlayer?, incoming: Boolean) {
            target ?: return
            target.audioEqualizer.isEnabled = true
            target.audioEqualizer.bands.forEachIndexed { index, band ->
                val attenuation = when {
                    plan.bassSwap && index < 4 -> if (incoming) -12.0 * (1 - (progress / plan.bassSwapFraction.coerceIn(0.2, 0.8)).coerceIn(0.0, 1.0)) else -12.0 * ((progress - plan.bassSwapFraction.coerceIn(0.2, 0.8)) / (1 - plan.bassSwapFraction.coerceIn(0.2, 0.8))).coerceIn(0.0, 1.0)
                    plan.filterSweep > 0 && !incoming && index >= 4 -> -12.0 * progress * (index - 3) / 6
                    else -> 0.0
                }
                band.gain = (preferences.equalizer.getOrElse(index) { 0.0 } + attenuation).coerceIn(-24.0, 12.0)
            }
        }
        apply(player, true); apply(outgoing, false)
    }
    private fun finishFade() { outgoing?.dispose(); outgoing = null; outgoingTemp?.delete(); outgoingTemp = null; renderingPlan = null; player?.let(::applyPreferences); mutable.value = mutable.value.copy(fading = false) }
    override fun toggle() = command { player?.let { if (it.status == MediaPlayer.Status.PLAYING) { it.pause(); outgoing?.pause() } else { it.play(); outgoing?.play() } } }
    override fun pause() = command { player?.pause(); outgoing?.pause() }
    override fun seek(ms: Long) = command {
        finishFade(); standbyJob?.cancel(); standbyJob = null; standby?.dispose(); standby = null; standbyKey = null
        standbyTemp?.delete(); standbyTemp = null; standbyTiming = null; standbyBaseFile = null; nextPlan = null
        if (currentTiming != null) {
            val entry = active ?: return@command
            val base = activeBaseFile ?: return@command
            val old = player; val playing = old?.status == MediaPlayer.Status.PLAYING
            old?.dispose(); currentTemp?.delete(); currentTemp = null; currentTiming = null
            mutable.value = mutable.value.copy(loading = true, playing = false)
            val replacement = makePlayer(entry, base); player = replacement
            replacement.setOnReady { if (player === replacement && !closed.get()) { applyPreferences(replacement); replacement.seek(Duration.millis(ms.coerceIn(0, finiteMs(replacement.totalDuration)).toDouble())); mutable.value = mutable.value.copy(loading = false, durationMs = finiteMs(replacement.totalDuration)); if (playing) replacement.play() } }
        } else player?.let { it.seek(Duration.millis(ms.coerceIn(0, finiteMs(it.totalDuration)).toDouble())) }
    }
    override fun configure(preferences: Preferences) = command {
        val replan = this.preferences.automix != preferences.automix || this.preferences.crossfadeSeconds != preferences.crossfadeSeconds || this.preferences.speed != preferences.speed
        this.preferences = preferences
        if (replan) { standbyJob?.cancel(); standbyJob = null; standby?.dispose(); standby = null; standbyKey = null; standbyTemp?.delete(); standbyTemp = null; nextPlan = null; standbyTiming = null }
        if (preferences.crossfadeSeconds == 0 || preferences.speed != 1f) finishFade()
        listOfNotNull(player, outgoing).forEach(::applyPreferences)
        if (replan && preferences.automix) prepareNext()
        if (!preferences.automix) { mutable.value = mutable.value.copy(automixStatus = null); if (currentTiming != null) seek(mutable.value.positionMs) }
    }
    private fun applyPreferences(target: MediaPlayer) {
        val progress = (fadeElapsed / fadeLength.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        val envelope = when {
            outgoing != null && target === player -> sin(progress * Math.PI / 2)
            target === outgoing -> cos(progress * Math.PI / 2)
            else -> 1.0
        }
        target.volume = preferences.volume.coerceIn(0f, 1f).toDouble() * envelope
        target.rate = preferences.speed.toDouble()
        val equalizer = target.audioEqualizer
        equalizer.isEnabled = preferences.equalizer.any { it != 0.0 }
        equalizer.bands.forEachIndexed { index, band -> band.gain = preferences.equalizer.getOrElse(index) { 0.0 }.coerceIn(-12.0, 12.0) }
    }
    override fun stop() = command { generation++; openJob?.cancel(); standbyJob?.cancel(); disposePlayers(); active = null; upcoming = null; mutable.value = AudioState() }
    private fun disposePlayers() { player?.dispose(); standby?.dispose(); outgoing?.dispose(); player = null; standby = null; outgoing = null; standbyKey = null
        listOfNotNull(currentTemp, standbyTemp, outgoingTemp).distinct().forEach { it.delete() }; currentTemp = null; standbyTemp = null; outgoingTemp = null
        currentTiming = null; standbyTiming = null; nextPlan = null; renderingPlan = null; activeBaseFile = null; standbyBaseFile = null }
    override fun close() {
        if (closed.compareAndSet(false, true)) { decoding.cancel(); FxRuntime.dispatch { ticker?.stop(); disposePlayers(); mutable.value = AudioState() } }
    }
}
private fun finiteMs(duration: Duration): Long = if (duration.isUnknown || duration.isIndefinite) 0 else duration.toMillis().toLong().coerceAtLeast(0)
