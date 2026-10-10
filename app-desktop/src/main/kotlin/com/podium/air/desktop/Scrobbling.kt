// SPDX-License-Identifier: GPL-3.0-only
// Desktop adaptation of Podium Air's ListenBrainzManager and audible-time scrobbling.
package com.podium.air.desktop

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

internal data class ScrobbleEvent(val song: Song, val startedAtSeconds: Long, val durationMs: Long, val playingNow: Boolean)

/** A seek never adds listening time; pauses and long process suspensions are excluded. */
internal class ScrobbleMeter {
    private var key: String? = null
    private var song: Song? = null
    private var startedAt = 0L
    private var duration = 0L
    private var elapsed = 0L
    private var previousAt: Long? = null
    private var previousPlaying = false
    private var nowSent = false
    private var sent = false
    fun sample(audio: AudioState, nowMs: Long, epochSeconds: Long): List<ScrobbleEvent> {
        val events = mutableListOf<ScrobbleEvent>()
        if (previousPlaying) elapsed += previousAt?.let { (nowMs - it).coerceIn(0, 2000) } ?: 0
        fun complete() {
            val selected = song ?: return
            if (!sent && duration > 30_000 && elapsed >= min(duration / 2, 240_000L)) {
                events += ScrobbleEvent(selected, startedAt, duration, false); sent = true
            }
        }
        complete()
        val next = audio.entry?.let { "${it.key}:${audio.session}" }
        if (key != next) {
            key = next; song = audio.entry?.song; elapsed = 0; startedAt = 0; duration = audio.durationMs; nowSent = false; sent = false
        } else if (audio.durationMs > 0) duration = audio.durationMs
        if (audio.playing && song != null) {
            if (startedAt == 0L) startedAt = epochSeconds
            if (!nowSent) { events += ScrobbleEvent(song!!, startedAt, duration, true); nowSent = true }
            complete()
        }
        previousAt = nowMs; previousPlaying = audio.playing
        return events
    }
}

internal interface ListenBrainzApi : AutoCloseable {
    suspend fun validate(token: String): String
    suspend fun submit(token: String, event: ScrobbleEvent)
    override fun close() {}
}

/** Uses the documented API, Authorization headers, bounded bodies and no redirects. */
internal class ListenBrainzHttp(
    private val base: URI = URI("https://api.listenbrainz.org/1/"),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ListenBrainzApi {
    private val blockedUntil = AtomicLong(0)
    private val requestMutex = Mutex()
    private var lastRequestAt: Long? = null
    init {
        require(base.scheme == "https" || (base.scheme == "http" && base.host in listOf("127.0.0.1", "localhost", "::1")))
        require(base.userInfo == null && base.query == null && base.fragment == null && base.path.endsWith('/'))
    }
    override suspend fun validate(token: String): String {
        val row = Json.parseToJsonElement(perform("validate-token", token, null)).jsonObject
        check((row["valid"] as? JsonPrimitive)?.booleanOrNull == true) { "ListenBrainz token is invalid." }
        return (row["user_name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it.length <= 200 }
            ?: error("ListenBrainz did not return an account name.")
    }
    override suspend fun submit(token: String, event: ScrobbleEvent) {
        val song = event.song
        require(song.title.isNotBlank() && song.artist.isNotBlank())
        fun clean(value: String) = value.replace("\u0000", "").take(500)
        val listen = buildJsonObject {
            if (!event.playingNow) put("listened_at", event.startedAtSeconds)
            put("track_metadata", buildJsonObject {
                put("track_name", clean(song.title)); put("artist_name", clean(song.artist))
                song.albumName?.takeIf { it.isNotBlank() }?.let { put("release_name", clean(it)) }
                put("additional_info", buildJsonObject {
                    put("submission_client", "Podium Air — Windows Edition")
                    if (event.durationMs > 0) put("duration_ms", event.durationMs)
                })
            })
        }
        val body = buildJsonObject { put("listen_type", if (event.playingNow) "playing_now" else "single"); put("payload", JsonArray(listOf(listen))) }
        perform("submit-listens", token, body.toString())
    }
    private suspend fun perform(path: String, token: String, body: String?): String = requestMutex.withLock {
        // ListenBrainz requires every client to stay at or below one request per second.
        val remaining = lastRequestAt?.let { 1_000_000_000L - (System.nanoTime() - it) } ?: 0
        if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
        runInterruptible(Dispatchers.IO) { lastRequestAt = System.nanoTime(); request(path, token, body) }
    }
    private fun request(path: String, token: String, body: String?): String {
        require(token.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))) { "Enter your ListenBrainz user token." }
        check(clock() >= blockedUntil.get()) { "ListenBrainz rate limit: try again later." }
        val builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(12))
            .header("Authorization", "Token $token").header("Accept", "application/json")
            .header("User-Agent", "PodiumAirWindows/0.2 (https://github.com/kaizen-flims/Podium-Air-Windows-)")
        if (body == null) builder.GET() else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        val future = client.sendAsync(builder.build(), HttpResponse.BodyHandler { limitedHttpBody(65536, "ListenBrainz response is too large.") })
        try {
            val response = future.get(15, TimeUnit.SECONDS)
            if (response.statusCode() == 429 || response.headers().firstValue("X-RateLimit-Remaining").orElse("1") == "0") {
                val seconds = response.headers().firstValue("X-RateLimit-Reset-In").orElse("60").toLongOrNull()?.coerceIn(1, 3600) ?: 60
                blockedUntil.set(clock() + seconds * 1000)
            }
            if (response.statusCode() == 429) error("ListenBrainz rate limit: try again later.")
            check(response.statusCode() in 200..299) { "ListenBrainz returned HTTP ${response.statusCode()}." }
            return response.body().toString(Charsets.UTF_8)
        } catch (error: ExecutionException) { throw error.cause ?: error }
        finally { if (!future.isDone) future.cancel(true) }
    }
    override fun close() { client.shutdownNow() }
}

