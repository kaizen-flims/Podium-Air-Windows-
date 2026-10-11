// SPDX-License-Identifier: GPL-3.0-only
// Desktop adaptation of Podium Air's data/webdav folder-library behavior.
package com.podium.air.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class DavEntry(val uri: URI, val name: String, val folder: Boolean, val bytes: Long? = null, val etag: String? = null) {
    val audio: Boolean get() = !folder && uri.path.substringAfterLast('.').lowercase(Locale.ROOT) in DAV_AUDIO
}
internal val DAV_AUDIO = setOf("mp3", "wav", "aif", "aiff", "m4a", "flac", "opus")

/** Credential-bearing requests remain inside the exact configured origin and folder. */
internal object DavPaths {
    fun root(raw: String): URI {
        val text = raw.trim().let { if ("://" in it) it else "https://$it" }
        val uri = runCatching { URI(text) }.getOrElse { throw IllegalArgumentException("Enter a valid HTTPS WebDAV folder URL.") }
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("127.0.0.1", "localhost", "[::1]"))) { "WebDAV requires HTTPS." }
        validate(uri)
        return URI(uri.toASCIIString().trimEnd('/') + "/")
    }
    private fun validate(uri: URI) {
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "Use a server URL without credentials, query or fragment." }
        require(uri.port == -1 || uri.port in 1..65535)
        val path = uri.path.orEmpty()
        require('\\' !in path && path.none { it.isISOControl() } && path.split('/').none { it == "." || it == ".." }) { "Invalid WebDAV path." }
        require(!Regex("(?i)%(?:2f|5c|00)|%25(?:2e|2f|5c|00)").containsMatchIn(uri.rawPath.orEmpty())) { "Encoded path separators are unsupported." }
    }
    fun within(root: URI, candidate: URI): Boolean = runCatching {
        validate(candidate)
        fun port(u: URI) = if (u.port != -1) u.port else if (u.scheme == "https") 443 else 80
        candidate.scheme == root.scheme && candidate.host.equals(root.host, true) && port(candidate) == port(root) && candidate.path.startsWith(root.path)
    }.getOrDefault(false)
    fun resolve(root: URI, directory: URI, href: String): URI? = runCatching {
        val raw = URI(href.trim())
        // Reject traversal before URI.resolve can normalize it away.
        require(raw.path.orEmpty().split('/').none { it == "." || it == ".." })
        directory.resolve(raw).takeIf { within(root, it) }
    }.getOrNull()
    fun parent(root: URI, directory: URI): URI = directory.resolve("../").takeIf { within(root, it) } ?: root
}

