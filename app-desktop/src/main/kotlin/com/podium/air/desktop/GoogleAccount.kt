// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.awt.Desktop
import java.io.File
import java.net.*
import java.net.http.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal const val YOUTUBE_READ_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun googleQuery(values: Map<String, String>) = values.entries.joinToString("&") {
    URLEncoder.encode(it.key, Charsets.UTF_8) + "=" + URLEncoder.encode(it.value, Charsets.UTF_8)
}
internal fun googleParams(query: String): Map<String, String> {
    require(query.length <= 12000)
    val result = linkedMapOf<String, String>()
    for (pair in query.split('&').filter(String::isNotEmpty)) {
        val key = URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8)
        val value = URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
        require(key.length <= 128 && value.length <= 8192 && result.put(key, value) == null) { "Duplicate or invalid callback parameter." }
    }
    return result
}

/** Only Google's downloaded Desktop client format is accepted; endpoints are never trusted from a file. */
internal class GoogleClient(val id: String, val secret: String = "") {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{5,220}\\.apps\\.googleusercontent\\.com"))) { "Choose a Google Desktop OAuth client JSON file." }
        require(secret.length <= 512 && secret.none { it.isISOControl() })
    }
    fun encode() = buildJsonObject { put("client_id", id); put("client_secret", secret) }.toString()
    override fun toString() = "GoogleClient(redacted)"
    companion object {
        fun parse(raw: String, downloaded: Boolean = true): GoogleClient {
            require(raw.toByteArray().size <= 32000) { "OAuth client file exceeds 32 KB." }
            val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrElse { error("The OAuth configuration is not a valid JSON object.") }
            val row = if (downloaded) root["installed"] as? JsonObject ?: error("A Desktop OAuth client is required; web/Android clients are unsupported.") else root
            if (downloaded) {
                require(row.text("auth_uri") in listOf("", "https://accounts.google.com/o/oauth2/auth", "https://accounts.google.com/o/oauth2/v2/auth"))
                require(row.text("token_uri") in listOf("", "https://oauth2.googleapis.com/token"))
            }
            return GoogleClient(row.text("client_id"), row.text("client_secret"))
        }
    }
}

internal data class GoogleEndpoints(
    val authorize: URI = URI("https://accounts.google.com/o/oauth2/v2/auth"),
    val token: URI = URI("https://oauth2.googleapis.com/token"),
    val user: URI = URI("https://openidconnect.googleapis.com/v1/userinfo"),
    val youtube: URI = URI("https://www.googleapis.com/youtube/v3/"),
    val revoke: URI = URI("https://oauth2.googleapis.com/revoke"),
) {
    init {
        val actual = listOf(authorize, token, user, youtube, revoke)
        val official = listOf("https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token", "https://openidconnect.googleapis.com/v1/userinfo", "https://www.googleapis.com/youtube/v3/", "https://oauth2.googleapis.com/revoke")
        require(actual.map(URI::toString) == official || actual.all { it.scheme == "http" && it.host == "127.0.0.1" && it.port > 0 && it.userInfo == null && it.query == null && it.fragment == null } && actual.map { it.port }.distinct().size == 1)
    }
    companion object {
        fun fixture(base: URI): GoogleEndpoints {
            require(base.scheme == "http" && base.host == "127.0.0.1" && base.port > 0 && base.path == "/" && base.userInfo == null && base.query == null && base.fragment == null)
            return GoogleEndpoints(base.resolve("authorize"), base.resolve("token"), base.resolve("userinfo"), base.resolve("youtube/v3/"), base.resolve("revoke"))
        }
    }
}

