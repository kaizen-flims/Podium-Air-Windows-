package com.podium.air.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ListeningAndPlaylistTest {
    @Test fun pausesSeeksAndDuplicateQueueEntriesHaveCorrectListeningTotals() {
        val recorder = ListeningRecorder()
        recorder.sample("a", "first", true, 0, "2026-10-09")
        recorder.sample("a", "first", true, 1000, "2026-10-09") // Seek position is deliberately absent from the API.
        recorder.sample("a", "first", false, 1500, "2026-10-09")
        recorder.sample("a", "first", false, 5500, "2026-10-09")
        recorder.sample("a", "first", true, 5500, "2026-10-09")
        recorder.sample("a", "duplicate", true, 6500, "2026-10-09")
        recorder.sample("b", "second", true, 7000, "2026-10-09")
        val values = recorder.drain().associateBy { it.trackId }
        assertEquals(3000L, values["a"]!!.milliseconds); assertEquals(2, values["a"]!!.plays)
        assertEquals(1, values["b"]!!.plays); assertTrue(recorder.drain().isEmpty())
    }
    @Test fun suspensionCannotInflateListeningAndMergingPreservesDays() {
        val recorder = ListeningRecorder()
        recorder.sample("a", "a", true, 0, "2026-10-09")
        recorder.sample("a", "a", false, 3600000, "2026-10-09")
        assertEquals(2000L, recorder.drain().single().milliseconds)
        val merged = mergeListening(listOf(ListeningEntry("a", "2026-10-08", 100, 1)), listOf(ListeningEntry("a", "2026-10-09", 200, 1), ListeningEntry("a", "2026-10-09", 300, 2)))
        assertEquals(2, merged.size); assertEquals(500L, merged[0].milliseconds); assertEquals(3, merged[0].plays)
    }
    @Test fun m3uRoundTripPreservesUnicodeOrderAndDuplicateEntries() {
        val dir = Files.createTempDirectory("podium-m3u").toFile()
        try {
            val tracks = listOf(StoredTrack("a", File(dir, "music/日本語.wav").absolutePath, "Title", "Artist", "Album"))
            val playlist = File(dir, "Mix.m3u8")
            PlaylistFiles.write(playlist, tracks + tracks)
            val imported = PlaylistFiles.read(playlist)
            assertTrue(imported.second.isEmpty()); assertEquals(listOf(File(tracks[0].path), File(tracks[0].path)), imported.first)
            assertFalse(playlist.readText().contains(dir.absolutePath), "Paths on the same volume should be portable")
        } finally { dir.deleteRecursively() }
    }
    @Test fun onlinePlaylistEntriesAreReportedAndNeverFetched() {
        val dir = Files.createTempDirectory("podium-m3u-invalid").toFile()
        try {
            val playlist = File(dir, "Mix.m3u8").apply { writeText("\uFEFF#EXTM3U\nhttps://example.com/music.mp3\nlocal.wav\n") }
            val result = PlaylistFiles.read(playlist)
            assertEquals(1, result.first.size); assertEquals(1, result.second.size)
            assertEquals(File(dir, "local.wav"), result.first.single())
        } finally { dir.deleteRecursively() }
    }
}
