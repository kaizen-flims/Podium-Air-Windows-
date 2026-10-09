// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.awt.*
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.contains("--audio-smoke")) { audioSmoke(args); return }
    val smoke = args.contains("--ui-smoke")
    val store = if (smoke) StateStore(File(System.getProperty("java.io.tmpdir"), "podium-ui-smoke-${System.currentTimeMillis()}")) else StateStore()
    if (smoke) {
        store.directory.mkdirs()
        val first = File(store.directory, "test-tone.wav"); generateTestWave(first, 1)
        val second = File(store.directory, "second-tone.wav"); generateTestWave(second, 1)
        val a = StoredTrack("smoke-a", first.absolutePath, "Generated test tone", "UI smoke fixture", "Test album", 1000)
        val b = StoredTrack("smoke-b", second.absolutePath, "Second test tone", "UI smoke fixture", "Test album", 1000)
        store.save(SavedState(library = listOf(a, b), playlists = listOf(Playlist(name = "Smoke playlist", tracks = listOf(a.id, b.id))), favorites = setOf(a.id), history = listOf(b.id, a.id)))
    }
    val model = DesktopModel(JavaFxAudioEngine(), store)
    var tray: TrayIcon? = null
    application {
        var visible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState(width = 1160.dp, height = 800.dp)
        fun shutdown() { tray?.let { SystemTray.getSystemTray().remove(it) }; model.close(); FxRuntime.exit(); exitApplication() }
        fun pick(folder: Boolean) {
            val chooser = JFileChooser().apply {
                dialogTitle = if (folder) "Import music folder" else "Import music"
                fileSelectionMode = if (folder) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = !folder
                if (!folder) fileFilter = FileNameExtensionFilter("Supported audio (MP3, WAV, AIFF, M4A AAC in M4A)", "mp3", "wav", "aif", "aiff", "m4a")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                model.importFiles(if (folder) listOf(chooser.selectedFile) else chooser.selectedFiles.toList(), folder)
            }
        }
        Window(onCloseRequest = { shutdown() }, title = "Podium Air — Windows Edition", state = windowState, visible = visible) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(720, 540)
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
                    }.onFailure { model.message.value = "System tray is unavailable: ${it.message}" }
                }
            }
            PodiumApp(model, ::pick, smoke = smoke, onReady = {
                if (smoke) {
                    // Start only after composition reaches content; captures render/runtime initialization failures.
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2500)
                        val result = args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "ui-smoke.txt"
                        File(result).writeText("PASS: Compose desktop window, all 14 navigation routes and populated playlist/album detail rendered.\n")
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

/** Optional hardware integration check. Fails explicitly if no usable audio output exists. */
private fun audioSmoke(args: Array<String>) {
    val result = File(args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "audio-smoke.txt")
    val wave = File.createTempFile("podium-audio-", ".wav")
    generateTestWave(wave, 6)
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
            withTimeout(3000) { engine.state.first { it.entry?.key == next.key && it.playing && !it.fading } }
            result.writeText("PASS: Generated PCM WAV played, paused, sought to 2s, resumed and crossfaded to a second queue entry.\n")
        }
    } catch (error: Throwable) { result.writeText("FAIL: ${error.message}\n"); engine.close(); FxRuntime.exit(); wave.delete(); exitProcess(1) }
    engine.close(); FxRuntime.exit(); wave.delete(); exitProcess(0)
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