/** A short-lived loopback listener; browser authorization codes never enter logs or saved state. */
internal class GoogleCallback(private val timeoutMs: Long = 180000) : AutoCloseable {
    private val socket = ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0)); soTimeout = 500 }
    val redirect = "http://127.0.0.1:${socket.localPort}/"
    private fun random() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
    val state = random()
    val verifier = random()
    val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun authorization(client: GoogleClient, endpoint: URI): URI = URI(endpoint.toString() + "?" + googleQuery(linkedMapOf(
        "client_id" to client.id, "redirect_uri" to redirect, "response_type" to "code", "scope" to "openid email profile $YOUTUBE_READ_SCOPE",
        "state" to state, "code_challenge" to challenge, "code_challenge_method" to "S256", "access_type" to "offline", "prompt" to "consent select_account",
    )))
    suspend fun awaitCode(): String = runInterruptible(Dispatchers.IO) {
        val deadline = System.nanoTime() + timeoutMs * 1000000
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val connection = try { socket.accept() } catch (_: SocketTimeoutException) { continue }
            connection.use { peer ->
                val peerDeadline = minOf(deadline, System.nanoTime() + 2500000000)
                var status = 400; var accepted: String? = null; var denied = false
                try {
                    val bytes = ArrayList<Byte>(); val input = peer.getInputStream()
                    while (bytes.size < 16384) {
                        val remaining = peerDeadline - System.nanoTime()
                        require(remaining > 0)
                        peer.soTimeout = ((remaining / 1000000) + 1).coerceIn(1, 2500).toInt()
                        val next = input.read(); if (next < 0) break; bytes.add(next.toByte())
                        if (bytes.size >= 4 && bytes.takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte())) break
                    }
                    require(bytes.size < 16384 && bytes.takeLast(4) == listOf(13.toByte(), 10.toByte(), 13.toByte(), 10.toByte()))
                    val lines = bytes.toByteArray().toString(Charsets.US_ASCII).split("\r\n")
                    val request = lines.first().split(' ')
                    require(request.size == 3 && request[0] == "GET" && request[2] in listOf("HTTP/1.1", "HTTP/1.0"))
                    val target = URI(request[1]); require(!target.isAbsolute && target.path == "/" && target.fragment == null)
                    val hosts = lines.drop(1).filter { it.startsWith("Host:", true) }.map { it.substringAfter(':').trim() }
                    require(hosts == listOf("127.0.0.1:${socket.localPort}"))
                    val values = googleParams(target.rawQuery.orEmpty())
                    if (!MessageDigest.isEqual(state.toByteArray(), values["state"].orEmpty().toByteArray())) status = 403
                    else if (values.containsKey("error") && !values.containsKey("code")) { status = 200; denied = true }
                    else if (!values.containsKey("error") && values["code"]?.let { it.isNotBlank() && it.length <= 4096 && it.none(Char::isISOControl) } == true) { status = 200; accepted = values["code"] }
                } catch (_: Exception) { /* Reject malformed/unrelated requests; keep awaiting the legitimate callback. */ }
                val message = if (status == 200) "Podium Air received the authorization response. You can return to the app." else "This authorization response was not accepted."
                val body = message.toByteArray()
                runCatching { peer.getOutputStream().write(("HTTP/1.1 $status ${if (status == 200) "OK" else "Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'none'\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").toByteArray() + body) }
                if (denied) error("Google authorization was declined.")
                if (accepted != null) return@runInterruptible accepted!!
            }
        }
        error("Google sign-in timed out. Please try again.")
    }
    override fun close() { runCatching { socket.close() } }
}

