// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import java.nio.file.Files
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.*

class ReplayExportTest {
    @Test fun replayUsesTheSelectedPeriodAndExportsARealFullSizePng() {
        val today = LocalDate.of(2026, 10, 10)
        val state = SavedState(library = listOf(StoredTrack("a", "a.wav", "Unicode 音楽 🎵", "Prem", "Album")), listening = listOf(
            ListeningEntry("a", "2026-10-10", 180000, 2),
            ListeningEntry("a", "2026-10-05", 60000, 1),
            ListeningEntry("a", "2026-09-10", 900000, 6),
            ListeningEntry("a", "2026-10-11", 900000, 6),
        ))
        val summary = ReplaySummary.from(state, 7, today)
        assertEquals(240000L, summary.milliseconds); assertEquals(3, summary.plays); assertEquals(1, summary.ranked.size)
        val image = renderReplayPoster(summary)
        val directory = Files.createTempDirectory("podium-replay-").toFile()
        try {
            val file = directory.resolve("Replay 音楽.png")
            writeReplayPoster(image, file)
            assertTrue(file.length() > 20000)
            val decoded = assertNotNull(ImageIO.read(file))
            assertEquals(1080, decoded.width); assertEquals(1920, decoded.height)
            val varied = (72..1008 step 16).flatMap { x -> (780..1500 step 16).map { y -> decoded.getRGB(x, y) } }.toSet()
            assertTrue(varied.size > 100, "PNG must contain rendered rows and text, not a solid background")
            assertTrue(directory.listFiles()!!.all { it.extension == "png" }); decoded.flush()
        } finally { image.flush(); directory.deleteRecursively() }
    }
    @Test fun invalidExportPreservesAnExistingFile() {
        val directory = Files.createTempDirectory("podium-replay-existing-").toFile()
        val image = renderReplayPoster(ReplaySummary.from(SavedState(), 30))
        try {
            val target = directory.resolve("existing.txt").apply { writeText("preserve") }
            assertFailsWith<IllegalArgumentException> { writeReplayPoster(image, target) }
            assertEquals("preserve", target.readText()); assertEquals(1, directory.listFiles()!!.size)
        } finally { image.flush(); directory.deleteRecursively() }
    }
}
