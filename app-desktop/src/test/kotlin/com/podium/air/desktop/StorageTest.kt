package com.podium.air.desktop

import com.podium.air.domain.PlaybackQueue
import com.podium.air.domain.RepeatMode
import java.nio.file.Files
import java.io.File
import kotlin.test.*
class StorageTest {
    @Test fun persistenceRoundTripIncludesDuplicateQueueEntries() {
        val dir = Files.createTempDirectory("podium-store").toFile()
        try {
            val track = StoredTrack("a", "C:/Music/a.mp3", "A", "Artist", "Album")
            val q = PlaybackQueue.from(listOf(track.song(), track.song()), 1, RepeatMode.ALL)
            val state = SavedState(library = listOf(track), favorites = setOf("a"), playlists = listOf(Playlist(name = "Mix", tracks = listOf("a", "a"))), preferences = Preferences(crossfadeSeconds = 10), queue = q.entries.map { SavedQueueEntry(it.key, "a") }, cursor = 1, repeat = "ALL")
            StateStore(dir).save(state)
            val restored = StateStore(dir).load()
            assertEquals(state, restored)
            assertEquals(q.current?.key, restored.playbackQueue().current?.key)
            assertEquals(2, restored.playbackQueue().entries.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun corruptLibraryIsPreservedForRecovery() {
        val dir = Files.createTempDirectory("podium-corrupt").toFile()
        try {
            File(dir, "library.json").writeText("broken json")
            val store = StateStore(dir)
            assertEquals(SavedState(), store.load())
            assertNotNull(store.recoveryMessage)
            assertTrue(dir.listFiles()!!.any { it.name.startsWith("library-unreadable-") && it.readText() == "broken json" })
        } finally { dir.deleteRecursively() }
    }
    @Test fun filteredRestoreKeepsSelectedEntryByIdentity() {
        val track = StoredTrack("a", "a.wav", "A", "Artist", "Album")
        val restored = SavedState(library = listOf(track), queue = listOf(SavedQueueEntry("gone", "missing"), SavedQueueEntry("current", "a")), cursor = 1).playbackQueue()
        assertEquals("current", restored.current?.key); assertEquals(0, restored.cursor)
    }
    @Test fun validWaveImportsAndSidecarLyricsUseMilliseconds() {
        val dir = Files.createTempDirectory("podium-wave").toFile()
        try {
            val wave = File(dir, "test.wav"); generateTestWave(wave, 1)
            File(dir, "test.lrc").writeText("[offset:100]\n[00:00.50]First\n[00:00.900]Second")
            val library = LocalLibrary(dir)
            val imported = library.import(listOf(wave, wave))
            assertEquals(1, imported.first.size); assertTrue(imported.second.isEmpty())
            assertEquals(listOf(600L, 1000L), library.lyrics(imported.first.single().song()).map { it.timeMs })
        } finally { dir.deleteRecursively() }
    }
    @Test fun unsupportedAndMissingFilesAreReported() {
        val dir = Files.createTempDirectory("podium-invalid").toFile()
        try { assertEquals(2, LocalLibrary(dir).import(listOf(File(dir, "missing.mp3"), File(dir, "fake.flac"))).second.size) }
        finally { dir.deleteRecursively() }
    }
}
