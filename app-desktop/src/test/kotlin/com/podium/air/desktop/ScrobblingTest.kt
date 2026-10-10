// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.data.model.Song
import com.podium.air.domain.QueueEntry
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ScrobblingTest {
    private val track = QueueEntry("entry", Song("track", "雨 \"song\" 🎵", "Artist", null, albumName = "Album"))
    private fun audio(playing: Boolean = true, session: Long = 1, duration: Long = 60_000, position: Long = 0) =
        AudioState(entry = track, playing = playing, durationMs = duration, session = session, positionMs = position)
    @Test fun pausedTimeAndSeekDistanceNeverQualifyAListen() {
        val meter = ScrobbleMeter()
        assertTrue(meter.sample(audio(), 0, 1_800_000_000).single().playingNow)
        for (i in 1..15) assertTrue(meter.sample(audio(position = 59_000), i * 1000L, 1_800_000_000L + i).isEmpty())
        assertTrue(meter.sample(audio(false), 15_000, 1_800_000_015).isEmpty())
        for (i in 16..25) assertTrue(meter.sample(audio(false), i * 1000L, 1_800_000_000L + i).isEmpty())
        assertTrue(meter.sample(audio(), 25_000, 1_800_000_025).isEmpty())
        for (i in 26..39) assertTrue(meter.sample(audio(), i * 1000L, 1_800_000_000L + i).isEmpty())
        val listen = meter.sample(audio(), 40_000, 1_800_000_040).single()
        assertFalse(listen.playingNow); assertEquals(1_800_000_000L, listen.startedAtSeconds)
        assertTrue(meter.sample(audio(), 41_000, 1_800_000_041).isEmpty())
    }
    @Test fun repeatingAnEntryCreatesOneNewListenAndShortTracksAreExcluded() {
        val meter = ScrobbleMeter()
        meter.sample(audio(), 0, 1_800_000_000)
        val first = (1..30).flatMap { meter.sample(audio(), it * 1000L, 1_800_000_000L + it) }
        assertEquals(1, first.size); assertFalse(first.single().playingNow)
        assertTrue(meter.sample(audio(session = 2), 31_000, 1_800_000_031).single().playingNow)
        val second = (32..61).flatMap { meter.sample(audio(session = 2), it * 1000L, 1_800_000_000L + it) }
        assertEquals(1, second.size); assertEquals(1_800_000_031L, second.single().startedAtSeconds)
        meter.sample(audio(session = 3, duration = 30_000), 62_000, 1_800_000_062)
        assertTrue((63..102).flatMap { meter.sample(audio(session = 3, duration = 30_000), it * 1000L, 1_800_000_000L + it) }.isEmpty())
    }
    @Test fun fourMinuteCeilingAndSuspensionAreMeasuredFromElapsedTime() {
        val meter = ScrobbleMeter()
        meter.sample(audio(duration = 900_000), 0, 1_800_000_000)
        assertTrue(meter.sample(audio(duration = 900_000), 120_000, 1_800_000_120).isEmpty())
        for (i in 1..237) assertTrue(meter.sample(audio(duration = 900_000), 120_000 + i * 1000L, 1_800_000_120L + i).isEmpty())
        assertFalse(meter.sample(audio(duration = 900_000), 358_000, 1_800_000_358).single().playingNow)
    }
    private fun respond(exchange: HttpExchange, body: String, code: Int = 200) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
    }
    @Test fun documentedHeadersUnicodePayloadAndTimestampRules() = runBlocking {
        val calls = CopyOnWriteArrayList<Pair<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/1/") { exchange ->
            calls += exchange.requestURI.toString() to exchange.requestHeaders.getFirst("Authorization")
            if (exchange.requestURI.path.endsWith("validate-token")) respond(exchange, "{\"valid\":true,\"user_name\":\"Prem\"}")
            else { calls += "body" to exchange.requestBody.readAllBytes().toString(Charsets.UTF_8); respond(exchange, "{\"status\":\"ok\"}") }
        }
        server.start()
        ListenBrainzHttp(URI("http://127.0.0.1:${server.address.port}/1/")).use { api ->
            try {
                assertEquals("Prem", api.validate(TEST_TOKEN))
                api.submit(TEST_TOKEN, ScrobbleEvent(track.song, 1_800_000_000, 60_000, true))
                api.submit(TEST_TOKEN, ScrobbleEvent(track.song, 1_800_000_000, 60_000, false))
                assertTrue(calls.filter { it.first != "body" }.all { it.second == "Token $TEST_TOKEN" && !it.first.contains(TEST_TOKEN) })
                val bodies = calls.filter { it.first == "body" }.map { Json.parseToJsonElement(it.second).jsonObject }
                assertEquals("playing_now", bodies[0]["listen_type"]!!.jsonPrimitive.content)
                val now = bodies[0]["payload"]!!.jsonArray.single().jsonObject
                assertFalse("listened_at" in now); assertEquals(track.song.title, now["track_metadata"]!!.jsonObject["track_name"]!!.jsonPrimitive.content)
                val listen = bodies[1]["payload"]!!.jsonArray.single().jsonObject
                assertEquals(1_800_000_000L, listen["listened_at"]!!.jsonPrimitive.long)
                assertEquals(60_000L, listen["track_metadata"]!!.jsonObject["additional_info"]!!.jsonObject["duration_ms"]!!.jsonPrimitive.long)
            } finally { server.stop(0) }
        }
    }
    @Test fun rateLimitPreventsRepeatedRequestsUntilReset() = runBlocking {
        val requests = AtomicInteger(); var now = 1_800_000_000_000L
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/1/validate-token") { exchange ->
            if (requests.incrementAndGet() == 1) { exchange.responseHeaders.add("X-RateLimit-Reset-In", "10"); respond(exchange, "{}", 429) }
            else respond(exchange, "{\"valid\":true,\"user_name\":\"Prem\"}")
        }
        server.start()
        ListenBrainzHttp(URI("http://127.0.0.1:${server.address.port}/1/"), clock = { now }).use { api ->
            try {
                assertFailsWith<IllegalStateException> { api.validate(TEST_TOKEN) }
                assertFailsWith<IllegalStateException> { api.validate(TEST_TOKEN) }; assertEquals(1, requests.get())
                now += 10_000; assertEquals("Prem", api.validate(TEST_TOKEN)); assertEquals(2, requests.get())
            } finally { server.stop(0) }
        }
    }
    @Test fun responseLimitAndInvalidTokensDoNotConnect() = runBlocking {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/1/validate-token") { exchange -> requests.incrementAndGet(); respond(exchange, "x".repeat(100_000)) }
        server.start()
        ListenBrainzHttp(URI("http://127.0.0.1:${server.address.port}/1/")).use { api ->
            try {
                assertFailsWith<IllegalArgumentException> { api.validate("not-a-token") }; assertEquals(0, requests.get())
                assertFailsWith<IllegalStateException> { api.validate(TEST_TOKEN) }; assertEquals(1, requests.get())
            } finally { server.stop(0) }
        }
    }
    @Test fun optInDisableAndDisconnectControlActualSubmissions() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val submitted = CopyOnWriteArrayList<ScrobbleEvent>()
        var saved: String? = null
        val secret = object : SecretStore { override fun read() = saved; override fun write(value: String) { saved = value }; override fun delete() { saved = null } }
        val api = object : ListenBrainzApi {
            override suspend fun validate(token: String) = "Prem"
            override suspend fun submit(token: String, event: ScrobbleEvent) { submitted += event }
        }
        val settings = CopyOnWriteArrayList<Pair<Boolean, String>>()
        val integration = withContext(dispatcher) { ScrobblingIntegration(scope, api, secret, Preferences()) { enabled, user -> settings += enabled to user } }
        try {
            withContext(dispatcher) { integration.sample(audio(), 0, 1_800_000_000) }; assertTrue(submitted.isEmpty()); assertNull(saved)
            withContext(dispatcher) { integration.connect(TEST_TOKEN) }
            withTimeout(5000) { while (integration.busy.value) delay(10) }
            assertEquals(TEST_TOKEN, saved); assertEquals(true to "Prem", settings.last())
            withContext(dispatcher) { integration.sample(audio(), 1000, 1_800_000_001) }
            withTimeout(5000) { while (submitted.isEmpty()) delay(10) }
            withContext(dispatcher) { integration.configure(false); integration.sample(audio(session = 2), 2000, 1_800_000_002) }
            assertEquals(1, submitted.size)
            withContext(dispatcher) { integration.disconnect() }
            withTimeout(5000) { while (integration.busy.value) delay(10) }
            assertNull(saved); assertEquals(false to "", settings.last())
        } finally { withContext(dispatcher) { integration.close() }; scope.cancel(); dispatcher.close() }
    }
    @Test fun nativeWindowsCredentialRoundTripOverwriteAndDeletion() {
        assumeTrue(System.getProperty("os.name").contains("Windows"))
        val key = "test-${UUID.randomUUID()}"
        val first = WindowsSecretStore(key)
        try {
            assertNull(first.read()); first.write("not-a-real-token-日本🎵")
            assertEquals("not-a-real-token-日本🎵", WindowsSecretStore(key).read())
            first.write("replacement"); assertEquals("replacement", first.read())
            first.delete(); assertNull(first.read()); first.delete()
        } finally { first.delete() }
    }
    companion object { private const val TEST_TOKEN = "00000000-0000-0000-0000-000000000000" }
}
