// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WebDavTest {
    private fun response(href: String, name: String = "Music", folder: Boolean = false, status: Int = 200, size: Long = 100): String =
        """<d:response><d:href>$href</d:href><d:propstat><d:prop><d:displayname>$name</d:displayname><d:resourcetype>${if (folder) "<d:collection/>" else ""}</d:resourcetype><d:getcontentlength>$size</d:getcontentlength><d:getetag>"fixture"</d:getetag></d:prop><d:status>HTTP/1.1 $status OK</d:status></d:propstat></d:response>"""
    private fun listing(vararg rows: String) = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">${rows.joinToString("")}</d:multistatus>"""
    private fun send(exchange: HttpExchange, bytes: ByteArray, code: Int = 200) {
        exchange.sendResponseHeaders(code, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
    }
    private class Server(handler: (HttpExchange) -> Unit) : AutoCloseable {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool()
        val root: URI get() = DavPaths.root("http://127.0.0.1:${server.address.port}/music/")
        init { server.executor = executor; server.createContext("/") { exchange -> try { handler(exchange) } finally { exchange.close() } }; server.start() }
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
    @Test fun pathAndOriginConfinementPrecedeCredentialSending() {
        val root = DavPaths.root("https://example.org/music")
        assertEquals("https://example.org/music/", root.toString())
        assertEquals("https://example.org/music/a%20b.wav", DavPaths.resolve(root, root, "a%20b.wav").toString())
        for (href in listOf("https://evil.org/music/a.wav", "http://example.org/music/a.wav", "https://example.org:444/music/a.wav", "/other/a.wav", "/music-two/a.wav", "../a.wav", "%2e%2e/a.wav", "/music/a%2fb.wav", "/music/a.wav?token=x", "//evil.org/music/a.wav")) {
            assertNull(DavPaths.resolve(root, root, href), href)
        }
        for (url in listOf("http://example.org/music", "https://user:password@example.org/music", "https://example.org/music?key=x", "https://example.org/music#x", "https://example.org/music/../private", "https://example.org/%2e%2e/music")) assertFailsWith<IllegalArgumentException> { DavPaths.root(url) }
        assertEquals(root, DavPaths.parent(root, root.resolve("album/")))
    }
    @Test fun multistatusUsesSuccessfulDavPropertiesAndOneLevelOnly() {
        val root = DavPaths.root("https://example.org/music/")
        val xml = listing(response("/music/", folder = true), response("/music/%E9%9B%A8+song.wav", "雨 + Song"), response("/music/Album/", "Album", true), response("/music/Denied.wav", status = 403), response("https://evil.org/track.wav"), response("/music/Album/deep.wav"), response("/music/video.mp4"))
        val rows = parseDavListing(xml, root, root)
        assertEquals(listOf("Album", "雨 + Song"), rows.map { it.name })
        assertEquals("/music/雨+song.wav", rows.last().uri.path)
        assertEquals(100L, rows.last().bytes); assertEquals("\"fixture\"", rows.last().etag)
        assertTrue(rows.first().folder); assertTrue(rows.last().audio)
    }
    @Test fun externalEntitiesAndMalformedXmlAreRejectedWithoutNetwork() {
        var calls = 0
        Server { calls++; send(it, "secret".toByteArray()) }.use { server ->
            val xml = """<!DOCTYPE d:multistatus [<!ENTITY xxe SYSTEM "${server.root}">]><d:multistatus xmlns:d="DAV:">&xxe;</d:multistatus>"""
            assertFailsWith<IllegalArgumentException> { parseDavListing(xml, server.root, server.root) }
            assertEquals(0, calls)
            assertFailsWith<IllegalArgumentException> { parseDavListing("<bad", server.root, server.root) }
            assertFailsWith<IllegalArgumentException> { parseDavListing("<multistatus/>", server.root, server.root) }
        }
    }
    @Test fun actualPropfindAuthUnicodeGetAndLocalImport() = runBlocking {
        val calls = CopyOnWriteArrayList<String>()
        val fixture = Files.createTempFile("webdav-fixture", ".wav").toFile()
        generateTestWave(fixture, 2)
        val payload = fixture.readBytes(); fixture.delete()
        val auth = "Basic " + Base64.getEncoder().encodeToString("listener:app-password".toByteArray())
        Server { exchange ->
            assertEquals(auth, exchange.requestHeaders.getFirst("Authorization"))
            assertNull(exchange.requestURI.rawQuery)
            calls += exchange.requestMethod
            if (exchange.requestMethod == "PROPFIND") {
                assertEquals("1", exchange.requestHeaders.getFirst("Depth"))
                assertTrue(exchange.requestBody.readBytes().toString(Charsets.UTF_8).contains("propfind"))
                send(exchange, listing(response("/music/%E9%9B%A8.wav", "雨.wav", size = payload.size.toLong())).toByteArray(), 207)
            } else {
                assertEquals("/music/雨.wav", exchange.requestURI.path)
                assertEquals("\"fixture\"", exchange.requestHeaders.getFirst("If-Match"))
                send(exchange, payload)
            }
        }.use { server ->
            DavClient().use { client ->
                val entry = client.list(server.root, server.root, "listener", "app-password").single()
                val directory = Files.createTempDirectory("dav-import").toFile()
                try {
                    val file = client.download(server.root, entry, "listener", "app-password", directory)
                    assertContentEquals(payload, file.readBytes())
                    assertEquals("wav", file.extension)
                    val imported = LocalLibrary(directory).import(listOf(file)).first.single()
                    assertEquals(file.canonicalPath, imported.path)
                    assertEquals(2000L, imported.duration)
                    assertEquals(listOf("PROPFIND", "GET"), calls.toList())
                } finally { directory.deleteRecursively() }
            }
        }
    }
    @Test fun redirectsAndCrossOriginEntriesNeverReceiveCredentials() = runBlocking {
        var redirected = 0
        Server { redirected++; send(it, "stolen".toByteArray()) }.use { other ->
            Server { it.responseHeaders.add("Location", other.root.toString()); send(it, "redirect".toByteArray(), 302) }.use { server ->
                DavClient().use { client ->
                    assertFails { client.list(server.root, server.root, "listener", "password") }
                    val directory = Files.createTempDirectory("dav-origin").toFile()
                    try { assertFailsWith<IllegalArgumentException> { client.download(server.root, DavEntry(other.root.resolve("x.wav"), "x.wav", false), "listener", "password", directory) } }
                    finally { directory.deleteRecursively() }
                }
            }
            assertEquals(0, redirected)
        }
    }
    @Test fun oversizedListingsAndChunkedTracksFailWithNoPartialFile() = runBlocking {
        Server { exchange ->
            if (exchange.requestMethod == "PROPFIND") send(exchange, ByteArray(2_000_001) { 32 }, 207)
            else { exchange.sendResponseHeaders(200, 0); exchange.responseBody.use { it.write(ByteArray(if (exchange.requestURI.path.endsWith("budget.wav")) 256 else 2048) { 1 }) } }
        }.use { server ->
            DavClient(maxFileBytes = 1024).use { client ->
                assertFails { client.list(server.root, server.root, "", "") }
                val directory = Files.createTempDirectory("dav-limit").toFile()
                try {
                    assertFails { client.download(server.root, DavEntry(server.root.resolve("x.wav"), "x.wav", false), "", "", directory) }
                    assertFails { client.download(server.root, DavEntry(server.root.resolve("budget.wav"), "budget.wav", false), "", "", directory, byteBudget = 128) }
                    assertTrue(directory.listFiles().orEmpty().isEmpty())
                } finally { directory.deleteRecursively() }
            }
        }
    }
    @Test fun cancelledTransferPreservesCompletedFileAndCleansTemp() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        var slow = false
        Server { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { output ->
                output.write(byteArrayOf(1, 2, 3)); output.flush()
                if (slow) { entered.countDown(); release.await(8, TimeUnit.SECONDS); output.write(byteArrayOf(4)) }
            }
        }.use { server ->
            DavClient().use { client ->
                val directory = Files.createTempDirectory("dav-cancel").toFile()
                try {
                    val entry = DavEntry(server.root.resolve("track.wav"), "track.wav", false)
                    val completed = client.download(server.root, entry, "", "", directory)
                    slow = true
                    val transfer = launch { client.download(server.root, entry, "", "", directory) }
                    assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                    withTimeout(3000) { transfer.cancelAndJoin() }
                    assertContentEquals(byteArrayOf(1, 2, 3), completed.readBytes())
                    assertEquals(listOf(completed.name), directory.listFiles().orEmpty().map { it.name })
                } finally { release.countDown(); directory.deleteRecursively() }
            }
        }
    }
    @Test fun sessionOptInSecureStorageAndDisconnectLifecycle() = runBlocking {
        var secret: String? = null; var reads = 0; var url = ""; var username = ""
        val store = object : SecretStore {
            override fun read(): String? { reads++; return secret }
            override fun write(value: String) { secret = value }
            override fun delete() { secret = null }
        }
        Server { send(it, listing(response("/music/track.wav", "track.wav")).toByteArray(), 207) }.use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = DavSession(scope, store, savedRoot = { url }) { root, user -> url = root; username = user }
            try {
                assertEquals(0, reads); assertNull(secret); assertNull(session.location.value)
                session.connect(server.root.toString(), "listener", "password")
                withTimeout(5000) { while (url.isEmpty()) delay(10) }
                assertEquals("listener", username); assertTrue(secret.orEmpty().contains("password"))
                withTimeout(5000) { while (session.busy.value) delay(10) }
                session.browse()
                withTimeout(5000) { while (reads == 0 || session.busy.value) delay(10) }
                assertEquals(1, session.entries.value.size)
                session.disconnect()
                withTimeout(5000) { while (url.isNotEmpty() || session.busy.value) delay(10) }
                assertNull(secret); assertNull(session.location.value); assertTrue(session.entries.value.isEmpty())
            } finally { session.close(); scope.cancel() }
        }
    }
    @Test fun disconnectDuringConnectionCannotResurrectCredentials() = runBlocking {
        var secret: String? = null; var url = ""
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val store = object : SecretStore { override fun read() = secret; override fun write(value: String) { secret = value }; override fun delete() { secret = null } }
        Server { entered.countDown(); release.await(8, TimeUnit.SECONDS); send(it, listing().toByteArray(), 207) }.use { server ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val session = DavSession(scope, store, savedRoot = { url }) { value, _ -> url = value }
            try {
                session.connect(server.root.toString(), "listener", "password")
                assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
                session.disconnect()
                withTimeout(3000) { while (session.busy.value) delay(10) }
                release.countDown(); delay(100)
                assertNull(secret); assertEquals("", url); assertNull(session.location.value)
            } finally { release.countDown(); session.close(); scope.cancel() }
        }
    }
}
