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
    @Volatile var closed = false
    private var session = 0L
    override fun open(entry: QueueEntry, play: Boolean) { state.value = AudioState(entry, playing = play, durationMs = 10000, session = ++session) }
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
    @Test fun systemMediaCommandsControlTheSelectedQueueWithoutReopeningPlayingTracks() {
        val audio = FakeAudio(); val store = MemoryState(); val model = DesktopModel(audio, store, dispatcher = Dispatchers.Unconfined)
        try {
            model.play(store.value.library.map { it.song() })
            val original = audio.state.value.entry!!.key
            handleMediaCommand(model, "PLAY"); assertEquals(original, audio.state.value.entry!!.key)
            handleMediaCommand(model, "PAUSE"); assertFalse(audio.state.value.playing)
            handleMediaCommand(model, "PLAY"); assertTrue(audio.state.value.playing)
            handleMediaCommand(model, "SEEK\t2000"); assertEquals(2000, audio.state.value.positionMs)
            handleMediaCommand(model, "SEEK\tinvalid"); assertEquals(2000, audio.state.value.positionMs)
            handleMediaCommand(model, "NEXT"); assertEquals("b", audio.state.value.entry!!.song.videoId)
            handleMediaCommand(model, "STOP"); assertNull(audio.state.value.entry)
            handleMediaCommand(model, "PLAY"); assertEquals("b", audio.state.value.entry!!.song.videoId)
            assertEquals("506f6469756d20e29da4", mediaText("Podium ❤"))
        } finally { model.close() }
    }
    @Test fun repeatsAndCompletedRestartsCountButPauseResumeDoesNot() {
        val audio = FakeAudio(); val store = MemoryState(); val model = DesktopModel(audio, store, dispatcher = Dispatchers.Unconfined)
        try {
            model.play(listOf(store.value.library.first().song()))
            val key = model.queue.value.current!!.key
            audio.pause(); model.toggle(); audio.pause()
            assertEquals(1, model.state.value.listening.sumOf { it.plays })
            while (model.queue.value.repeat != com.podium.air.domain.RepeatMode.ONE) model.repeat()
            audio.onEnd(key); audio.pause()
            assertEquals(key, model.queue.value.current!!.key)
            assertEquals(2, model.state.value.listening.sumOf { it.plays })
            audio.state.value = audio.state.value.copy(playing = false, positionMs = audio.state.value.durationMs, completed = true)
            model.previous() // Rewind after completion must still begin a new listening session on Play.
            model.toggle(); audio.pause()
            assertEquals(3, model.state.value.listening.sumOf { it.plays })
            assertEquals(0L, audio.state.value.positionMs)
        } finally { model.close() }
    }
    @Test fun shutdownFinalSnapshotWinsOverAnInFlightCancelledSave() {
        val initial = MemoryState().value
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val writes = java.util.Collections.synchronizedList(mutableListOf<SavedState>())
        val persistence = object : StatePersistence {
            override fun load() = initial
            override fun save(state: SavedState) {
                if (calls.incrementAndGet() == 1) {
                    started.countDown()
                    check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                }
                writes += state
            }
        }
        val audio = FakeAudio(); val model = DesktopModel(audio, persistence, dispatcher = Dispatchers.Unconfined)
        var closer: Thread? = null
        try {
            model.createPlaylist("Before shutdown")
            assertTrue(started.await(3, java.util.concurrent.TimeUnit.SECONDS), "The background save must have started")
            model.favorite("a")
            closer = Thread { model.close() }.apply { start() }
            val deadline = System.nanoTime() + 2_000_000_000
            while (!audio.closed && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue(audio.closed)
            release.countDown(); closer.join(3000)
            assertFalse(closer.isAlive, "Shutdown must finish after the pending disk write")
            assertTrue("a" in writes.last().favorites, "An older snapshot must not overwrite the shutdown snapshot")
            assertFalse(model.message.value.orEmpty().contains("Could not save"), "Normal save cancellation is not an error")
        } finally { release.countDown(); closer?.join(3000); model.close() }
    }
    @Test fun lateCrossfadeOfRemovedEntryCannotReplaceTheCurrentSelection() {
        val audio = FakeAudio(); val store = MemoryState(); val model = DesktopModel(audio, store, dispatcher = Dispatchers.Unconfined)
        try {
            model.play(store.value.library.map { it.song() })
            val current = model.queue.value.current!!; val removed = audio.next!!
            model.removeQueue(removed.key)
            audio.state.value = AudioState(removed, playing = true, positionMs = 100, fading = true)
            audio.onAdvance(removed)
            assertEquals(current.key, model.queue.value.current?.key)
            assertEquals(current.key, audio.state.value.entry?.key)
            val session = audio.state.value.session
            audio.onAdvance(removed) // A second stale callback must not restart the reconciled player.
            assertEquals(session, audio.state.value.session)
        } finally { model.close() }
    }
    @Test fun olderEndCallbackCannotAdvanceAManuallyRestartedQueueEntry() {
        val tasks = java.util.concurrent.ConcurrentLinkedQueue<Runnable>()
        val dispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks.add(block) }
        }
        val audio = FakeAudio(); val store = MemoryState(); val model = DesktopModel(audio, store, dispatcher = dispatcher)
        try {
            model.play(store.value.library.map { it.song() })
            val key = model.queue.value.current!!.key
            audio.onEnd(key) // The old end waits for the UI thread while the user restarts this entry.
            model.selectQueue(0)
            val restartedSession = audio.state.value.session
            while (true) { val task = tasks.poll() ?: break; task.run() }
            assertEquals(key, model.queue.value.current?.key)
            assertEquals(restartedSession, audio.state.value.session)
            assertEquals("a", audio.state.value.entry?.song?.videoId)
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