/** All control/state mutation stays on the model dispatcher; requests run on IO. */
internal class ScrobblingIntegration(
    private val scope: CoroutineScope,
    private val api: ListenBrainzApi,
    private val secrets: SecretStore,
    initial: Preferences,
    private val saveAccount: (Boolean, String) -> Unit,
) : AutoCloseable {
    val status = MutableStateFlow(if (initial.listenBrainzEnabled) "Restoring ListenBrainz…" else "ListenBrainz sharing is off.")
    val busy = MutableStateFlow(false)
    private var token: String? = null
    private var enabled = false
    private var meter = ScrobbleMeter()
    private var worker: Job? = null
    private var operation: Job? = null
    private val credentialMutex = Mutex()
    private var outbox = Channel<ScrobbleEvent>(64)
    init { configure(initial.listenBrainzEnabled) }
    fun configure(value: Boolean) {
        if (enabled == value) return
        enabled = value; worker?.cancel(); outbox.close(); outbox = Channel(64); meter = ScrobbleMeter()
        if (!value) { operation?.cancel(); busy.value = false; status.value = "ListenBrainz sharing is off."; return }
        if (token != null) startWorker() else {
            operation = scope.launch {
                try {
                    token = credentialMutex.withLock { withContext(Dispatchers.IO) { secrets.read() } }
                    ensureActive()
                    if (token == null) { status.value = "Connect your ListenBrainz account to enable sharing."; enabled = false; saveAccount(false, "") }
                    else startWorker()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { enabled = false; saveAccount(false, ""); status.value = "Could not read Windows credential storage. Reconnect ListenBrainz." }
            }
        }
    }
    fun connect(value: String) {
        if (busy.value) return
        operation?.cancel(); busy.value = true; status.value = "Checking your ListenBrainz token…"
        operation = scope.launch {
            try {
                val selected = value.trim()
                val username = api.validate(selected)
                ensureActive()
                credentialMutex.withLock { withContext(Dispatchers.IO) { secrets.write(selected) } }
                ensureActive()
                token = selected
                // Reset even when replacing an already connected account.
                worker?.cancel(); outbox.close(); outbox = Channel(64); meter = ScrobbleMeter(); enabled = true
                saveAccount(true, username); startWorker()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { status.value = error.message ?: "Could not connect ListenBrainz." }
            finally { busy.value = false }
        }
    }
    fun disconnect() {
        configure(false); token = null; saveAccount(false, ""); busy.value = true
        operation = scope.launch {
            try { credentialMutex.withLock { withContext(Dispatchers.IO) { secrets.delete() } }; status.value = "Disconnected from ListenBrainz." }
            catch (_: Exception) { status.value = "Sharing is off, but Windows could not delete the saved credential." }
            finally { busy.value = false }
        }
    }
    private fun startWorker() {
        val credential = token ?: return
        val channel = outbox
        status.value = "ListenBrainz connected. Sharing is enabled."
        worker = scope.launch {
            for (event in channel) {
                try { api.submit(credential, event); status.value = if (event.playingNow) "Playing Now sent to ListenBrainz." else "Listen saved to ListenBrainz." }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { status.value = (error.message ?: "ListenBrainz submission failed.") + " This listen was not saved; there is no offline retry queue." }
            }
        }
    }
    fun sample(audio: AudioState, monotonicMs: Long, epochSeconds: Long) {
        if (!enabled || token == null || worker?.isActive != true) return
        meter.sample(audio, monotonicMs, epochSeconds).forEach { event ->
            if (event.song.title.isBlank() || event.song.artist.isBlank() || event.song.artist == "Unknown artist") return@forEach
            if (!outbox.trySend(event).isSuccess) status.value = "ListenBrainz queue is full. This listen was not submitted."
        }
    }
    override fun close() { operation?.cancel(); worker?.cancel(); outbox.close(); token = null; api.close() }
}
