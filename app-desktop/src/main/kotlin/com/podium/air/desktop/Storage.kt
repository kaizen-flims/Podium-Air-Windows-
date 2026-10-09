// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LyricLine
import com.podium.air.domain.PlaybackQueue
import com.podium.air.domain.QueueEntry
import com.podium.air.domain.RepeatMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@Serializable
data class StoredTrack(val id: String, val path: String, val title: String, val artist: String, val album: String, val duration: Long = 0, val artwork: String? = null) {
    fun song() = Song(id, title, artist, artwork?.let { File(it).toURI().toString() }, albumName = album,
        durationText = formatTime(duration), localPath = path, localUri = File(path).toURI().toString())
}
@Serializable
data class Playlist(val id: String = UUID.randomUUID().toString(), val name: String, val tracks: List<String> = emptyList())
@Serializable
data class Preferences(val dark: Boolean = true, val volume: Float = 0.8f, val crossfadeSeconds: Int = 0, val speed: Float = 1f, val equalizer: List<Double> = List(10) { 0.0 })
@Serializable
data class SavedQueueEntry(val key: String, val trackId: String)
@Serializable
data class SavedState(
    val schema: Int = 1,
    val library: List<StoredTrack> = emptyList(), val playlists: List<Playlist> = emptyList(),
    val favorites: Set<String> = emptySet(), val history: List<String> = emptyList(),
    val preferences: Preferences = Preferences(), val queue: List<SavedQueueEntry> = emptyList(),
    val cursor: Int = -1, val repeat: String = "OFF", val originalOrder: List<String>? = null,
) {
    fun playbackQueue(): PlaybackQueue {
        val known = library.associateBy { it.id }
        val selectedKey = queue.getOrNull(cursor)?.key
        val restored = queue.mapNotNull { saved -> known[saved.trackId]?.let { QueueEntry(saved.key, it.song()) } }
        val restoredIndex = restored.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 } ?: if (restored.isEmpty()) -1 else 0
        return PlaybackQueue(restored, restoredIndex, runCatching { RepeatMode.valueOf(repeat) }.getOrDefault(RepeatMode.OFF), originalOrder)
    }
}

interface StatePersistence { fun load(): SavedState; fun save(state: SavedState) }
class StateStore(val directory: File = defaultDataDirectory()) : StatePersistence {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(directory, "library.json")
    var recoveryMessage: String? = null
        private set
    @Synchronized override fun load(): SavedState {
        if (!file.exists()) return SavedState()
        return try {
            val state = json.decodeFromString<SavedState>(file.readText())
            require(state.schema == 1) { "Unsupported library schema ${state.schema}" }
            state.copy(preferences = state.preferences.copy(
                volume = state.preferences.volume.coerceIn(0f, 1f), crossfadeSeconds = state.preferences.crossfadeSeconds.coerceIn(0, 12),
                speed = state.preferences.speed.coerceIn(0.5f, 2f), equalizer = List(10) { state.preferences.equalizer.getOrElse(it) { 0.0 }.coerceIn(-12.0, 12.0) }))
        } catch (error: Exception) {
            val backup = File(directory, "library-unreadable-${System.currentTimeMillis()}.json")
            Files.copy(file.toPath(), backup.toPath())
            recoveryMessage = "Your saved library could not be read. A copy was preserved at ${backup.absolutePath}."
            SavedState()
        }
    }
    @Synchronized override fun save(state: SavedState) {
        directory.mkdirs()
        val temp = Files.createTempFile(directory.toPath(), "library-", ".tmp")
        try {
            Files.writeString(temp, json.encodeToString(SavedState.serializer(), state))
            try { Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }
}
fun defaultDataDirectory(): File = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "PodiumAirWindows")
fun formatTime(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }

/** Native formats plus bounded, background FLAC/Opus-to-PCM preparation. */
class LocalLibrary(private val directory: File = defaultDataDirectory()) {
    val extensions = setOf("mp3", "wav", "aif", "aiff", "m4a", "flac", "opus", "ogg")
    fun import(files: List<File>): Pair<List<StoredTrack>, List<String>> {
        val failures = mutableListOf<String>()
        val tracks = files.distinctBy { it.canonicalPath }.mapNotNull { file ->
            if (!file.isFile || file.extension.lowercase() !in extensions) { failures += "${file.name}: unsupported or missing file"; return@mapNotNull null }
            val id = UUID.nameUUIDFromBytes(file.canonicalPath.toByteArray()).toString()
            var title = file.nameWithoutExtension; var artist = "Unknown artist"; var album = "Local music"; var duration = 0L; var artwork: String? = null
            // Metadata failure never makes a playable WAV or other valid file disappear.
            runCatching {
                val audio = AudioFileIO.read(file)
                duration = audio.audioHeader.trackLength * 1000L
                val tag = audio.tag
                tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
                tag?.firstArtwork?.binaryData?.let { bytes ->
                    val artDir = File(directory, "artwork").apply { mkdirs() }
                    val image = File(artDir, "$id.image")
                    image.writeBytes(bytes); artwork = image.absolutePath
                }
            }
            StoredTrack(id, file.canonicalPath, title, artist, album, duration, artwork)
        }
        return tracks to failures
    }
    fun folder(folder: File): List<File> = folder.walkTopDown().onEnter { !it.isHidden }.filter { it.isFile && it.extension.lowercase() in extensions }.toList()
    fun lyrics(track: Song): List<LyricLine> {
        val audio = track.localPath?.let(::File) ?: return emptyList()
        val lrc = File(audio.parentFile, "${audio.nameWithoutExtension}.lrc")
        val text = if (lrc.isFile) lrc.readText() else runCatching { AudioFileIO.read(audio).tag?.getFirst(FieldKey.LYRICS) }.getOrNull().orEmpty()
        if (text.isBlank()) return emptyList()
        val enhanced = EnhancedLrc.parse(text)
        if (enhanced.isNotEmpty()) return enhanced
        val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val stamp = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")
        val synced = text.lineSequence().flatMap { row ->
            stamp.findAll(row).map { m ->
                val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                LyricLine((m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + fraction + offset).coerceAtLeast(0), row.replace(stamp, "").trim())
            }
        }.toList().sortedBy { it.timeMs }
        return synced.ifEmpty { text.lineSequence().filter { it.isNotBlank() && !it.startsWith("[") }.map { LyricLine(0, it.trim()) }.toList() }
    }
}
