package com.podium.air.desktop

import com.podium.air.domain.QueueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.test.*

private class MemoryState : StatePersistence {
    var value = SavedState(library = listOf(StoredTrack("a", "a.wav", "A", "Artist", "Album"), StoredTrack("b", "b.wav", "B", "Artist", "Album")))
    override fun load() = value
    override fun save(state: SavedState) { value = state }
}
private class FakeAudio : AudioEngine {
    override val state = MutableStateFlow(AudioState())
    override var onEnd: (String) -> Unit = {}
    override var onAdvance: (QueueEntry) -> Unit = {}
    var next: QueueEntry? = null
    var closed = false
    override fun open(entry: QueueEntry, play: Boolean) { state.value = AudioState(entry, playing = play, durationMs = 10000) }
    override fun setUpcoming(entry: QueueEntry?) { next = entry }
    override fun toggle() { state.value = state.value.copy(playing = !state.value.playing) }
    override fun pause() { state.value = state.value.copy(playing = false) }
    override fun seek(ms: Long) { state.value = state.value.copy(positionMs = ms.coerceIn(0, state.value.durationMs)) }
    override fun configure(preferences: Preferences) {}
    override fun stop() { state.value = AudioState() }
    override fun close() { closed = true }
}
class PlaybackModelTest {
    @Test fun playPauseSeekNextAndEndAdvanceUseRealQueueState() {
        val audio = FakeAudio(); val store = MemoryState()
        val model = DesktopModel(audio, store, LocalLibrary(File(System.getProperty("java.io.tmpdir"))), Dispatchers.Unconfined)
        try {
            model.play(store.value.library.map { it.song() })
            assertEquals("a", audio.state.value.entry?.song?.videoId); assertTrue(audio.state.value.playing)
            model.toggle(); assertFalse(audio.state.value.playing)
            model.seek(2000); assertEquals(2000, audio.state.value.positionMs)
            model.toggle(); assertTrue(audio.state.value.playing)
            val key = audio.state.value.entry!!.key
            audio.onEnd(key)
            assertEquals("b", audio.state.value.entry?.song?.videoId)
            audio.onEnd(key) // stale callback from the disposed first player must not advance again
            assertEquals("b", audio.state.value.entry?.song?.videoId)
            model.close(); assertTrue(audio.closed)
            assertEquals(listOf("b", "a"), store.value.history)
        } finally { model.close() }
    }
    @Test fun deletingCurrentTrackRemovesReferencesAndStopsEmptyQueue() {
        val audio = FakeAudio(); val store = MemoryState(); val model = DesktopModel(audio, store, dispatcher = Dispatchers.Unconfined)
        try {
            model.createPlaylist("Mix"); val playlist = model.state.value.playlists.single()
            model.addToPlaylist(playlist.id, "a"); model.addToPlaylist(playlist.id, "a"); model.favorite("a")
            model.play(listOf(store.value.library[0].song())); model.removeTrack("a")
            assertNull(audio.state.value.entry); assertTrue(model.queue.value.entries.isEmpty())
            assertTrue(model.state.value.playlists.single().tracks.isEmpty()); assertTrue(model.state.value.favorites.isEmpty())
        } finally { model.close() }
    }
    @Test fun crossfadeAdvanceDoesNotReopenIncomingPlayer() {
        val audio = FakeAudio(); val store = MemoryState(); val model = DesktopModel(audio, store, dispatcher = Dispatchers.Unconfined)
        try {
            model.play(store.value.library.map { it.song() })
            val incoming = audio.next!!
            audio.state.value = AudioState(incoming, playing = true, positionMs = 800, fading = true)
            audio.onAdvance(incoming)
            assertEquals(incoming.key, model.queue.value.current?.key)
            assertEquals(800, audio.state.value.positionMs)
        } finally { model.close() }
    }
}
