// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.data.lyrics.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Duration
import java.util.Locale
import java.util.concurrent.*
import java.util.concurrent.Flow
import kotlin.math.abs

data class LyricsQuery(val title: String, val artist: String, val album: String, val durationMs: Long)
data class LyricsResult(val lines: List<LyricLine>, val status: String)
interface LyricsProvider : AutoCloseable {
    suspend fun lookup(query: LyricsQuery, online: Boolean): LyricsResult
    override fun close() {}
}

/** The source app's keyless provider, through its documented public API. Never uploads audio. */
internal class LrcLibLyrics(
    private val directory: File = File(defaultDataDirectory(), "lyrics/lrclib"),
    private val base: URI = URI("https://lrclib.net/api/"),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) : LyricsProvider {
    private val misses = ConcurrentHashMap<String, Long>()
    override fun close() { client.shutdownNow() }
    init {
        require(base.scheme == "https" || (base.scheme == "http" && base.host in listOf("127.0.0.1", "localhost", "::1")))
        require(base.userInfo == null && base.query == null && base.fragment == null && base.path.endsWith('/'))
    }
    override suspend fun lookup(query: LyricsQuery, online: Boolean): LyricsResult = runInterruptible(Dispatchers.IO) {
        require(query.title.length <= 500 && query.artist.length <= 500 && query.album.length <= 500)
        val identity = listOf(base.toString(), normalize(query.title), normalize(query.artist), normalize(query.album), query.durationMs.toString()).joinToString("\u001f")
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val cached = File(directory, "$key.json")
        if (cached.isFile && cached.length() <= MAX_BODY) {
            runCatching { Json.parseToJsonElement(cached.readText()).jsonObject }.getOrNull()?.let { match(query, it) }?.let {
                return@runInterruptible result(it, "LRCLIB • cached")
            }
        }
        if (!online) return@runInterruptible LyricsResult(emptyList(), "Online lyrics are off. Use sidecar or embedded lyrics, or enable LRCLIB in Settings.")
        if (query.title.isBlank() || query.artist.isBlank() || normalize(query.artist) == "unknown artist" || query.durationMs !in 1000..3_600_000) {
            return@runInterruptible LyricsResult(emptyList(), "LRCLIB needs a title, artist and known duration to identify the recording.")
        }
        if (clock() - (misses[key] ?: Long.MIN_VALUE / 2) < 30 * 60_000) return@runInterruptible LyricsResult(emptyList(), "No matching lyrics on LRCLIB. Try again later or add a sidecar file.")
        val params = linkedMapOf("track_name" to query.title, "artist_name" to query.artist, "duration" to (query.durationMs / 1000.0).toString())
        if (query.album.isNotBlank()) params["album_name"] = query.album
        val exact = get("get", params)?.let { Json.parseToJsonElement(it) as? JsonObject }?.let { match(query, it) }
        val chosen = exact ?: get("search", mapOf("track_name" to query.title, "artist_name" to query.artist))?.let {
            (Json.parseToJsonElement(it) as? JsonArray).orEmpty().mapNotNull { item -> (item as? JsonObject)?.let { row -> match(query, row) } }
                .minByOrNull { abs((it["duration"] as JsonPrimitive).double - query.durationMs / 1000.0) }
        }
        if (chosen == null) {
            misses[key] = clock()
            LyricsResult(emptyList(), "No matching lyrics on LRCLIB. Different edits are excluded to prevent timing drift.")
        } else {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            directory.mkdirs()
            val temporary = Files.createTempFile(directory.toPath(), "lyrics-", ".json")
            try {
                Files.writeString(temporary, chosen.toString())
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                Files.move(temporary, cached.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
            // A bounded cache; pruning does not affect the current in-memory result.
            directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.drop(500)?.forEach { it.delete() }
            result(chosen, "LRCLIB")
        }
    }
    private fun result(row: JsonObject, source: String): LyricsResult {
        if ((row["instrumental"] as? JsonPrimitive)?.booleanOrNull == true) return LyricsResult(emptyList(), "$source • instrumental recording")
        val sync = (row["syncedLyrics"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val plain = (row["plainLyrics"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return LyricsResult(parseLyricText(sync.ifBlank { plain }), "$source • ${if (sync.isNotBlank()) "synced lyrics" else "unsynced lyrics"}")
    }
    private fun match(query: LyricsQuery, row: JsonObject): JsonObject? {
        fun value(name: String) = (row[name] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val duration = (row["duration"] as? JsonPrimitive)?.doubleOrNull ?: return null
        if (!duration.isFinite() || abs(duration - query.durationMs / 1000.0) > 5) return null
        if (normalize(value("trackName")) != normalize(query.title) || normalize(value("artistName")) != normalize(query.artist)) return null
        val sync = value("syncedLyrics"); val plain = value("plainLyrics")
        if (sync.length + plain.length > MAX_BODY || (sync.isBlank() && plain.isBlank() && (row["instrumental"] as? JsonPrimitive)?.booleanOrNull != true)) return null
        val lines = parseLyricText(sync.ifBlank { plain })
        if (lines.any { it.timeMs > query.durationMs + 5000 }) return null
        return row
    }
    private fun normalize(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    private fun get(path: String, params: Map<String, String>): String? {
        val query = params.entries.joinToString("&") { URLEncoder.encode(it.key, Charsets.UTF_8) + "=" + URLEncoder.encode(it.value, Charsets.UTF_8) }
        val request = HttpRequest.newBuilder(base.resolve("$path?$query")).timeout(Duration.ofSeconds(12))
            .header("User-Agent", "Podium Air Windows (https://github.com/kaizen-flims/Podium-Air-Windows-)").header("Accept", "application/json").GET().build()
        val future = client.sendAsync(request, HttpResponse.BodyHandler { limitedBody() })
        try {
            val response = future.get(15, TimeUnit.SECONDS)
            if (response.statusCode() == 404) return null
            check(response.statusCode() in 200..299) { "LRCLIB returned HTTP ${response.statusCode()}." }
            return response.body().toString(Charsets.UTF_8)
        } catch (error: ExecutionException) { throw error.cause ?: error }
        finally { if (!future.isDone) future.cancel(true) }
    }
    private fun limitedBody(): HttpResponse.BodySubscriber<ByteArray> {
        val delegate = HttpResponse.BodySubscribers.ofByteArray()
        return object : HttpResponse.BodySubscriber<ByteArray> {
            var subscription: Flow.Subscription? = null; var received = 0L
            override fun getBody(): CompletionStage<ByteArray> = delegate.body
            override fun onSubscribe(value: Flow.Subscription) { subscription = value; delegate.onSubscribe(value) }
            override fun onNext(items: List<ByteBuffer>) {
                received += items.sumOf { it.remaining().toLong() }
                if (received > MAX_BODY) { subscription?.cancel(); delegate.onError(IllegalStateException("LRCLIB response exceeds 2 MB.")) }
                else delegate.onNext(items)
            }
            override fun onError(error: Throwable) = delegate.onError(error)
            override fun onComplete() = delegate.onComplete()
        }
    }
    companion object { private const val MAX_BODY = 2_000_000 }
}
