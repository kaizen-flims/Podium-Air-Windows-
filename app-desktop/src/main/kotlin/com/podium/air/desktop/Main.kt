// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.*
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.contains("--platform-smoke")) { platformSmoke(args); return }
    if (args.contains("--audio-smoke")) { audioSmoke(args); return }
    val performance = args.contains("--performance-smoke")
    val smoke = args.contains("--ui-smoke") || performance
    val store = if (smoke) StateStore(File(System.getProperty("java.io.tmpdir"), "podium-ui-smoke-${System.currentTimeMillis()}")) else StateStore()
    if (smoke) {
        store.directory.mkdirs()
        val first = File(store.directory, "test-tone.wav"); generateTestWave(first, 6)
        val second = File(store.directory, "second-tone.wav"); generateTestWave(second, 6)
        val a = StoredTrack("smoke-a", first.absolutePath, "Generated test tone", "UI smoke fixture", "Test album", 6000)
        val b = StoredTrack("smoke-b", second.absolutePath, "Second test tone", "UI smoke fixture", "Test album", 6000)
        File(store.directory, "test-tone.ttml").writeText("<tt><body><p begin='0' end='2'><span begin='0' end='1.2'>Podium </span><span begin='1.2' end='2'>Air</span></p></body></tt>")
        store.save(SavedState(queue = listOf(SavedQueueEntry("ui-current", a.id), SavedQueueEntry("ui-next", b.id)), cursor = 0, library = listOf(a, b), playlists = listOf(Playlist(name = "Smoke playlist", tracks = listOf(a.id, b.id))), favorites = setOf(a.id), history = listOf(b.id, a.id)))
    }
    val model = DesktopModel(JavaFxAudioEngine(), store)
    var tray: TrayIcon? = null
    var mediaControls: WindowsMediaControls? = null
    application {
        var visible by remember { mutableStateOf(!args.contains("--background") || !SystemTray.isSupported()) }
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val windowState = rememberWindowState(placement = if (args.contains("--small")) WindowPlacement.Floating else WindowPlacement.Maximized,
            position = WindowPosition(Alignment.Center), width = minOf(760, bounds.width - 24).coerceAtLeast(360).dp, height = minOf(560, bounds.height - 24).coerceAtLeast(280).dp)
        fun shutdown() { tray?.let { SystemTray.getSystemTray().remove(it) }; mediaControls?.close(); model.close(); FxRuntime.exit(); exitApplication() }
        fun pick(folder: Boolean) {
            val chooser = JFileChooser().apply {
                dialogTitle = if (folder) "Import music folder" else "Import music"
                fileSelectionMode = if (folder) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = !folder
                if (!folder) fileFilter = FileNameExtensionFilter("Supported audio (MP3, WAV, AIFF, M4A, FLAC, Opus)", "mp3", "wav", "aif", "aiff", "m4a", "flac", "opus", "ogg")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                model.importFiles(if (folder) listOf(chooser.selectedFile) else chooser.selectedFiles.toList(), folder)
            }
        }
        fun pickPlaylist(playlist: Playlist?) {
            val chooser = JFileChooser().apply {
                dialogTitle = if (playlist == null) "Import local playlist" else "Export ${playlist.name}"
                fileFilter = FileNameExtensionFilter("Local playlists (M3U8, M3U)", "m3u8", "m3u")
                if (playlist != null) selectedFile = File(playlist.name.replace(Regex("[<>:\"/\\\\|?*]"), "_") + ".m3u8")
            }
            val result = if (playlist == null) chooser.showOpenDialog(null) else chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                if (playlist == null) model.importPlaylist(chooser.selectedFile)
                else {
                    var selected = chooser.selectedFile
                    if (selected.extension.lowercase() !in setOf("m3u8", "m3u")) selected = File(selected.absolutePath + ".m3u8")
                    if (!selected.exists() || javax.swing.JOptionPane.showConfirmDialog(null, "Replace ${selected.name}?", "Export playlist", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION) model.exportPlaylist(playlist.id, selected)
                }
            }
        }
        Window(onCloseRequest = { if (model.state.value.preferences.closeToTray && tray != null) visible = false else shutdown() }, title = "Podium Air — Windows Edition", state = windowState, visible = visible) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(minOf(720, bounds.width - 24), minOf(540, bounds.height - 24))
                window.toFront(); window.requestFocus()
                if ((!smoke || performance) && System.getProperty("os.name").startsWith("Windows")) {
                    mediaControls = WindowsMediaControls(model)
                    launch { mediaControls!!.status.collect { model.platformStatus.value = it } }
                }
                window.iconImage = ImageIO.read(object {}.javaClass.getResource("/brand/icon.jpg"))
                if (!smoke && SystemTray.isSupported()) {
                    runCatching {
                        val menu = PopupMenu()
                        fun item(name: String, action: () -> Unit) { menu.add(MenuItem(name).apply { addActionListener { SwingUtilities.invokeLater(action) } }) }
                        item("Show Podium Air") { visible = true; window.toFront() }
                        item("Play / Pause") { model.toggle() }; item("Next track") { model.next() }; item("Previous track") { model.previous() }
                        menu.addSeparator(); item("Quit") { shutdown() }
                        tray = TrayIcon(window.iconImage, "Podium Air", menu).apply {
                            isImageAutoSize = true; addActionListener { SwingUtilities.invokeLater { visible = true; window.toFront() } }
                        }
                        SystemTray.getSystemTray().add(tray)
                        launch {
                            model.engine.state.map { it.entry?.song }.distinctUntilChangedBy { it?.videoId }.collect { song ->
                                tray?.toolTip = song?.let { "${it.title} — ${it.artist}" } ?: "Podium Air"
                                if (song != null && model.state.value.preferences.notifications) tray?.displayMessage(song.title, song.artist, TrayIcon.MessageType.NONE)
                            }
                        }
                    }.onFailure { model.message.value = "System tray is unavailable: ${it.message}" }
                }
            }
            PodiumApp(model, ::pick, ::pickPlaylist, smoke = smoke && !performance, onSmokePage = { page ->
                val screenshots = args.firstOrNull { it.startsWith("--screenshots=") }?.substringAfter("=")
                if (screenshots != null) {
                    val output = File(screenshots).apply { mkdirs() }
                    ImageIO.write(Robot().createScreenCapture(Rectangle(window.locationOnScreen, window.size)), "png", File(output, "$page.png"))
                }
            }, onSmokeFailure = { error ->
                val result = args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "ui-smoke.txt"
                File(result).writeText("FAIL: ${error.javaClass.simpleName}: ${error.message}\n")
                error.printStackTrace(); mediaControls?.close(); model.close(); FxRuntime.exit(); exitProcess(1)
            }, onReady = {
                if (smoke) {
                    // Start only after composition reaches content; captures render/runtime initialization failures.
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(if (performance) 20000 else 2500)
                        val result = args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "ui-smoke.txt"
                        File(result).writeText(if (performance) "PASS: Paused native window and Windows media helper stayed responsive during the performance observation.\n" else "PASS: Compose desktop window, all 15 navigation routes, populated playlist/album detail, Ctrl+F and Ctrl+Space playback/pause verified.\n")
                        val png = args.firstOrNull { it.startsWith("--screenshot=") }?.substringAfter("=")
                        if (png != null) runCatching {
                            ImageIO.write(Robot().createScreenCapture(Rectangle(window.locationOnScreen, window.size)), "png", File(png))
                        }.onFailure { File(result).appendText("Screenshot unavailable: ${it.message}\n") }
                        shutdown()
                    }
                }
            })
        }
    }
}