data class GoogleIdentity(val subject: String, val name: String, val email: String)
data class GooglePlaylist(val id: String, val title: String, val count: Int)
data class GooglePlaylistItem(val videoId: String, val title: String, val channel: String, val position: Int)
data class GoogleAccountState(
    val configured: Boolean = false, val saved: Boolean = false, val busy: Boolean = false,
    val identity: GoogleIdentity? = null, val playlistAccess: Boolean = false,
    val playlists: List<GooglePlaylist> = emptyList(), val playlist: GooglePlaylist? = null, val items: List<GooglePlaylistItem> = emptyList(),
    val status: String = "Checking saved Google connection…", val truncated: Boolean = false,
)
internal class GoogleGrant(val access: String, val refresh: String, val scopes: Set<String>, val expiresAt: Long) {
    override fun toString() = "GoogleGrant(redacted)"
}
internal class GoogleApiFailure(val status: Int, val reason: String) : IllegalStateException(when {
    reason == "invalid_grant" -> "The saved Google authorization expired or was revoked. Sign in again."
    reason == "accessNotConfigured" || reason == "SERVICE_DISABLED" -> "Enable YouTube Data API v3 in the OAuth client's Google Cloud project."
    reason == "quotaExceeded" || reason == "dailyLimitExceeded" || status == 429 -> "The Google project request quota was reached. Try again later."
    status == 401 -> "Google authorization was rejected. Sign in again."
    status == 403 -> "Google denied this request. Check the granted playlist permission and OAuth project setup."
    else -> "Google returned HTTP $status. Please try again later."
})

