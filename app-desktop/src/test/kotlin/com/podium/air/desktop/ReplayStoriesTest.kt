// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.awt.image.BufferedImage
import kotlin.test.*

class ReplayStoriesTest {
    private val library = listOf(
        StoredTrack("a", "a.wav", "雨 🎵", "First artist", "Shared title", albumArtist = "First artist", genre = "Jazz"),
        StoredTrack("b", "b.wav", "Second track", "Second artist", "Shared title", albumArtist = "Second artist", genre = "Rock"),
    )
    @Test fun calendarRangesExcludeOtherMonthsYearsFutureAndMalformedDates() {
        val state = SavedState(library = library, listening = listOf(
            ListeningEntry("a", "2025-12-31", 1000, 1), ListeningEntry("b", "2026-09-30", 2000, 2),
            ListeningEntry("a", "2026-10-01", 3000, 3), ListeningEntry("b", "2026-10-10", 4000, 4),
            ListeningEntry("a", "2026-10-11", 5000, 5), ListeningEntry("b", "2026-09-31", 6000, 6)))
        val today = LocalDate.of(2026, 10, 10)
        val month = ReplaySummary.from(state, ReplayPeriod.THIS_MONTH, today)
        assertEquals(7000L, month.milliseconds); assertEquals(7, month.plays); assertEquals("2026-10", month.fileLabel)
        assertEquals(9000L, ReplaySummary.from(state, ReplayPeriod.THIS_YEAR, today).milliseconds)
        assertEquals(10_000L, ReplaySummary.from(state, ReplayPeriod.ALL_TIME, today).milliseconds)
        assertEquals(4000L, ReplaySummary.from(state, 7, today).milliseconds)
    }
    @Test fun allStoryCardsRenderMeasuredRankingsWithDistinctAlbumsAndGenres() {
        val summary = ReplaySummary.from(SavedState(library = library, listening = listOf(
            ListeningEntry("a", "2026-10-10", 120_000, 2, mapOf(9 to 120_000)),
            ListeningEntry("b", "2026-10-10", 60_000, 1, mapOf(20 to 60_000)))), ReplayPeriod.THIS_MONTH, LocalDate.of(2026, 10, 10))
        assertEquals(8, summary.storyPages().size); assertEquals(9, summary.peakHour())
        assertEquals(2, summary.groupRows(ReplayStoryPage.ALBUMS).size)
        assertEquals("Jazz", summary.groupRows(ReplayStoryPage.GENRES).first().title)
        assertEquals(2, summary.songRows().first().starts)
        val fingerprints = mutableSetOf<Long>()
        for (page in ReplayStoryPage.entries) {
            val image = renderReplayPoster(summary, page)
            try {
                assertEquals(1080, image.width); assertEquals(1920, image.height)
                var hash = 0L
                for (y in 100 until 1800 step 17) for (x in 50 until 1030 step 19) hash = hash * 31 + image.getRGB(x, y)
                fingerprints += hash
                assertTrue(hasBrightPixels(image), "$page has no readable foreground")
            } finally { image.flush() }
        }
        assertEquals(8, fingerprints.size)
        val old = ReplaySummary.from(SavedState(library = library, listening = listOf(ListeningEntry("a", "2026-10-10", 1000, 1))), ReplayPeriod.ALL_TIME, LocalDate.of(2026, 10, 10))
        assertNull(old.peakHour()); assertFalse(ReplayStoryPage.GENRES in ReplaySummary.from(SavedState(), ReplayPeriod.ALL_TIME).storyPages())
    }
    private fun hasBrightPixels(image: BufferedImage): Boolean {
        for (y in 450 until 1750 step 3) for (x in 70 until 1010 step 3) {
            val rgb = image.getRGB(x, y)
            if (((rgb shr 16) and 255) > 220 && ((rgb shr 8) and 255) > 220 && (rgb and 255) > 220) return true
        }
        return false
    }
    @Test fun listeningHoursCountAudibleIntervalsAndOldStateRemainsReadable() {
        val recorder = ListeningRecorder()
        recorder.sample("a", "entry", true, 0, "2026-10-10", 9)
        recorder.sample("a", "entry", true, 1000, "2026-10-10", 10)
        recorder.sample("a", "entry", false, 2000, "2026-10-10", 10)
        recorder.sample("a", "entry", false, 12_000, "2026-10-10", 10)
        val entry = recorder.drain().single()
        assertEquals(2000L, entry.milliseconds); assertEquals(mapOf(9 to 1000L, 10 to 1000L), entry.hourMilliseconds)
        val old = Json.decodeFromString<ListeningEntry>("{\"trackId\":\"a\",\"day\":\"2026-10-10\",\"milliseconds\":1000,\"plays\":1}")
        assertTrue(old.hourMilliseconds.isEmpty())
        assertEquals(mapOf(9 to 1000L, 10 to 1000L), mergeListening(listOf(old), listOf(entry)).single().hourMilliseconds)
    }
}
