// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.Song
import com.podium.air.domain.PlaybackQueue
import com.podium.air.domain.QueueEntry
import com.podium.air.domain.RepeatMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** UI commands execute on Swing's EDT; playback callbacks are marshalled back to it. */
class DesktopModel(
    val engine: AudioEngine,
    private val persistence: StatePersistence = StateStore(),
    val localLibrary: LocalLibrary = LocalLibrary(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val saveMutex = Mutex()
    private val mutable = MutableStateFlow(persistence.load())
    val state: StateFlow<SavedState> = mutable
    private val queueMutable = MutableStateFlow(mutable.value.playbackQueue())
    val queue: StateFlow<PlaybackQueue> = queueMutable
    val message = MutableStateFlow<String?>((persistence as? StateStore)?.recoveryMessage)
    val importing = MutableStateFlow(false)
    val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val sleepRemaining = MutableStateFlow<Long?>(null)
    val platformStatus = MutableStateFlow("Windows media controls initialize when the window opens.")
    private var saveJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepJob: Job? = null
    init {
        engine.configure(mutable.value.preferences)
        engine.onEnd = { key -> scope.launch {
            if (queue.value.current?.key == key) {
                val nextIndex = queue.value.nextIndex(automatic = true)
                if (nextIndex != null) playQueue(queue.value.select(nextIndex))
            }
        } }
        engine.onAdvance = { entry -> scope.launch {
            val index = queue.value.entries.indexOfFirst { it.key == entry.key }
            if (index >= 0) { updateQueue(queue.value.select(index)); record(entry.song); loadLyrics(entry.song); primeNext() }
        } }
        // Restore selection and queue without unexpectedly starting music.
        queue.value.current?.let { engine.open(it, play = false); loadLyrics(it.song); primeNext() }
    }
    fun importFiles(files: List<File>, folder: Boolean = false) {
        if (importing.value) return
        scope.launch {
            importing.value = true
            try {
                val (newTracks, failures) = withContext(Dispatchers.IO) {
                    val candidates = if (folder) files.flatMap(localLibrary::folder) else files
                    localLibrary.import(candidates)
                }
                change { copy(library = (library + newTracks).associateBy { it.id }.values.toList()) }
                message.value = if (failures.isEmpty()) "Imported ${newTracks.size} tracks." else "Imported ${newTracks.size} tracks. ${failures.take(4).joinToString("; ")}"
            } catch (error: Exception) { message.value = "Import failed: ${error.message}" }
            finally { importing.value = false }
        }
    }
    fun play(songs: List<Song>, index: Int = 0) { if (songs.isNotEmpty()) playQueue(PlaybackQueue.from(songs, index, queue.value.repeat)) }
    fun playQueue(value: PlaybackQueue) { updateQueue(value); value.current?.let { engine.open(it); record(it.song); loadLyrics(it.song) }; primeNext() }
    fun selectQueue(index: Int) = playQueue(queue.value.select(index))
    fun toggle() {
        val current = queue.value.current ?: return
        if (engine.state.value.entry == null || engine.state.value.error != null) { engine.open(current); record(current.song) } else engine.toggle()
    }
    fun next() { val index = queue.value.nextIndex() ?: return; playQueue(queue.value.select(index)) }
    fun previous() { if (engine.state.value.positionMs > 3000) engine.seek(0) else playQueue(queue.value.previous()) }
    fun seek(ms: Long) = engine.seek(ms)
    fun enqueue(song: Song, next: Boolean = false) { updateQueue(queue.value.append(song, next)); primeNext() }
    fun removeQueue(key: String) {
        val current = queue.value.current?.key
        val result = queue.value.remove(key)
        updateQueue(result)
        if (current != result.current?.key) { if (result.current == null) engine.stop() else playQueue(result) }
        primeNext()
    }
    fun moveQueue(key: String, offset: Int) { updateQueue(queue.value.move(key, offset)); primeNext() }
    fun shuffle() { updateQueue(queue.value.toggleShuffle()); primeNext() }
    fun repeat() { updateQueue(queue.value.copy(repeat = RepeatMode.entries[(queue.value.repeat.ordinal + 1) % 3])); primeNext() }
    private fun primeNext() {
        val q = queue.value
        // A repeated single track restarts at its end; it must not crossfade into itself.
        engine.setUpcoming(q.nextIndex(automatic = true)?.let { q.entries[it] }?.takeUnless { it.key == q.current?.key })
    }
    private fun updateQueue(value: PlaybackQueue) {
        queueMutable.value = value
        change { copy(queue = value.entries.map { SavedQueueEntry(it.key, it.song.videoId) }, cursor = value.cursor, repeat = value.repeat.name, originalOrder = value.originalOrder) }
    }
    private fun record(song: Song) { change { copy(history = (listOf(song.videoId) + history.filterNot { it == song.videoId }).take(200)) } }
    private fun loadLyrics(song: Song) {
        lyricsJob?.cancel(); lyrics.value = emptyList()
        lyricsJob = scope.launch { lyrics.value = withContext(Dispatchers.IO) { runCatching { localLibrary.lyrics(song) }.getOrElse { emptyList() } } }
    }
    fun favorite(id: String) { change { copy(favorites = if (id in favorites) favorites - id else favorites + id) } }
    fun createPlaylist(name: String) { val trimmed = name.trim(); if (trimmed.isNotEmpty()) change { copy(playlists = playlists + Playlist(name = trimmed)) } }
    fun renamePlaylist(id: String, name: String) { if (name.isNotBlank()) change { copy(playlists = playlists.map { if (it.id == id) it.copy(name = name.trim()) else it }) } }
    fun deletePlaylist(id: String) { change { copy(playlists = playlists.filterNot { it.id == id }) } }
    fun addToPlaylist(playlistId: String, trackId: String) { change { copy(playlists = playlists.map { if (it.id == playlistId) it.copy(tracks = it.tracks + trackId) else it }) } }
    fun removeFromPlaylist(playlistId: String, index: Int) { change { copy(playlists = playlists.map { if (it.id == playlistId) it.copy(tracks = it.tracks.filterIndexed { at, _ -> at != index }) else it }) } }
    fun moveInPlaylist(playlistId: String, index: Int, offset: Int) { change { copy(playlists = playlists.map { p ->
        if (p.id == playlistId && index in p.tracks.indices) p.copy(tracks = p.tracks.toMutableList().apply { add((index + offset).coerceIn(indices), removeAt(index)) }) else p
    }) } }
    fun removeTrack(id: String) {
        queue.value.entries.filter { it.song.videoId == id }.forEach { removeQueue(it.key) }
        change { copy(library = library.filterNot { it.id == id }, favorites = favorites - id, history = history - id,
            playlists = playlists.map { it.copy(tracks = it.tracks.filterNot { track -> track == id }) }) }
    }
    fun preferences(value: Preferences) {
        if (value.launchAtStartup != state.value.preferences.launchAtStartup) {
            try { WindowsStartup.set(value.launchAtStartup) }
            catch (error: Exception) { message.value = error.message; return }
        }
        change { copy(preferences = value) }; engine.configure(value)
    }
    fun sleepTimer(minutes: Int?) {
        sleepJob?.cancel(); sleepRemaining.value = minutes?.times(60L)
        if (minutes != null) sleepJob = scope.launch {
            while ((sleepRemaining.value ?: 0) > 0) { delay(1000); sleepRemaining.value = (sleepRemaining.value ?: 1) - 1 }
            engine.pause(); sleepRemaining.value = null; message.value = "Sleep timer paused playback."
        }
    }
    private fun change(transform: SavedState.() -> SavedState) { mutable.value = mutable.value.transform(); scheduleSave() }
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(250)
            try { saveMutex.withLock { withContext(Dispatchers.IO) { persistence.save(mutable.value) } } }
            catch (error: Exception) { message.value = "Could not save your library: ${error.message}" }
        }
    }
    override fun close() {
        scope.cancel(); engine.close()
        // Flush the final snapshot before process shutdown; StateStore serializes with an in-flight save.
        runCatching { persistence.save(mutable.value) }.onFailure { System.err.println("Library save failed: ${it.message}") }
    }
}
