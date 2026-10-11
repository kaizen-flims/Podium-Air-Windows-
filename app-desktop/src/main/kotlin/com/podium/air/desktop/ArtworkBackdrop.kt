// SPDX-License-Identifier: GPL-3.0-only
// boxBlurred/boxBlurPass adapted from Podium Air's ArtworkMeshBackdrop.kt at
// 48902e6b20fdcfeb1723e02d4744849e82d5067a; original source comments retained.
package com.podium.air.desktop

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

/** Small immutable-by-convention textures, decoded on an IO worker, never from a URL. */
internal class ArtworkBackdropCache(private val capacity: Int = 8) {
    init { require(capacity in 1..64) }
    private data class Key(val path: String, val bytes: Long, val modified: Long)
    private val images = object : LinkedHashMap<Key, BufferedImage>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Key, BufferedImage>) = size > capacity
    }

    @Synchronized fun load(uri: String?): BufferedImage? {
        if (uri == null) return null
        return try {
            val location = URI(uri)
            if (location.scheme != "file" || location.rawQuery != null || location.rawFragment != null) return null
            val file = File(location)
            val bytes = file.length()
            if (!file.isFile || bytes !in 1..MAX_ARTWORK_BYTES) return null
            val key = Key(file.canonicalPath, bytes, file.lastModified())
            images[key]?.let { return it }
            val decoded = decode(file) ?: return null
            images.keys.removeIf { it.path == key.path }
            images[key] = decoded
            decoded
        } catch (_: Exception) { null }
    }

    private fun decode(file: File): BufferedImage? {
        ImageIO.createImageInputStream(file).use { input ->
            if (input == null) return null
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0 || width.toLong() * height > MAX_ARTWORK_PIXELS) return null
                val stride = ((maxOf(width, height) + SOURCE_PX - 1) / SOURCE_PX).coerceAtLeast(1)
                val options = reader.defaultReadParam.apply { setSourceSubsampling(stride, stride, 0, 0) }
                val sample = reader.read(0, options) ?: return null
                try {
                    // Ignore hidden RGB in fully transparent pixels; the original source blur is opaque.
                    val opaque = BufferedImage(sample.width, sample.height, BufferedImage.TYPE_INT_RGB)
                    val graphics = opaque.createGraphics()
                    try {
                        graphics.color = Color.BLACK
                        graphics.fillRect(0, 0, opaque.width, opaque.height)
                        graphics.drawImage(sample, 0, 0, null)
                    } finally { graphics.dispose() }
                    return opaque.boxBlurred(3)
                } finally { sample.flush() }
            } finally { reader.dispose() }
        }
    }

    companion object {
        val shared = ArtworkBackdropCache()
        private const val SOURCE_PX = 128
        private const val MAX_ARTWORK_BYTES = 8L * 1024 * 1024
        private const val MAX_ARTWORK_PIXELS = 32_000_000L
    }
}

/** Three small box passes approximate a broad Gaussian blur at thumbnail scale. */
private fun BufferedImage.boxBlurred(passes: Int): BufferedImage {
    val width = width
    val height = height
    if (width < 2 || height < 2) return this
    var source = getRGB(0, 0, width, height, null, 0, width)
    var target = IntArray(source.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)

    repeat(passes) {
        boxBlurPass(source, target, width, height, radius, horizontal = true)
        boxBlurPass(target, source, width, height, radius, horizontal = false)
    }
    return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, width, height, source, 0, width) }
}

private fun boxBlurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1
    repeat(minor) { fixed ->
        var red = 0
        var green = 0
        var blue = 0
        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) source[fixed * width + position]
            else source[position * width + fixed]
        }
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        repeat(major) { moving ->
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = argb(red / window, green / window, blue / window)
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}


private fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