/** Documented Google OAuth, OpenID userinfo and read-only YouTube Data API. No media extraction. */
internal class GoogleHttp(val endpoints: GoogleEndpoints = GoogleEndpoints(), private val clock: () -> Long = System::currentTimeMillis) : AutoCloseable {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
    private suspend fun request(uri: URI, form: Map<String, String>? = null, access: String? = null, limit: Int = 1000000): JsonObject = runInterruptible(Dispatchers.IO) {
        val b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Accept", "application/json")
        if (access != null) { require(access.length in 1..4096 && access.none(Char::isISOControl)); b.header("Authorization", "Bearer $access") }
        if (form != null) b.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(googleQuery(form))) else b.GET()
        val future = http.sendAsync(b.build(), HttpResponse.BodyHandler { limitedHttpBody(limit, "Google response exceeded its size limit.") })
        try {
            val result = future.get(18, TimeUnit.SECONDS)
            if (result.statusCode() !in 200..299) {
                val error = runCatching { Json.parseToJsonElement(result.body().toString(Charsets.UTF_8)).jsonObject }.getOrNull()
                val e = error?.get("error")
                val reason = (e as? JsonPrimitive)?.contentOrNull ?: ((e as? JsonObject)?.get("errors") as? JsonArray)?.firstOrNull()?.jsonObject?.text("reason").orEmpty()
                throw GoogleApiFailure(result.statusCode(), reason)
            }
            if (result.body().isEmpty()) JsonObject(emptyMap()) else runCatching { Json.parseToJsonElement(result.body().toString(Charsets.UTF_8)).jsonObject }.getOrElse { error("Google returned an invalid JSON response.") }
        } catch (e: ExecutionException) { throw e.cause ?: e }
        finally { if (!future.isDone) future.cancel(true) }
    }
    private fun grant(row: JsonObject, previous: GoogleGrant? = null): GoogleGrant {
        val access = row.text("access_token"); val refresh = row.text("refresh_token").ifBlank { previous?.refresh.orEmpty() }
        require(access.length in 1..4096 && access.none { it.isISOControl() || it.isWhitespace() } && row.text("token_type").equals("Bearer", true)) { "Google returned an unsupported token response." }
        require(refresh.toByteArray().size <= 1800 && refresh.none(Char::isISOControl)) { "Google refresh token cannot be securely stored." }
        val seconds = (row["expires_in"] as? JsonPrimitive)?.longOrNull ?: error("Google token expiry is missing.")
        require(seconds in 1..86400)
        require(row.text("scope").length <= 1600) { "Google returned an unsupported permission response." }
        val scopes = row.text("scope").split(' ').filter(String::isNotBlank).toSet().ifEmpty { previous?.scopes.orEmpty() }
        return GoogleGrant(access, refresh, scopes, clock() + seconds * 1000)
    }
    suspend fun exchange(client: GoogleClient, code: String, callback: GoogleCallback): GoogleGrant {
        val form = linkedMapOf("client_id" to client.id, "grant_type" to "authorization_code", "code" to code, "code_verifier" to callback.verifier, "redirect_uri" to callback.redirect)
        if (client.secret.isNotBlank()) form["client_secret"] = client.secret
        return grant(request(endpoints.token, form))
    }
    suspend fun refresh(client: GoogleClient, saved: GoogleGrant): GoogleGrant {
        require(saved.refresh.isNotBlank()) { "Sign in to Google again." }
        val form = linkedMapOf("client_id" to client.id, "grant_type" to "refresh_token", "refresh_token" to saved.refresh)
        if (client.secret.isNotBlank()) form["client_secret"] = client.secret
        return grant(request(endpoints.token, form), saved)
    }
    suspend fun identity(access: String): GoogleIdentity {
        val row = request(endpoints.user, access = access, limit = 64000)
        val id = row.text("sub"); require(id.length in 1..256)
        val email = if ((row["email_verified"] as? JsonPrimitive)?.booleanOrNull == true) row.text("email").take(256) else ""
        return GoogleIdentity(id, row.text("name").take(256).ifBlank { email.ifBlank { "Google account" } }, email)
    }
    private suspend fun pages(access: String, path: String, params: Map<String, String>): Pair<List<JsonObject>, Boolean> {
        val rows = mutableListOf<JsonObject>(); var next = ""; val seen = mutableSetOf<String>()
        repeat(10) {
            val fields = params + (if (next.isBlank()) emptyMap() else mapOf("pageToken" to next)) + mapOf("maxResults" to "50")
            val row = request(endpoints.youtube.resolve(path + "?" + googleQuery(fields)), access = access)
            val page = row["items"] as? JsonArray ?: error("Google returned an invalid playlist response.")
            require(page.size <= 50)
            rows += page.map { it.jsonObject }
            next = row.text("nextPageToken"); require(next.length <= 1024 && next.none(Char::isISOControl))
            if (next.isBlank()) return rows to false
            check(seen.add(next)) { "Google repeated a playlist page token." }
        }
        return rows to true
    }
    suspend fun playlists(access: String): Pair<List<GooglePlaylist>, Boolean> {
        val (rows, more) = pages(access, "playlists", mapOf("part" to "snippet,contentDetails", "mine" to "true"))
        return rows.mapNotNull { row ->
            val id = row.text("id"); if (!validGoogleId(id)) return@mapNotNull null
            GooglePlaylist(id, row["snippet"]?.jsonObject?.text("title").orEmpty().take(500), row["contentDetails"]?.jsonObject?.get("itemCount")?.jsonPrimitive?.intOrNull?.coerceAtLeast(0) ?: 0)
        }.distinctBy { it.id } to more
    }
    suspend fun items(access: String, id: String): Pair<List<GooglePlaylistItem>, Boolean> {
        require(validGoogleId(id))
        val (rows, more) = pages(access, "playlistItems", mapOf("part" to "snippet", "playlistId" to id))
        return rows.mapNotNull { row ->
            val snippet = row["snippet"]?.jsonObject ?: return@mapNotNull null
            val video = snippet["resourceId"]?.jsonObject?.text("videoId").orEmpty()
            if (!video.matches(Regex("[A-Za-z0-9_-]{11}"))) return@mapNotNull null
            GooglePlaylistItem(video, snippet.text("title").take(500), snippet.text("videoOwnerChannelTitle").take(256), (snippet["position"] as? JsonPrimitive)?.intOrNull ?: 0)
        }.sortedBy { it.position } to more
    }
    suspend fun revoke(refresh: String) { request(endpoints.revoke, mapOf("token" to refresh), limit = 64000) }
    fun fresh(grant: GoogleGrant) = grant.access.isNotBlank() && grant.expiresAt - clock() > 60000
    override fun close() { http.shutdownNow() }
}
internal fun validGoogleId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,128}"))

