// SPDX-License-Identifier: GPL-3.0-only
// Desktop equivalent of Podium Air's 1080x1920 ReplayPoster, preserving its
// summary content and 72px margins. Java2D replaces Android Canvas/Bitmap.
package com.podium.air.desktop

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import javax.imageio.ImageIO

internal data class ReplaySummary(val period: Long, val entries: List<ListeningEntry>, val ranked: List<Pair<StoredTrack, Long>>) {
    val milliseconds = entries.sumOf { it.milliseconds }
    val plays = entries.sumOf { it.plays }
    val artists = ranked.groupBy { it.first.artist }.map { (artist, rows) -> artist to rows.sumOf { it.second } }.sortedByDescending { it.second }
    companion object {
        fun from(state: SavedState, period: Long, today: LocalDate = LocalDate.now()): ReplaySummary {
            require(period in setOf(7L, 30L, 365L))
            val start = today.minusDays(period - 1).toString(); val end = today.toString()
            val entries = state.listening.filter { it.day in start..end && it.milliseconds >= 0 && it.plays >= 0 }
            val library = state.library.associateBy { it.id }
            val ranked = entries.groupBy { it.trackId }.mapNotNull { (id, rows) -> library[id]?.let { it to rows.sumOf { row -> row.milliseconds } } }.sortedByDescending { it.second }
            return ReplaySummary(period, entries, ranked)
        }
    }
}
internal data class ReplayPreview(val image: BufferedImage, val summary: ReplaySummary)

/** Real listening records, frozen when the preview is opened. No network or messaging. */
internal fun renderReplayPoster(summary: ReplaySummary): BufferedImage {
    val image = BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.paint = GradientPaint(0f, 0f, Color(0x3A1C71), 1080f, 1920f, Color(0x2B5876)); graphics.fillRect(0, 0, 1080, 1920)
        graphics.paint = RadialGradientPaint(900f, 430f, 1100f, floatArrayOf(0f, 1f), arrayOf(Color(215, 109, 119, 200), Color(215, 109, 119, 0))); graphics.fillRect(0, 0, 1080, 1920)
        graphics.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 70), 0f, 1920f, Color(0, 0, 0, 225)); graphics.fillRect(0, 0, 1080, 1920)
        fun text(value: String, x: Int, y: Int, size: Int, color: Color = Color.WHITE, bold: Boolean = false, width: Int = 936) {
            graphics.font = Font("Segoe UI", if (bold) Font.BOLD else Font.PLAIN, size); graphics.color = color
            var fitted = value.replace(Regex("[\\r\\n\\t]+"), " ")
            if (graphics.fontMetrics.stringWidth(fitted) > width) {
                val points = fitted.codePoints().toArray(); var count = points.size
                while (count > 0 && graphics.fontMetrics.stringWidth(String(points, 0, count) + "…") > width) count--
                fitted = String(points, 0, count) + "…"
            }
            graphics.drawString(fitted, x, y)
        }
        text("PODIUM AIR", 72, 112, 38, bold = true)
        text("Your Replay", 72, 242, 96, bold = true)
        text("Last ${summary.period} days", 72, 313, 38, Color(255, 255, 255, 170))
        text((summary.milliseconds / 60000).toString(), 72, 503, 132, bold = true)
        text("minutes listened", 72, 565, 42, Color(255, 255, 255, 190))
        text("${summary.plays} track starts · ${summary.ranked.size} tracks", 72, 636, 35, Color(255, 255, 255, 155))
        text("TOP TRACKS", 72, 763, 38, bold = true)
        summary.ranked.take(5).forEachIndexed { index, (track, ms) ->
            val y = 822 + index * 132
            text((index + 1).toString(), 72, y + 53, 38, Color(255, 255, 255, 120), bold = true, width = 45)
            val artX = 134; val artY = y; val artSize = 98
            graphics.color = Color(255, 255, 255, 35); graphics.fill(RoundRectangle2D.Float(artX.toFloat(), artY.toFloat(), artSize.toFloat(), artSize.toFloat(), 18f, 18f))
            track.artwork?.let { path ->
                runCatching { File(path).takeIf { it.isFile && it.length() <= 10_000_000 }?.let(ImageIO::read) }.getOrNull()?.let { art ->
                    val old = graphics.clip
                    graphics.clip = RoundRectangle2D.Float(artX.toFloat(), artY.toFloat(), artSize.toFloat(), artSize.toFloat(), 18f, 18f)
                    val side = minOf(art.width, art.height)
                    graphics.drawImage(art, artX, artY, artX + artSize, artY + artSize, (art.width-side)/2, (art.height-side)/2, (art.width+side)/2, (art.height+side)/2, null)
                    graphics.clip = old; art.flush()
                }
            }
            text(track.title, 260, y + 39, 36, bold = true, width = 748)
            text("${track.artist} · ${ms / 60000} minutes", 260, y + 82, 29, Color(255, 255, 255, 160), width = 748)
        }
        if (summary.ranked.isEmpty()) text("Your listening history starts with your next track.", 72, 848, 36)
        text("TOP ARTISTS", 72, 1542, 38, bold = true)
        summary.artists.take(3).forEachIndexed { index, (artist, ms) -> text("${index + 1}. $artist · ${ms / 60000} minutes", 72, 1621 + index * 62, 34) }
        text("Made with love by Prem", 72, 1856, 28, Color(255, 255, 255, 130))
    } finally { graphics.dispose() }
    return image
}
internal fun writeReplayPoster(image: BufferedImage, target: File) {
    require(target.extension.equals("png", true)) { "Replay exports use the PNG format." }
    val directory = requireNotNull(target.absoluteFile.parentFile).toPath()
    val temporary = Files.createTempFile(directory, "podium-replay-", ".png")
    try {
        check(ImageIO.write(image, "png", temporary.toFile())) { "The PNG encoder is unavailable." }
        try { Files.move(temporary, target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temporary) }
}
