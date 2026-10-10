// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class OnlineLyricsTest {
    private val query = LyricsQuery("A & B", "Test Artist", "Test Album", 180_000)
    private fun record(duration: Double = 180.0, title: String = query.title, synced: String = "[00:01.00]One\n[00:03.00]Two") = buildJsonObject {
        put("trackName", title); put("artistName", query.artist); put("albumName", query.album); put("duration", duration)
        put("instrumental", false); put("syncedLyrics", synced)
    }
    @Test fun exactLookupEncodesMetadataCachesAndWorksWithoutNetwork() = runBlocking {
        val requests = AtomicInteger(); val directory = Files.createTempDirectory("lrclib-test-").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/get") { exchange ->
            requests.incrementAndGet()
            assertTrue(exchange.requestURI.rawQuery.contains("track_name=A+%26+B"))
            assertTrue(exchange.requestURI.rawQuery.contains("duration=180.0"))
            assertTrue(exchange.requestHeaders.getFirst("User-Agent").contains("Podium Air Windows"))
            val body = record().toString().toByteArray(); exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val provider = LrcLibLyrics(directory, URI("http://127.0.0.1:${server.address.port}/api/"))
            val first = provider.lookup(query, true); assertEquals(listOf("One", "Two"), first.lines.map { it.text }); assertEquals(3000L, first.lines.last().timeMs)
            server.stop(0)
            val second = provider.lookup(query, false); assertEquals(first.lines, second.lines); assertTrue(second.status.contains("cached")); assertEquals(1, requests.get())
        } finally { server.stop(0); directory.deleteRecursively() }
    }
    @Test fun searchRejectsWrongRecordingAndCachesMisses() = runBlocking {
        val directory = Files.createTempDirectory("lrclib-miss-").toFile(); val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/get") { exchange -> requests.incrementAndGet(); exchange.sendResponseHeaders(404, -1); exchange.close() }
        server.createContext("/api/search") { exchange ->
            requests.incrementAndGet(); val body = JsonArray(listOf(record(210.0), record(title = "Different song"))).toString().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val provider = LrcLibLyrics(directory, URI("http://127.0.0.1:${server.address.port}/api/"))
            assertTrue(provider.lookup(query, true).lines.isEmpty()); assertTrue(provider.lookup(query, true).lines.isEmpty()); assertEquals(2, requests.get())
            assertEquals(0, directory.listFiles().orEmpty().size)
        } finally { server.stop(0); directory.deleteRecursively() }
    }
    @Test fun missingExactFallsBackToMatchingSyncedSearch() = runBlocking {
        val directory = Files.createTempDirectory("lrclib-search-").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/get") { exchange -> exchange.sendResponseHeaders(404, -1); exchange.close() }
        server.createContext("/api/search") { exchange ->
            val body = JsonArray(listOf(record(220.0), record(180.0), record(183.0))).toString().toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val result = LrcLibLyrics(directory, URI("http://127.0.0.1:${server.address.port}/api/")).lookup(query, true)
            assertEquals("One", result.lines.first().text); assertTrue(result.status.contains("synced"))
            val stored = directory.listFiles()!!.single().readText(); assertEquals(180.0, Json.parseToJsonElement(stored).jsonObject["duration"]!!.jsonPrimitive.double)
        } finally { server.stop(0); directory.deleteRecursively() }
    }
    @Test fun rejectsOversizeHttpBodyWithoutCachingIt() = runBlocking {
        val directory = Files.createTempDirectory("lrclib-large-").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/get") { exchange ->
            val bytes = ByteArray(2_100_000) { 32 }; exchange.sendResponseHeaders(200, bytes.size.toLong())
            runCatching { exchange.responseBody.use { it.write(bytes) } }; exchange.close()
        }
        server.start()
        try {
            val provider = LrcLibLyrics(directory, URI("http://127.0.0.1:${server.address.port}/api/"))
            val error = assertFailsWith<IllegalStateException> { provider.lookup(query, true) }; assertTrue(error.message!!.contains("2 MB"))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { server.stop(0); directory.deleteRecursively() }
    }
    @Test fun cancellationInterruptsBlockedNetworkAndDoesNotCreateCache() = runBlocking {
        val directory = Files.createTempDirectory("lrclib-cancel-").toFile(); val started = CountDownLatch(1); val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/get") { exchange -> started.countDown(); release.await(5, TimeUnit.SECONDS); exchange.close() }; server.start()
        try {
            val provider = LrcLibLyrics(directory, URI("http://127.0.0.1:${server.address.port}/api/"))
            val job = async { provider.lookup(query, true) }
            assertTrue(withContext(Dispatchers.IO) { started.await(3, TimeUnit.SECONDS) })
            withTimeout(2000) { job.cancelAndJoin() }; assertTrue(job.isCancelled); assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { release.countDown(); server.stop(0); directory.deleteRecursively() }
    }
}