/** All observable state is changed on the owning UI scope; credentials have serialized writes/deletes. */
internal class GoogleConnection(
    private val scope: CoroutineScope, private val api: GoogleHttp = GoogleHttp(),
    private val clients: SecretStore = WindowsSecretStore("google-desktop-client"),
    private val refreshTokens: SecretStore = WindowsSecretStore("google-refresh-token"),
    private val permissions: SecretStore = WindowsSecretStore("google-permissions"),
    private val browse: (URI) -> Unit = { Desktop.getDesktop().browse(it) },
) : AutoCloseable {
    val state = MutableStateFlow(GoogleAccountState())
    private var client: GoogleClient? = null; private var grant: GoogleGrant? = null
    private var task: Job? = null; private var callback: GoogleCallback? = null
    private var epoch = 0L; private val writes = Mutex(); private val closed = AtomicBoolean(false)
    init { action("Checking saved Google connection…") {
        val restored = writes.withLock { withContext(Dispatchers.IO) {
            val selected = clients.read()?.let { GoogleClient.parse(it, downloaded = false) }
            val refresh = refreshTokens.read().orEmpty()
            val scopes = permissions.read().orEmpty().split(' ').filter(String::isNotBlank).toSet()
            selected to if (selected != null && refresh.isNotBlank()) GoogleGrant("", refresh, scopes, 0) else null
        } }
        currentCoroutineContext().ensureActive(); client = restored.first; grant = restored.second
        state.value = state.value.copy(configured = client != null, saved = grant != null, status = if (grant != null) "Saved Google connection. Refresh playlists to reconnect." else if (client != null) "Desktop OAuth client configured. Sign in to connect." else "Set up a Google Desktop OAuth client to connect.")
    } }
    private fun action(status: String, block: suspend () -> Unit) {
        if (closed.get()) return
        task?.cancel(); callback?.close(); callback = null
        val revision = ++epoch
        state.value = state.value.copy(busy = true, status = status)
        task = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if (e is GoogleApiFailure && (e.reason == "invalid_grant" || e.status == 401)) {
                    writes.withLock { withContext(Dispatchers.IO) { refreshTokens.delete(); permissions.delete() } }
                    grant = null
                    state.value = state.value.copy(saved = false, identity = null, playlistAccess = false, playlists = emptyList(), playlist = null, items = emptyList())
                }
                state.value = state.value.copy(status = e.message?.take(300) ?: "Google connection failed.")
            } finally { if (epoch == revision) state.value = state.value.copy(busy = false) }
        }
    }
    fun configure(file: File) = action("Reading Desktop OAuth configuration…") {
        val selected = withContext(Dispatchers.IO) { require(file.isFile && file.length() <= 32000); GoogleClient.parse(file.readText()) }
        writes.withLock { withContext(Dispatchers.IO) { refreshTokens.delete(); permissions.delete(); clients.write(selected.encode()) } }
        currentCoroutineContext().ensureActive(); client = selected; grant = null
        state.value = GoogleAccountState(configured = true, busy = true, status = "Desktop OAuth client configured. Sign in to connect.")
    }
    fun signIn() = action("Waiting for Google sign-in in your browser…") {
        val selected = client ?: error("Import a Google Desktop OAuth client JSON first.")
        val listener = GoogleCallback(); callback = listener
        try {
            browse(listener.authorization(selected, api.endpoints.authorize))
            val code = listener.awaitCode()
            val next = api.exchange(selected, code, listener)
            val profile = api.identity(next.access)
            saveGrant(next); currentCoroutineContext().ensureActive()
            state.value = state.value.copy(identity = profile, configured = true, saved = next.refresh.isNotBlank(), playlistAccess = YOUTUBE_READ_SCOPE in next.scopes, playlists = emptyList(), playlist = null, items = emptyList())
            if (YOUTUBE_READ_SCOPE in next.scopes) loadPlaylists(next) else state.value = state.value.copy(status = "Signed in. YouTube playlist permission was not granted; playlist browsing is unavailable.")
        } finally { listener.close(); if (callback === listener) callback = null }
    }
    private suspend fun saveGrant(next: GoogleGrant) {
        writes.withLock {
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO) {
                if (next.refresh.isNotBlank()) { refreshTokens.write(next.refresh); permissions.write(next.scopes.joinToString(" ")) }
                else { refreshTokens.delete(); permissions.delete() }
            }
        }
        currentCoroutineContext().ensureActive(); grant = next
    }
    private suspend fun access(): GoogleGrant {
        val saved = grant ?: error("Sign in to Google first.")
        if (api.fresh(saved)) return saved
        val refreshed = api.refresh(client ?: error("Desktop OAuth configuration is missing."), saved)
        saveGrant(refreshed); return refreshed
    }
    private suspend fun loadPlaylists(next: GoogleGrant) {
        require(YOUTUBE_READ_SCOPE in next.scopes) { "YouTube playlist permission was not granted. Sign in and allow read-only playlist access." }
        val (playlists, more) = api.playlists(next.access); currentCoroutineContext().ensureActive()
        state.value = state.value.copy(playlists = playlists, playlist = null, items = emptyList(), playlistAccess = true, truncated = more,
            status = if (more) "Showing the first 500 playlists." else "Loaded ${playlists.size} YouTube playlists. Metadata only; native streaming and playlist writes are unavailable.")
    }
    fun refresh() = action("Refreshing your Google playlists…") {
        val next = access(); val profile = api.identity(next.access); currentCoroutineContext().ensureActive()
        state.value = state.value.copy(identity = profile, saved = next.refresh.isNotBlank(), playlistAccess = YOUTUBE_READ_SCOPE in next.scopes)
        loadPlaylists(next)
    }
    fun select(playlist: GooglePlaylist) = action("Loading ${playlist.title.take(100)}…") {
        require(state.value.playlists.any { it.id == playlist.id })
        val next = access(); require(YOUTUBE_READ_SCOPE in next.scopes)
        val (items, more) = api.items(next.access, playlist.id); currentCoroutineContext().ensureActive()
        state.value = state.value.copy(playlist = playlist, items = items, truncated = more, status = if (more) "Showing the first 500 playlist items." else "Loaded ${items.size} playlist items. Open them on YouTube for playback.")
    }
    fun back() { if (!state.value.busy) state.value = state.value.copy(playlist = null, items = emptyList(), truncated = false) }
    fun cancel() { ++epoch; task?.cancel(); callback?.close(); callback = null; state.value = state.value.copy(busy = false, status = "Google operation cancelled.") }
    fun disconnect() {
        cancel()
        action("Removing saved Google authorization…") {
            val previous = grant; grant = null
            writes.withLock { withContext(Dispatchers.IO) { refreshTokens.delete(); permissions.delete() } }
            state.value = GoogleAccountState(configured = client != null, busy = true, status = "Disconnected. Saved authorization was deleted.")
            if (previous?.refresh?.isNotBlank() == true) {
                try { api.revoke(previous.refresh) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { state.value = state.value.copy(status = "Disconnected locally. Google revocation could not be confirmed; remove Podium Air's access in your Google account if needed.") }
            }
        }
    }
    fun forgetClient() = action("Removing Google configuration…") {
        writes.withLock { withContext(Dispatchers.IO) { refreshTokens.delete(); permissions.delete(); clients.delete() } }
        currentCoroutineContext().ensureActive(); client = null; grant = null
        state.value = GoogleAccountState(busy = true, status = "Google configuration and saved authorization were deleted.")
    }
    override fun close() { if (closed.compareAndSet(false, true)) { ++epoch; task?.cancel(); callback?.close(); api.close() } }
}
