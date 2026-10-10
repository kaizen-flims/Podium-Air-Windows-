// SPDX-License-Identifier: GPL-3.0-only
// Desktop adaptation of Podium Air ReplayModel/ReplayPoster's individual story
// ordering, hue offsets, facts, hero rows and 1080x1920 format.
// Source: kaizen-flims/Podium-Air, 48902e6b20fdcfeb1723e02d4744849e82d5067a.
package com.podium.air.desktop

import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal enum class ReplayStoryPage(val title: String, val hue: Float) {
    INTRO("Your story", 0f), MINUTES("Minutes", 40f), ARTISTS("Artists", 145f),
    SONGS("Songs", 95f), ALBUMS("Albums", 195f), GENRES("Genres", 240f),
    HABITS("Habits", 285f), SUMMARY("Recap", 325f),
}
internal data class ReplayRow(val title: String, val subtitle: String?, val milliseconds: Long, val starts: Int, val artwork: String?)
internal fun ReplaySummary.songRows() = ranked.map { (track, ms) ->
    ReplayRow(track.title, track.artist, ms, entries.filter { it.trackId == track.id }.sumOf { it.plays }, track.artwork)
}
internal fun ReplaySummary.groupRows(kind: ReplayStoryPage): List<ReplayRow> = ranked.groupBy { (track, _) ->
    when (kind) { ReplayStoryPage.ARTISTS -> track.artist; ReplayStoryPage.ALBUMS -> track.albumKey; else -> track.genre.trim() }
}.values.mapNotNull { rows ->
    val track = rows.first().first
    val title = when (kind) { ReplayStoryPage.ARTISTS -> track.artist; ReplayStoryPage.ALBUMS -> track.album; else -> track.genre.trim() }
    if (title.isBlank() || title in listOf("Unknown album", "Unknown artist")) null else {
        val ids = rows.map { it.first.id }.toSet()
        ReplayRow(title, if (kind == ReplayStoryPage.ALBUMS) track.albumArtist.ifBlank { track.artist } else null,
            rows.sumOf { it.second }, entries.filter { it.trackId in ids }.sumOf { it.plays }, track.artwork)
    }
}.sortedByDescending { it.milliseconds }
internal fun ReplaySummary.storyPages() = ReplayStoryPage.entries.filter { page ->
    when (page) { ReplayStoryPage.ALBUMS, ReplayStoryPage.GENRES -> groupRows(page).isNotEmpty(); else -> true }
}
internal fun ReplaySummary.peakHour(): Int? = entries.flatMap { it.hourMilliseconds.entries }
    .filter { it.key in 0..23 && it.value > 0 }.groupBy { it.key }.maxByOrNull { (_, rows) -> rows.sumOf { it.value } }?.key