internal fun parseDavListing(xml: String, root: URI, directory: URI): List<DavEntry> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        isXIncludeAware = false; isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder().apply {
        setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
            override fun error(e: org.xml.sax.SAXParseException) { throw e }
            override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
        })
    }
    val doc = try { builder.parse(xml.byteInputStream(Charsets.UTF_8)) } catch (_: Exception) { throw IllegalArgumentException("Invalid WebDAV directory response.") }
    require(doc.documentElement.localName == "multistatus" && doc.documentElement.namespaceURI == "DAV:") { "Expected a WebDAV multistatus response." }
    fun Element.children(name: String): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.namespaceURI == "DAV:" && it.localName == name }
    val responses = doc.documentElement.children("response")
    require(responses.size <= 5000) { "WebDAV folder exceeds 5,000 entries." }
    return responses.mapNotNull { response ->
        val href = response.children("href").firstOrNull()?.textContent ?: return@mapNotNull null
        val uri = DavPaths.resolve(root, directory, href) ?: return@mapNotNull null
        if (uri.path.trimEnd('/') == directory.path.trimEnd('/')) return@mapNotNull null
        // Depth 1 must not smuggle descendants into a directory's browse result.
        val relative = uri.path.removePrefix(directory.path).trimEnd('/')
        if (!uri.path.startsWith(directory.path) || relative.isBlank() || '/' in relative) return@mapNotNull null
        val props = response.children("propstat").filter { Regex("HTTP/\\S+\\s+200(?:\\s|$)").containsMatchIn(it.children("status").firstOrNull()?.textContent.orEmpty()) }.flatMap { it.children("prop") }
        if (props.isEmpty()) return@mapNotNull null
        fun property(name: String) = props.flatMap { it.children(name) }.firstOrNull()
        val folder = property("resourcetype")?.children("collection")?.isNotEmpty() == true
        val name = property("displayname")?.textContent?.trim()?.take(500)?.takeIf { it.isNotBlank() } ?: uri.path.trimEnd('/').substringAfterLast('/')
        val bytes = property("getcontentlength")?.textContent?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
        val etag = property("getetag")?.textContent?.trim()?.takeIf { it.length <= 200 && it.none(Char::isISOControl) }
        DavEntry(if (folder) URI(uri.toASCIIString().trimEnd('/') + "/") else uri, name.filterNot(Char::isISOControl), folder, bytes, etag)
    }.distinctBy { it.uri }.filter { it.folder || it.audio }.sortedWith(compareByDescending<DavEntry> { it.folder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/** Read-only PROPFIND/GET. No redirect follows, remote writes, cookies or background scans. */
internal class DavClient(
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build(),
    private val maxFileBytes: Long = 750_000_000,
) : AutoCloseable {
    private val active = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    override fun close() { active.forEach { it.cancel(true) }; client.shutdownNow() }
    private fun request(root: URI, target: URI, username: String, password: String): HttpRequest.Builder {
        require(DavPaths.within(root, target)) { "WebDAV request leaves the configured folder." }
        require(':' !in username && (username + password).none(Char::isISOControl)) { "Invalid WebDAV credentials." }
        require(username.length <= 200 && password.length <= 1000)
        val builder = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(120)).header("User-Agent", "PodiumAirWindows/0.2.0")
        if (username.isNotBlank() || password.isNotBlank()) {
            val bytes = "$username:$password".toByteArray(Charsets.UTF_8)
            try { builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(bytes)) } finally { bytes.fill(0) }
        }
        return builder
    }
    private fun <T> await(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
        val future = client.sendAsync(request, handler); active.add(future)
        try { return future.get(125, TimeUnit.SECONDS) }
        catch (e: InterruptedException) { throw e }
        catch (_: Exception) { throw IllegalStateException("WebDAV request failed or timed out.") }
        finally { active.remove(future); if (!future.isDone) future.cancel(true) }
    }
    suspend fun list(root: URI, directory: URI, username: String, password: String): List<DavEntry> = runInterruptible(Dispatchers.IO) {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getetag/></d:prop></d:propfind>"""
        val request = request(root, directory, username, password).header("Depth", "1").header("Content-Type", "application/xml; charset=utf-8").method("PROPFIND", HttpRequest.BodyPublishers.ofString(body)).build()
        val response = await(request, HttpResponse.BodyHandler { limitedHttpBody(2_000_000, "WebDAV listing exceeds 2 MB.") })
        check(response.statusCode() == 207) { "WebDAV directory request returned HTTP ${response.statusCode()}." }
        parseDavListing(response.body().toString(Charsets.UTF_8), root, directory)
    }
    suspend fun download(root: URI, entry: DavEntry, username: String, password: String, directory: File): File = runInterruptible(Dispatchers.IO) {
        require(entry.audio && DavPaths.within(root, entry.uri)) { "Select a supported audio file inside the WebDAV folder." }
        require(entry.bytes == null || entry.bytes in 1..maxFileBytes) { "WebDAV track is empty or exceeds 750 MB." }
        directory.mkdirs()
        val key = MessageDigest.getInstance("SHA-256").digest(entry.uri.toASCIIString().toByteArray()).joinToString("") { "%02x".format(it) }
        val extension = entry.uri.path.substringAfterLast('.').lowercase(Locale.ROOT)
        val target = File(directory, "$key.$extension")
        val temp = Files.createTempFile(directory.toPath(), "webdav-", ".part")
        val sink = DavFileBody(temp.toFile(), maxFileBytes)
        try {
            val request = request(root, entry.uri, username, password)
            entry.etag?.takeUnless { it.startsWith("W/") }?.let { request.header("If-Match", it) }
            val response = await(request.GET().build(), HttpResponse.BodyHandler { info ->
                if (info.statusCode() != 200) sink.fail(IllegalStateException("WebDAV download returned HTTP ${info.statusCode()}."))
                val size = info.headers().firstValueAsLong("Content-Length").orElse(-1)
                if (size > maxFileBytes) sink.fail(IllegalStateException("WebDAV track exceeds 750 MB."))
                sink
            })
            check(response.statusCode() == 200 && sink.count > 0) { "WebDAV download failed or was empty." }
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            try { Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            target
        } finally { sink.abort(); Files.deleteIfExists(temp) }
    }
}

/** Streams bounded bytes to disk, closing its handle before cancellation cleanup. */
private class DavFileBody(file: File, private val limit: Long) : HttpResponse.BodySubscriber<File> {
    private val result = CompletableFuture<File>()
    private val channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE)
    private val lock = Any()
    private val file = file
    private var subscription: Flow.Subscription? = null
    private var done = false
    var count: Long = 0; private set
    override fun getBody() = result
    fun fail(error: Throwable) = synchronized(lock) {
        if (!done) { done = true; subscription?.cancel(); channel.close(); result.completeExceptionally(error) }
    }
    fun abort() { fail(java.util.concurrent.CancellationException("WebDAV transfer cancelled.")) }
    override fun onSubscribe(value: Flow.Subscription) = synchronized(lock) {
        if (done || subscription != null) value.cancel() else { subscription = value; value.request(1) }
    }
    override fun onNext(buffers: List<ByteBuffer>) = synchronized(lock) {
        if (!done) try {
            for (buffer in buffers) {
                val bytes = buffer.remaining().toLong()
                check(bytes <= limit - count) { "WebDAV track exceeds 750 MB." }
                while (buffer.hasRemaining()) channel.write(buffer)
                count += bytes
            }
            subscription?.request(1)
        } catch (e: Exception) { fail(e) }
    }
    override fun onError(error: Throwable) { fail(error) }
    override fun onComplete() = synchronized(lock) {
        if (!done) { done = true; channel.close(); result.complete(file) }
    }
}
