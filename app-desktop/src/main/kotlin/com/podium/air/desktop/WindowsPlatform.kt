// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

interface MediaControls : AutoCloseable { val status: StateFlow<String> }

/** SMTC uses a separate native process with an HWND it owns; no global keyboard hook is installed. */
class WindowsMediaControls(private val model: DesktopModel) : MediaControls {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow("Starting Windows media controls…")
    override val status: StateFlow<String> = mutable
    private val lifecycle = Any()
    private var closed = false
    private var process: Process? = null
    private var writer: java.io.BufferedWriter? = null
    init {
        scope.launch {
            try {
                val native = extractMediaBridge()
                val child = ProcessBuilder(native.absolutePath).redirectErrorStream(true).start()
                synchronized(lifecycle) {
                    if (closed) { child.destroyForcibly(); return@launch }
                    process = child; writer = child.outputStream.bufferedWriter(Charsets.UTF_8)
                }
                launch {
                    child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        when {
                            line == "READY" -> mutable.value = "Connected to Windows system media controls"
                            line.startsWith("ERROR\t") -> mutable.value = "Windows media controls: ${line.substringAfter('\t')}"
                            line != "ACK" -> withContext(Dispatchers.Main) { handleMediaCommand(model, line) }
                        }
                    } }
                    if (isActive) mutable.value = "Windows media controls disconnected. Restart the app to reconnect."
                }
                combine(model.engine.state, model.queue, model.state) { audio, queue, saved ->
                    val song = audio.entry?.song
                    listOf("UPDATE", if (song != null) "1" else "0", if (audio.playing) "1" else "0",
                        (audio.positionMs / 1000 * 1000).toString(), audio.durationMs.toString(),
                        if (queue.nextIndex() != null) "1" else "0", if (queue.current != null) "1" else "0",
                        mediaText(song?.title.orEmpty()), mediaText(song?.artist.orEmpty()), mediaText(song?.albumName.orEmpty()),
                        mediaText(song?.thumbnailUrl.orEmpty()), saved.preferences.speed.toString()).joinToString("\t")
                }.distinctUntilChanged().collect { packet -> writer?.let { synchronized(it) { it.write(packet); it.newLine(); it.flush() } } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = "Windows media controls unavailable: ${error.message}" }
        }
    }
    override fun close() {
        val resources = synchronized(lifecycle) {
            if (closed) return
            closed = true
            (writer to process).also { writer = null; process = null }
        }
        scope.cancel()
        resources.first?.let { runCatching { synchronized(it) { it.write("QUIT\n"); it.flush(); it.close() } } }
        resources.second?.let { if (!it.waitFor(1500, TimeUnit.MILLISECONDS)) it.destroyForcibly() }
    }
}
internal fun mediaText(text: String): String = text.take(16000).toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
internal fun handleMediaCommand(model: DesktopModel, command: String) {
    when (command.substringBefore('\t')) {
        "PLAY" -> if (!model.engine.state.value.playing) model.toggle()
        "PAUSE" -> model.engine.pause()
        "NEXT" -> model.next()
        "PREVIOUS" -> model.previous()
        "STOP" -> model.engine.stop()
        "FORWARD" -> model.seek(model.engine.state.value.positionMs + 10000)
        "REWIND" -> model.seek(model.engine.state.value.positionMs - 10000)
        "SEEK" -> command.substringAfter('\t', "").toLongOrNull()?.let(model::seek)
    }
}
internal fun extractMediaBridge(directory: File = File(defaultDataDirectory(), "platform")): File {
    val bytes = requireNotNull(object {}.javaClass.getResourceAsStream("/windows/PodiumMediaBridge.exe")) {
        "The native Windows helper is absent. Build it using platform-windows/build.ps1 before packaging."
    }.use { it.readBytes() }
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    directory.mkdirs()
    val target = File(directory, "PodiumMediaBridge-${hash.take(16)}.exe")
    if (!target.exists()) {
        val temporary = Files.createTempFile(directory.toPath(), "bridge-", ".tmp")
        try { Files.write(temporary, bytes); Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temporary) }
    }
    return target
}

object WindowsStartup {
    fun set(enabled: Boolean, executable: File? = packagedExecutable()) {
        require(System.getProperty("os.name").startsWith("Windows")) { "Launch at startup is available on Windows." }
        val folder = File(requireNotNull(System.getenv("APPDATA")), "Microsoft/Windows/Start Menu/Programs/Startup")
        val script = File(folder, "Podium Air.cmd")
        if (!enabled) { check(!script.exists() || script.delete()) { "Could not remove the startup entry." }; return }
        require(executable?.isFile == true && executable.extension.equals("exe", true)) { "Launch at startup requires the packaged Windows app." }
        require(!executable.absolutePath.contains('\n') && !executable.absolutePath.contains('\r'))
        folder.mkdirs()
        val escaped = executable.absolutePath.replace("%", "%%")
        script.writeText("@echo off\r\nchcp 65001 >nul\r\nstart \"\" \"$escaped\" --background\r\n", Charsets.UTF_8)
    }
    fun packagedExecutable(): File? = System.getProperty("jpackage.app-path")?.let(::File)
        ?: ProcessHandle.current().info().command().orElse(null)?.let(::File)?.takeIf { it.name == "Podium Air.exe" }
}