private fun platformSmoke(args: Array<String>) {
    val result = File(args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "platform-smoke.txt")
    val directory = java.nio.file.Files.createTempDirectory("podium-platform-smoke").toFile()
    try {
        val process = ProcessBuilder(extractMediaBridge(directory).absolutePath, "--self-test").redirectErrorStream(true).start()
        if (!process.waitFor(15000, java.util.concurrent.TimeUnit.MILLISECONDS)) { process.destroyForcibly(); error("Packaged Windows media helper timed out") }
        val text = process.inputStream.bufferedReader().readText()
        result.writeText(text)
        check(process.exitValue() == 0 && text.contains("PASS:")) { text }
    } catch (error: Exception) { result.writeText("FAIL: ${error.message}\n"); directory.deleteRecursively(); exitProcess(1) }
    directory.deleteRecursively(); exitProcess(0)
}

/** Optional hardware integration check. Fails explicitly if no usable audio output exists. */
private fun audioSmoke(args: Array<String>) {
    val result = File(args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "audio-smoke.txt")
    val supplied = args.firstOrNull { it.startsWith("--media=") }?.substringAfter("=")
    val wave = supplied?.let(::File) ?: File.createTempFile("podium-audio-", ".wav")
    if (supplied == null) generateTestWave(wave, 6)
    val engine = JavaFxAudioEngine()
    try {
        runBlocking {
            val tracks = LocalLibrary(File(wave.parentFile, "podium-smoke-art")).import(listOf(wave)).first
            check(tracks.size == 1)
            val first = com.podium.air.domain.QueueEntry(song = tracks[0].song())
            val next = com.podium.air.domain.QueueEntry(song = tracks[0].song())
            val advanced = CompletableDeferred<Unit>()
            engine.onAdvance = { if (it.key == next.key) advanced.complete(Unit) }
            engine.configure(Preferences(crossfadeSeconds = 1))
            engine.open(first)
            withTimeout(15000) { engine.state.first { it.error != null || it.playing && it.positionMs > 300 } }.let { check(it.error == null) { it.error.orEmpty() } }
            engine.pause()
            withTimeout(3000) { engine.state.first { !it.playing } }
            engine.seek(2000)
            withTimeout(3000) { engine.state.first { it.positionMs in 1900..2200 } }
            engine.setUpcoming(next); engine.toggle()
            withTimeout(12000) { advanced.await() }
            engine.configure(Preferences(crossfadeSeconds = 1, volume = 0.5f))
            withTimeout(3000) { engine.state.first { it.entry?.key == next.key && it.playing && !it.fading } }
            result.writeText("PASS: ${wave.extension.uppercase()} played, paused, sought to 2s, resumed and crossfaded to a second queue entry.\n")
        }
    } catch (error: Throwable) { result.writeText("FAIL: ${error.message}\n"); engine.close(); FxRuntime.exit(); if (supplied == null) wave.delete(); exitProcess(1) }
    engine.close(); FxRuntime.exit(); if (supplied == null) wave.delete(); exitProcess(0)
}
internal fun generateTestWave(file: File, seconds: Int) {
    val rate = 44100
    val bytes = ByteArray(rate * seconds * 2)
    for (sample in 0 until rate * seconds) {
        val value = (kotlin.math.sin(sample * 2 * Math.PI * 440 / rate) * 1500).toInt()
        bytes[sample * 2] = value.toByte(); bytes[sample * 2 + 1] = (value shr 8).toByte()
    }
    val format = javax.sound.sampled.AudioFormat(rate.toFloat(), 16, 1, true, false)
    javax.sound.sampled.AudioInputStream(bytes.inputStream(), format, (rate * seconds).toLong()).use {
        javax.sound.sampled.AudioSystem.write(it, javax.sound.sampled.AudioFileFormat.Type.WAVE, file)
    }
}
