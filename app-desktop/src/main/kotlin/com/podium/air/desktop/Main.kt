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
    if (args.contains("--replay-smoke")) { replaySmoke(args); return }
    if (args.contains("--automix-smoke")) { automixSmoke(args); return }
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
        val pickerScope = rememberCoroutineScope()
        var pickerActive by remember { mutableStateOf(false) }
        val windows = System.getProperty("os.name").startsWith("Windows")
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val windowState = rememberWindowState(placement = if (args.contains("--small")) WindowPlacement.Floating else WindowPlacement.Maximized,
            position = WindowPosition(Alignment.Center), width = minOf(760, bounds.width - 24).coerceAtLeast(360).dp, height = minOf(560, bounds.height - 24).coerceAtLeast(280).dp)
        fun shutdown() { pickerScope.cancel(); tray?.let { SystemTray.getSystemTray().remove(it) }; mediaControls?.close(); model.close(); FxRuntime.exit(); exitApplication() }
        fun pick(folder: Boolean) {
            if (windows) {
                if (pickerActive) return
                pickerActive = true
                pickerScope.launch {
                    try {
                        val selected = WindowsFilePicker.choose(if (folder) "--pick-folder" else "--pick-files")
                        if (selected.isNotEmpty()) model.importFiles(selected, folder)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { model.message.value = "File selection failed: ${error.message}" }
                    finally { pickerActive = false }
                }
                return
            }
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
            if (windows) {
                if (pickerActive) return
                pickerActive = true
                pickerScope.launch {
                    try {
                        val filename = playlist?.name?.replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "_")?.plus(".m3u8").orEmpty()
                        val selected = WindowsFilePicker.choose(if (playlist == null) "--pick-playlist" else "--save-playlist", filename).firstOrNull()
                        if (selected != null) { if (playlist == null) model.importPlaylist(selected) else model.exportPlaylist(playlist.id, selected) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { model.message.value = "Playlist selection failed: ${error.message}" }
                    finally { pickerActive = false }
                }
                return
            }
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
        result.writeText("")
        for (mode in listOf("--self-test", "--picker-self-test")) {
            val process = ProcessBuilder(extractMediaBridge(directory).absolutePath, mode).redirectErrorStream(true).start()
            if (!process.waitFor(20000, java.util.concurrent.TimeUnit.MILLISECONDS)) { process.destroyForcibly(); error("Packaged Windows helper timed out: $mode") }
            val text = process.inputStream.bufferedReader().readText()
            result.appendText(text)
            check(process.exitValue() == 0 && text.contains("PASS:")) { text }
        }
        runBlocking {
            val fixture = File(directory, "é音.wav"); generateTestWave(fixture, 1)
            check(WindowsFilePicker.chooseForSmoke("--pick-files", fixture.name, directory, 1).singleOrNull()?.let { java.nio.file.Files.isSameFile(it.toPath(), fixture.toPath()) } == true) { "JVM picker returned the wrong Unicode file." }
            check(WindowsFilePicker.chooseForSmoke("--pick-folder", "", directory, 1).singleOrNull()?.let { java.nio.file.Files.isSameFile(it.toPath(), directory.toPath()) } == true) { "JVM picker returned the wrong folder." }
            check(WindowsFilePicker.chooseForSmoke("--save-playlist", "é音.m3u8", directory, 1).singleOrNull()?.let { it.name == "é音.m3u8" && java.nio.file.Files.isSameFile(it.parentFile.toPath(), directory.toPath()) } == true) { "JVM save picker returned the wrong path." }
            check(WindowsFilePicker.chooseForSmoke("--save-image", "é音.png", directory, 1).singleOrNull()?.let { it.name == "é音.png" && java.nio.file.Files.isSameFile(it.parentFile.toPath(), directory.toPath()) } == true) { "JVM PNG picker returned the wrong path." }
            check(WindowsFilePicker.chooseForSmoke("--pick-files", "", directory, 2).isEmpty()) { "Cancelled picker returned a selection." }
            val pending = launch { WindowsFilePicker.chooseForSmoke("--pick-files", "", directory, 0) }
            delay(750); pending.cancelAndJoin()
            withTimeout(5000) {
                while (ProcessHandle.current().children().use { children -> children.anyMatch { it.isAlive && it.info().command().orElse("").contains("PodiumMediaBridge", ignoreCase = true) } }) delay(50)
            }
            result.appendText("PASS: Packaged JVM/native picker protocol round-tripped Unicode file/folder/save selections, cancellation returned no paths, and cancelling an open picker left no helper process.\n")
        }
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
            // Automix's decoder must work for each actual codec fixture, not just WAV.
            val pcm = engine.mediaFiles.preparePcm(wave) { ensureActive() }
            javax.sound.sampled.AudioSystem.getAudioInputStream(pcm).use { input ->
                check(input.format.sampleSizeInBits == 16 && !input.format.isBigEndian && input.format.channels in 1..2)
                val pcmDuration = input.frameLength * 1000.0 / input.format.sampleRate
                check(pcmDuration >= 5000 && (tracks[0].duration <= 0 || kotlin.math.abs(pcmDuration - tracks[0].duration) < 2000)) { "Decoded PCM duration differs from the recording." }
                val bytes = ByteArray(8192); var energy = 0L
                while (true) {
                    ensureActive(); val count = input.read(bytes); if (count < 0) break
                    for (i in 0 until count - 1 step 2) energy += kotlin.math.abs(((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()).toLong()
                }
                check(energy > 0) { "Codec PCM conversion produced silence." }
            }
            val first = com.podium.air.domain.QueueEntry(song = tracks[0].song())
            val next = com.podium.air.domain.QueueEntry(song = tracks[0].song())
            val advanced = CompletableDeferred<Unit>()
            val ended = CompletableDeferred<Unit>()
            engine.onEnd = { if (it == next.key) ended.complete(Unit) }
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
            withTimeout(12000) { ended.await() }
            check(!engine.state.value.playing) { "Playback remained active after end of track." }
            if (supplied == null) {
                engine.open(first.copy(song = first.song.copy(localPath = File(wave.parentFile, "missing-${System.nanoTime()}.wav").absolutePath)))
                withTimeout(5000) { engine.state.first { it.error != null && !it.loading && !it.playing } }
                engine.open(first, play = false)
                withTimeout(15000) { engine.state.first { !it.loading && it.entry?.key == first.key && it.error == null } }
            }
            result.writeText("PASS: ${wave.extension.uppercase()} played, paused, sought to 2s, resumed, crossfaded to a second queue entry and reached natural end. Automix PCM conversion passed duration, format and non-silence checks.${if (supplied == null) " Missing-file error and subsequent recovery also passed." else ""}\n")
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

/** Packaged native measurements feed a real two-player PCM transition, then pause/seek/end/cleanup. */
private fun automixSmoke(args: Array<String>) {
    val result = File(args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "automix-smoke.txt")
    val directory = java.nio.file.Files.createTempDirectory("podium-automix-smoke-").toFile()
    val firstFile = File(directory, "Measured track one.wav")
    val secondFile = File(directory, "Measured track two.wav")
    fun beatWave(file: File, bpm: Double) {
        val rate = 22050
        PcmWaveWriter(file, rate, 1).use { wave ->
            val bytes = ByteArray(rate * 60 * 2)
            for (i in 0 until rate * 60) {
                val t = i / rate.toDouble(); val beat = (t - 2).mod(60 / bpm)
                val sound = if (t < 2 || t > 57) 0.0 else 0.05 * kotlin.math.sin(t * 2 * Math.PI * 220) + if (beat < 0.055) 0.65 * kotlin.math.exp(-beat * 70) * kotlin.math.sin(beat * 2 * Math.PI * 90) else 0.0
                val value = (sound * 15000).toInt().coerceIn(-32768, 32767)
                bytes[i * 2] = value.toByte(); bytes[i * 2 + 1] = (value shr 8).toByte()
            }
            wave.write(bytes)
        }
    }
    val engine = JavaFxAudioEngine()
    try {
        beatWave(firstFile, 120.0); beatWave(secondFile, 122.0)
        runBlocking {
            val tracks = LocalLibrary(File(directory, "art")).import(listOf(firstFile, secondFile)).first
            check(tracks.size == 2)
            val first = com.podium.air.domain.QueueEntry(song = tracks[0].song())
            val next = com.podium.air.domain.QueueEntry(song = tracks[1].song())
            val advanced = CompletableDeferred<Unit>(); val ended = CompletableDeferred<Unit>()
            engine.onAdvance = { if (it.key == next.key) advanced.complete(Unit) }
            engine.onEnd = { if (it == next.key) ended.complete(Unit) }
            engine.configure(Preferences(automix = true, crossfadeSeconds = 0, volume = 0.2f))
            engine.open(first, play = false)
            withTimeout(15000) { engine.state.first { it.error != null || !it.loading && it.durationMs > 50000 } }.let { check(it.error == null) { it.error.orEmpty() } }
            engine.seek(40000)
            withTimeout(3000) { engine.state.first { it.positionMs in 39800..40200 } }
            engine.setUpcoming(next)
            withTimeout(5000) { engine.state.first { it.automixStatus?.startsWith("Analyzing") == true } }
            // Replace an in-flight preparation, then seek with the same upcoming key.
            // Cancelled native/model work must not install a stale standby player.
            engine.setUpcoming(null); engine.setUpcoming(next); engine.seek(40000); engine.setUpcoming(next)
            withTimeout(90000) { engine.state.first { it.automixStatus?.startsWith("Automix ready") == true || it.automixStatus?.startsWith("Automix uses standard") == true } }.let {
                check(it.automixStatus?.startsWith("Automix ready") == true) { it.automixStatus.orEmpty() }
                check(it.automixStatus?.contains("open-unmix vocal mask") == true) { "The packaged vocal model did not contribute measured evidence: ${it.automixStatus}" }
            }
            engine.toggle()
            withTimeout(25000) { advanced.await() }
            withTimeout(4000) { engine.state.first { it.entry?.key == next.key && it.playing && it.fading } }
            engine.configure(Preferences(automix = true, crossfadeSeconds = 0, volume = 0.1f))
            val stillFading = CompletableDeferred<Boolean>()
            FxRuntime.dispatch { stillFading.complete(engine.state.value.fading) }
            check(stillFading.await()) { "Changing volume cancelled an enabled Automix with manual crossfade off." }
            engine.pause(); withTimeout(3000) { engine.state.first { !it.playing } }
            engine.seek(10000)
            withTimeout(15000) { engine.state.first { it.error != null || !it.loading && it.positionMs in 9800..10200 } }.let { check(it.error == null) { it.error.orEmpty() } }
            check(!engine.state.value.playing) { "Seeking while paused started playback." }
            engine.configure(Preferences(automix = false, volume = 0.2f)); engine.toggle()
            withTimeout(5000) { engine.state.first { it.playing } }
            engine.seek(59400); withTimeout(5000) { ended.await() }
            check(!engine.state.value.playing)
            result.writeText("PASS: Packaged native DSP and original planner prepared an actual PCM Automix transition; two real players overlapped, pause/seek restored the original track clock, disabling Automix worked and the incoming track reached natural end. Pitch-preserving WSOLA is verified separately by measured PCM tests.\n")
        }
    } catch (error: Throwable) { result.writeText("FAIL: " + error.message + "\n"); error.printStackTrace(); engine.close(); FxRuntime.exit(); directory.deleteRecursively(); exitProcess(1) }
    engine.close(); FxRuntime.exit(); directory.deleteRecursively(); exitProcess(0)
}


/** The packaged app renders and saves a real Replay PNG through its native save dialog. */
private fun replaySmoke(args: Array<String>) {
    val result = File(args.firstOrNull { it.startsWith("--result=") }?.substringAfter("=") ?: "replay-smoke.txt")
    val imagePath = File(args.firstOrNull { it.startsWith("--image=") }?.substringAfter("=") ?: "replay-smoke.png")
    val directory = java.nio.file.Files.createTempDirectory("podium-replay-smoke-").toFile()
    var image: java.awt.image.BufferedImage? = null
    try {
        val today = java.time.LocalDate.now()
        val state = SavedState(library = listOf(StoredTrack("a", "a.wav", "Native Replay 音楽", "Podium Air", "Test album")), listening = listOf(ListeningEntry("a", today.toString(), 1_800_000, 10)))
        val summary = ReplaySummary.from(state, 30, today)
        image = renderReplayPoster(summary)
        val target = runBlocking { WindowsFilePicker.chooseForSmoke("--save-image", "Replay 音楽.png", directory, 1).single() }
        writeReplayPoster(image, target)
        ImageIO.read(target).let { decoded -> check(decoded.width == 1080 && decoded.height == 1920); decoded.flush() }
        target.copyTo(imagePath, overwrite = true)
        result.writeText("PASS: Packaged app rendered the actual listening summary, saved a 1080x1920 PNG through the native Unicode save dialog, and reopened the exported image.\n")
    } catch (error: Throwable) { result.writeText("FAIL: ${error.message}\n"); error.printStackTrace(); image?.flush(); directory.deleteRecursively(); exitProcess(1) }
    image?.flush(); directory.deleteRecursively(); exitProcess(0)
}
