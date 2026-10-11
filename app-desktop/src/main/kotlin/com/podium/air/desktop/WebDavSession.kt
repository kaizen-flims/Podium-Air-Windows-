// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI

@Serializable
internal data class DavCredentials(val root: String, val username: String, val password: String)

/** No credentials are loaded and no network is contacted until a user command. */
internal class DavSession(
    private val scope: CoroutineScope,
    private val secrets: SecretStore = WindowsSecretStore("webdav"),
    private val client: DavClient = DavClient(),
    private val savedRoot: () -> String,
    private val configured: (String, String) -> Unit,
) : AutoCloseable {
    val entries = MutableStateFlow<List<DavEntry>>(emptyList())
    val location = MutableStateFlow<URI?>(null)
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("Connect your HTTPS WebDAV music folder to browse tracks.")
    private val credentialMutex = Mutex()
    private var job: Job? = null
    private var epoch = 0L

    fun connect(url: String, username: String, password: String) {
        val command = ++epoch; job?.cancel()
        job = scope.launch {
            busy.value = true; status.value = "Connecting to WebDAV…"
            try {
                val root = DavPaths.root(url)
                val credentials = DavCredentials(root.toASCIIString(), username, password)
                val encoded = Json.encodeToString(DavCredentials.serializer(), credentials)
                require(encoded.toByteArray().size <= 2048) { "WebDAV credentials are too long for secure storage." }
                val listing = client.list(root, root, username, password)
                ensureActive()
                credentialMutex.withLock { withContext(Dispatchers.IO) { secrets.write(encoded) } }
                ensureActive()
                if (epoch == command) {
                    configured(root.toASCIIString(), username)
                    location.value = root; entries.value = listing
                    status.value = "Connected • ${listing.count { !it.folder }} tracks in this folder."
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == command) status.value = e.message ?: "Could not connect to WebDAV." }
            finally { if (epoch == command) busy.value = false }
        }
    }
    private suspend fun credentials(): DavCredentials {
        val encoded = credentialMutex.withLock { withContext(Dispatchers.IO) { secrets.read() } }
        val value = encoded?.let { runCatching { Json.decodeFromString<DavCredentials>(it) }.getOrNull() }
        check(value != null && value.root == savedRoot()) { "Reconnect your WebDAV folder to save its credentials." }
        return value
    }
    fun browse(directory: URI? = null) {
        if (busy.value) return
        val command = ++epoch
        job = scope.launch {
            busy.value = true; status.value = "Loading folder…"
            try {
                val credentials = credentials(); val root = DavPaths.root(credentials.root)
                val target = directory ?: root
                val listing = client.list(root, target, credentials.username, credentials.password)
                ensureActive()
                if (epoch == command) {
                    location.value = target; entries.value = listing
                    status.value = "${listing.count { !it.folder }} tracks • ${listing.count { it.folder }} folders"
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (epoch == command) status.value = e.message ?: "Could not browse WebDAV." }
            finally { if (epoch == command) busy.value = false }
        }
    }
    suspend fun download(entry: DavEntry, directory: File): File {
        val credentials = credentials()
        return client.download(DavPaths.root(credentials.root), entry, credentials.username, credentials.password, directory)
    }
    fun cancel() { ++epoch; job?.cancel(); busy.value = false; status.value = "WebDAV operation cancelled." }
    fun disconnect() {
        val command = ++epoch; job?.cancel()
        job = scope.launch {
            busy.value = true
            try {
                credentialMutex.withLock { withContext(Dispatchers.IO) { secrets.delete() } }
                ensureActive()
                if (epoch == command) {
                    configured("", ""); entries.value = emptyList(); location.value = null
                    status.value = "Disconnected. Imported tracks remain available offline."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (epoch == command) status.value = "Could not delete saved WebDAV credentials. Try disconnecting again." }
            finally { if (epoch == command) busy.value = false }
        }
    }
    override fun close() { ++epoch; job?.cancel(); client.close() }
}
