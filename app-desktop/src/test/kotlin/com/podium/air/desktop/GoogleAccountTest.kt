// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GoogleAccountTest {
    private class MemorySecret(var value: String? = null) : SecretStore {
        override fun read() = value
        override fun write(value: String) { this.value = value }
        override fun delete() { value = null }
    }
    private val desktop = GoogleClient("fixture-client.apps.googleusercontent.com", "fixture-secret")
    private fun reply(e: HttpExchange, body: String, status: Int = 200) {
        val bytes = body.toByteArray(); e.responseHeaders.add("Content-Type", "application/json")
        e.sendResponseHeaders(status, bytes.size.toLong()); e.responseBody.use { it.write(bytes) }
    }
    private fun send(uri: URI): Int = HttpClient.newHttpClient().use { http -> http.send(HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() }
    private fun callback(uri: URI, code: String = "fixture-code") {
        val query = googleParams(uri.rawQuery)
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("offline", query["access_type"])
        assertFalse("code_verifier" in query || "client_secret" in query)
        assertEquals(200, send(URI(query.getValue("redirect_uri") + "?" + googleQuery(mapOf("state" to query.getValue("state"), "code" to code)))))
    }
    private fun server(block: (HttpExchange) -> Unit): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }; createContext("/", block); start()
    }
    @Test fun loopbackRejectsWrongStateDuplicateParametersAndDeclineIsNotSuccess() = runBlocking {
        GoogleCallback(5000).use { listener ->
            assertEquals(listener.challenge, Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(listener.verifier.toByteArray())))
            val pending = async { listener.awaitCode() }
            withContext(Dispatchers.IO) {
                assertEquals(403, send(URI(listener.redirect + "?state=wrong&code=wrong")))
                assertEquals(400, send(URI(listener.redirect + "?state=${listener.state}&state=${listener.state}&code=wrong")))
                assertEquals(200, send(URI(listener.redirect + "?state=${listener.state}&code=accepted")))
            }
            assertEquals("accepted", pending.await())
        }
        GoogleCallback(5000).use { listener ->
            val pending = async { runCatching { listener.awaitCode() } }
            withContext(Dispatchers.IO) { assertEquals(200, send(URI(listener.redirect + "?state=${listener.state}&error=access_denied"))) }
            assertTrue(pending.await().exceptionOrNull()?.message?.contains("declined") == true)
        }
    }
    @Test fun desktopConfigRejectsWebClientAndArbitraryEndpointWithoutEchoingSecret() {
        assertEquals(desktop.id, GoogleClient.parse("""{"installed":{"client_id":"${desktop.id}","client_secret":"fixture-secret"}}""").id)
        assertFails { GoogleClient.parse("""{"web":{"client_id":"${desktop.id}"}}""") }
        assertFails { GoogleClient.parse("""{"installed":{"client_id":"${desktop.id}","token_uri":"https://attacker.invalid/token"}}""") }
        val error = assertFails { GoogleClient.parse("invalid secret-fixture-value") }
        assertFalse(error.message.orEmpty().contains("secret-fixture-value"))
        assertFails { GoogleEndpoints(token = URI("https://attacker.invalid/token")) }
    }
    @Test fun tokenExchangePaginationOrderingAndHeaderOnlyAuthorization() = runBlocking {
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        val forms = CopyOnWriteArrayList<Map<String, String>>()
        val server = server { e ->
            requests += e.requestURI.toString() to e.requestHeaders.getFirst("Authorization").orEmpty()
            val query = googleParams(e.requestURI.rawQuery.orEmpty())
            when (e.requestURI.path) {
                "/token" -> { forms += googleParams(e.requestBody.readAllBytes().toString(Charsets.UTF_8)); reply(e, """{"access_token":"fixture-access","refresh_token":"fixture-refresh","expires_in":3600,"token_type":"Bearer","scope":"openid $YOUTUBE_READ_SCOPE"}""") }
                "/userinfo" -> reply(e, """{"sub":"subject","name":"Prem বাংলা","email":"prem@example.invalid","email_verified":false}""")
                "/youtube/v3/playlists" -> reply(e, if (query["pageToken"] == "next") """{"items":[{"id":"PLsecond","snippet":{"title":"Second"}}]}""" else """{"items":[{"id":"PLfirst","snippet":{"title":"First"},"contentDetails":{"itemCount":3}}],"nextPageToken":"next"}""")
                "/youtube/v3/playlistItems" -> reply(e, """{"items":[{"snippet":{"position":2,"title":"C","resourceId":{"videoId":"fixture0002"}}},{"snippet":{"position":0,"title":"A","resourceId":{"videoId":"fixture0000"}}},{"snippet":{"position":1,"title":"B","resourceId":{"videoId":"fixture0001"}}}]}""")
                else -> reply(e, "{}", 404)
            }
        }
        GoogleHttp(GoogleEndpoints.fixture(URI("http://127.0.0.1:${server.address.port}/"))).use { api ->
            try {
                GoogleCallback().use { listener ->
                    val token = api.exchange(desktop, "fixture-code", listener)
                    assertEquals(listener.verifier, forms.single()["code_verifier"])
                    assertEquals(listener.redirect, forms.single()["redirect_uri"])
                    assertTrue(api.fresh(token)); assertTrue(YOUTUBE_READ_SCOPE in token.scopes)
                    assertEquals("", api.identity(token.access).email)
                    assertEquals(listOf("PLfirst", "PLsecond"), api.playlists(token.access).first.map { it.id })
                    assertEquals(listOf(0, 1, 2), api.items(token.access, "PLfirst").first.map { it.position })
                    assertTrue(requests.filter { !it.first.startsWith("/token") }.all { it.second == "Bearer fixture-access" && !it.first.contains("fixture-access") })
                    assertTrue(requests.filter { it.first.startsWith("/youtube/v3/playlists") }.all { googleParams(URI(it.first).rawQuery)["mine"] == "true" })
                    api.refresh(desktop, token)
                    assertEquals("refresh_token", forms.last()["grant_type"])
                }
            } finally { server.stop(0) }
        }
    }
    @Test fun partialConsentShowsIdentityWithoutRequestingPlaylistsAndDisconnectDeletesGrant() = runBlocking {
        val paths = CopyOnWriteArrayList<String>()
        val server = server { e ->
            paths += e.requestURI.path
            when (e.requestURI.path) {
                "/token" -> reply(e, """{"access_token":"fixture-access","refresh_token":"fixture-refresh","expires_in":3600,"token_type":"Bearer","scope":"openid email"}""")
                "/userinfo" -> reply(e, """{"sub":"fixture-sub","name":"Prem"}""")
                "/revoke" -> reply(e, "{}")
                else -> reply(e, "{}", 404)
            }
        }
        val client = MemorySecret(desktop.encode()); val refresh = MemorySecret(); val permissions = MemorySecret()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val connection = GoogleConnection(scope, GoogleHttp(GoogleEndpoints.fixture(URI("http://127.0.0.1:${server.address.port}/"))), client, refresh, permissions) { uri -> Thread { callback(uri) }.apply { isDaemon = true; start() } }
        try {
            withTimeout(3000) { connection.state.first { !it.busy } }
            connection.signIn(); val result = withTimeout(7000) { connection.state.first { !it.busy } }
            assertEquals("Prem", result.identity?.name); assertFalse(result.playlistAccess)
            assertTrue(result.status.contains("not granted")); assertFalse(paths.any { it.contains("youtube") })
            assertEquals("fixture-refresh", refresh.value)
            connection.disconnect(); withTimeout(5000) { connection.state.first { !it.busy } }
            assertNull(refresh.value); assertNull(permissions.value); assertNull(connection.state.value.identity)
            assertTrue("/revoke" in paths); assertNotNull(client.value)
            connection.forgetClient(); withTimeout(3000) { connection.state.first { !it.busy } }; assertNull(client.value)
        } finally { connection.close(); scope.cancel(); server.stop(0) }
    }
    @Test fun disconnectDuringRefreshNeverResurrectsCredentialsOrPlaylistState() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val server = server { e ->
            if (e.requestURI.path == "/token") {
                entered.countDown(); release.await(5, TimeUnit.SECONDS)
                runCatching { reply(e, """{"access_token":"late-access","refresh_token":"late-refresh","expires_in":3600,"token_type":"Bearer","scope":"openid $YOUTUBE_READ_SCOPE"}""") }
            } else reply(e, "{}")
        }
        val client = MemorySecret(desktop.encode()); val refresh = MemorySecret("saved-refresh"); val permissions = MemorySecret(YOUTUBE_READ_SCOPE)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val connection = GoogleConnection(scope, GoogleHttp(GoogleEndpoints.fixture(URI("http://127.0.0.1:${server.address.port}/"))), client, refresh, permissions)
        try {
            withTimeout(3000) { connection.state.first { !it.busy } }
            connection.refresh(); assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            connection.disconnect(); release.countDown()
            withTimeout(5000) { connection.state.first { !it.busy } }; delay(200)
            assertNull(refresh.value); assertNull(permissions.value); assertFalse(connection.state.value.saved)
            assertNull(connection.state.value.identity); assertTrue(connection.state.value.playlists.isEmpty())
        } finally { release.countDown(); connection.close(); scope.cancel(); server.stop(0) }
    }
    @Test fun redirectsMalformedTokenAndOversizedResponsesAreNotAccepted() = runBlocking {
        var mode = 0
        val server = server { e -> when (mode) {
            0 -> { e.responseHeaders.add("Location", "https://attacker.invalid/"); reply(e, "{}", 302) }
            1 -> reply(e, """{"access_token":"secret token\nvalue","expires_in":3600,"token_type":"Bearer"}""")
            else -> reply(e, " ".repeat(1000001))
        } }
        GoogleHttp(GoogleEndpoints.fixture(URI("http://127.0.0.1:${server.address.port}/"))).use { api ->
            try {
                GoogleCallback().use { listener ->
                    assertEquals(302, assertFailsWith<GoogleApiFailure> { api.exchange(desktop, "code", listener) }.status)
                    mode = 1; assertFails { api.exchange(desktop, "code", listener) }
                    mode = 2; assertFails { api.exchange(desktop, "code", listener) }
                }
            } finally { server.stop(0) }
        }
    }
}