/** Paints source story facts using local artwork and installed system fonts; never fetches covers. */
internal fun renderReplayStory(summary: ReplaySummary, page: ReplayStoryPage): BufferedImage {
    val output = BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB)
    val g = output.createGraphics()
    val loaded = mutableMapOf<String, BufferedImage?>()
    fun art(path: String?): BufferedImage? {
        if (path == null) return null
        return loaded.getOrPut(path) {
            runCatching {
                val file = File(path).takeIf { it.isFile && it.length() <= 10_000_000 } ?: return@runCatching null
                ImageIO.createImageInputStream(file).use { input ->
                    val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return@use null
                    try {
                        reader.input = input
                        val width = reader.getWidth(0); val height = reader.getHeight(0)
                        require(width > 0 && height > 0 && width.toLong() * height <= 20_000_000)
                        val params = reader.defaultReadParam
                        val sample = (maxOf(width, height) / 512).coerceAtLeast(1)
                        params.setSourceSubsampling(sample, sample, 0, 0)
                        reader.read(0, params)
                    } finally { reader.dispose() }
                }
            }.getOrNull()
        }
    }
    fun text(value: String, x: Int, y: Int, size: Int, bold: Boolean = false, alpha: Int = 255, width: Int = 936) {
        g.font = Font(Font.DIALOG, if (bold) Font.BOLD else Font.PLAIN, size); g.color = Color(255, 255, 255, alpha)
        var clean = value.replace(Regex("[\\r\\n\\t]+"), " ")
        if (g.fontMetrics.stringWidth(clean) > width) {
            val points = clean.codePoints().toArray(); var length = points.size
            while (length > 0 && g.fontMetrics.stringWidth(String(points, 0, length) + "…") > width) length--
            clean = String(points, 0, length) + "…"
        }
        g.drawString(clean, x, y)
    }
    fun cover(row: ReplayRow, x: Int, y: Int, size: Int, circular: Boolean = false) {
        val shape = if (circular) Ellipse2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat())
            else RoundRectangle2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat(), 24f, 24f)
        g.color = Color(255, 255, 255, 35); g.fill(shape)
        val bitmap = art(row.artwork)
        if (bitmap == null) text(String(Character.toChars(row.title.codePoints().findFirst().orElse(0x266A))).uppercase(), x + size / 4, y + size * 2 / 3, size / 2, bold = true, alpha = 160, width = size)
        else {
            val clip = g.clip; g.clip = shape
            val side = minOf(bitmap.width, bitmap.height)
            g.drawImage(bitmap, x, y, x + size, y + size, (bitmap.width-side)/2, (bitmap.height-side)/2, (bitmap.width+side)/2, (bitmap.height+side)/2, null)
            g.clip = clip
        }
    }
    fun duration(ms: Long) = "${String.format(java.util.Locale.US, "%,d", ms / 60000)} minutes"
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val rows = when (page) { ReplayStoryPage.ARTISTS, ReplayStoryPage.ALBUMS, ReplayStoryPage.GENRES -> summary.groupRows(page); else -> summary.songRows() }
        val lead = rows.firstOrNull()?.let { art(it.artwork) }
        var red = 0L; var green = 0L; var blue = 0L; var samples = 0
        if (lead != null) for (y in 0 until lead.height step maxOf(1, lead.height / 16)) for (x in 0 until lead.width step maxOf(1, lead.width / 16)) {
            val color = Color(lead.getRGB(x, y)); red += color.red; green += color.green; blue += color.blue; samples++
        }
        val hsv = if (samples > 0) Color.RGBtoHSB((red/samples).toInt(), (green/samples).toInt(), (blue/samples).toInt(), null) else floatArrayOf(0.72f, 0.5f, 0.55f)
        val hue = (hsv[0] + page.hue / 360) % 1
        val first = Color.getHSBColor(hue, hsv[1].coerceIn(0.3f, 0.75f), 0.42f)
        val second = Color.getHSBColor((hue+0.22f)%1, 0.55f, 0.5f)
        g.paint = GradientPaint(0f, 0f, first, 1080f, 1700f, second); g.fillRect(0, 0, 1080, 1920)
        g.paint = RadialGradientPaint(850f, 320f, 950f, floatArrayOf(0f, 1f), arrayOf(Color(255, 255, 255, 35), Color(255, 255, 255, 0))); g.fillRect(0, 0, 1080, 1920)
        g.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 60), 0f, 1920f, Color(0, 0, 0, 225)); g.fillRect(0, 0, 1080, 1920)
        text("PODIUM AIR", 72, 112, 38, true); text("Replay · ${summary.label}", 72, 194, 44, alpha = 180)
        text(page.title, 72, 330, 104, true)
        when (page) {
            ReplayStoryPage.INTRO, ReplayStoryPage.MINUTES -> {
                text(String.format(java.util.Locale.US, "%,d", summary.milliseconds / 60000), 72, 570, 160, true)
                text("minutes of your music", 72, 638, 48, alpha = 190)
                summary.songRows().take(6).forEachIndexed { index, row ->
                    cover(row, 72 + (index % 3) * 318, 780 + (index / 3) * 318, 286)
                }
                text("${summary.ranked.size} tracks · ${summary.artists.size} artists", 72, 1580, 44, alpha = 180)
            }
            ReplayStoryPage.SONGS, ReplayStoryPage.ARTISTS, ReplayStoryPage.ALBUMS -> {
                rows.firstOrNull()?.let { row ->
                    cover(row, 72, 450, 348, page == ReplayStoryPage.ARTISTS)
                    text(row.title, 468, 535, 72, true, width = 540)
                    row.subtitle?.let { text(it, 468, 604, 44, alpha = 180, width = 540) }
                    text(duration(row.milliseconds), 468, 677, 42, alpha = 165, width = 540)
                    text("${row.starts} track starts", 468, 740, 36, alpha = 145, width = 540)
                }
                rows.drop(1).take(4).forEachIndexed { index, row ->
                    val y = 900 + index * 160
                    text((index+2).toString(), 72, y+74, 44, true, 140, 50)
                    cover(row, 134, y, 108, page == ReplayStoryPage.ARTISTS)
                    text(row.title, 270, y+46, 42, true, width = 738)
                    text(duration(row.milliseconds), 270, y+99, 34, alpha = 160, width = 738)
                }
                if (rows.isEmpty()) text("No listening records in this period.", 72, 560, 40, alpha = 180)
            }
            ReplayStoryPage.GENRES -> {
                rows.firstOrNull()?.let { text(it.title, 72, 555, 142, true); text(duration(it.milliseconds), 72, 632, 46, alpha = 180) }
                rows.drop(1).take(4).forEachIndexed { i, row -> text("${i+2}. ${row.title}", 72, 850+i*140, 52, true); text(duration(row.milliseconds), 72, 903+i*140, 36, alpha = 160) }
                if (rows.isEmpty()) text("No genre tags in this period.", 72, 560, 40, alpha = 180)
            }
            ReplayStoryPage.HABITS -> {
                var y = 540
                fun stat(value: String, label: String) { text(value, 72, y, 78, true); text(label, 72, y+60, 40, alpha = 180); y += 235 }
                stat(summary.groupRows(ReplayStoryPage.ALBUMS).size.toString(), "different albums")
                val days = summary.entries.filter { it.milliseconds > 0 }.groupBy { it.day }
                stat(days.size.toString(), "days with music")
                days.maxByOrNull { (_, records) -> records.sumOf { it.milliseconds } }?.let { stat(it.key, "your biggest day · ${duration(it.value.sumOf { row -> row.milliseconds })}") }
                summary.peakHour()?.let { hour -> stat(when (hour) { 0 -> "Midnight"; 12 -> "Noon"; else -> "${if (hour > 12) hour-12 else hour} ${if (hour < 12) "am" else "pm"}" }, "when you listen most") }
            }
            ReplayStoryPage.SUMMARY -> {
                val facts = listOf("Minutes" to (summary.milliseconds / 60000).toString(), "Top song" to summary.ranked.firstOrNull()?.first?.title,
                    "Top artist" to summary.artists.firstOrNull()?.first, "Top album" to summary.groupRows(ReplayStoryPage.ALBUMS).firstOrNull()?.title,
                    "Top genre" to summary.groupRows(ReplayStoryPage.GENRES).firstOrNull()?.title)
                facts.filter { it.second != null }.forEachIndexed { i, (name, value) -> text(name, 72, 530+i*180, 38, alpha = 150); text(value!!, 72, 600+i*180, 64, true) }
            }
        }
        text("Made with love by Prem", 72, 1856, 28, alpha = 130)
    } finally { loaded.values.filterNotNull().forEach { it.flush() }; g.dispose() }
    return output
}
