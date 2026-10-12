// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.*

class ArtworkBackdropTest {
    private fun directory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("podium-artwork-test").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun cover(file: File, width: Int = 128, height: Int = 128, pixel: (Int, Int) -> Int): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, pixel(x, y))
        check(ImageIO.write(image, "png", file)); image.flush()
        return file
    }

    @Test fun neutralCoversStayNeutralAndDoNotAcquireRed() = directory { root ->
        val cache = ArtworkBackdropCache()
        for (gray in listOf(0, 32, 128, 255)) {
            val file = cover(File(root, "gray-$gray.png")) { _, _ -> (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray }
            val result = assertNotNull(cache.load(file.toURI().toString()))
            val rgb = result.getRGB(64, 64)
            assertEquals(gray, rgb shr 16 and 255)
            assertEquals(gray, rgb shr 8 and 255)
            assertEquals(gray, rgb and 255)
        }
    }

    @Test fun smallColorDetailsDoNotTurnAMostlyDarkSleeveIntoARedField() = directory { root ->
        val file = cover(File(root, "black-and-red.png")) { x, _ -> if (x >= 115) 0xFFFF0000.toInt() else 0xFF000000.toInt() }
        val result = assertNotNull(ArtworkBackdropCache().load(file.toURI().toString()))
        val left = result.getRGB(8, 64) shr 16 and 255
        val right = result.getRGB(126, 64) shr 16 and 255
        assertTrue(left < 5, "The dark region must retain its position.")
        assertTrue(right > 100, "The sleeve's small red region must remain visible.")
        val meanRed = (0 until result.width).map { result.getRGB(it, 64) shr 16 and 255 }.average()
        assertTrue(meanRed < 45, "The backdrop must preserve the predominance of black.")
    }

    @Test fun decodingIsThumbnailSizedAndTransparentHiddenRgbStaysInvisible() = directory { root ->
        val large = cover(File(root, "large.png"), 2048, 512) { _, _ -> 0xFF0033CC.toInt() }
        val result = assertNotNull(ArtworkBackdropCache().load(large.toURI().toString()))
        assertTrue(result.width <= 128 && result.height <= 128)
        assertEquals(4, result.width / result.height)
        val invisible = cover(File(root, "invisible.png")) { _, _ -> 0x00FF0000 }
        val transparentResult = assertNotNull(ArtworkBackdropCache().load(invisible.toURI().toString()))
        assertEquals(0xFF000000.toInt(), transparentResult.getRGB(64, 64))
    }

    @Test fun cacheIsBoundedAndChangedFilesAreReadAgain() = directory { root ->
        val cache = ArtworkBackdropCache(2)
        val one = cover(File(root, "cover ü 音楽.png")) { _, _ -> 0xFF0011CC.toInt() }
        val uri = one.toURI().toString()
        val first = assertNotNull(cache.load(uri))
        assertSame(first, cache.load(uri), "Reopening one sleeve must not blur it again.")
        for (i in 2..3) cache.load(cover(File(root, "$i.png")) { _, _ -> 0xFF334455.toInt() }.toURI().toString())
        val reloaded = assertNotNull(cache.load(uri))
        assertNotSame(first, reloaded, "The oldest sleeve must be evicted.")
        val modified = one.lastModified()
        cover(one) { _, _ -> 0xFFCC1100.toInt() }
        assertTrue(one.setLastModified(modified + 2000))
        val changed = assertNotNull(cache.load(uri))
        assertNotSame(reloaded, changed)
        assertTrue((changed.getRGB(64, 64) shr 16 and 255) > 150)
    }

    @Test fun brokenRemoteOversizedAndBombDimensionInputsFallBackWithoutDecoding() = directory { root ->
        val cache = ArtworkBackdropCache()
        assertNull(cache.load(null))
        assertNull(cache.load("https://example.invalid/private-art.png"))
        assertNull(cache.load("file:///bad.png?secret=yes"))
        val broken = File(root, "broken.png").apply { writeText("This is not an image.") }
        assertNull(cache.load(broken.toURI().toString()))
        val tooLarge = File(root, "too-large.png")
        RandomAccessFile(tooLarge, "rw").use { it.setLength(9L * 1024 * 1024) }
        assertNull(cache.load(tooLarge.toURI().toString()))
        val bomb = cover(File(root, "huge-dimensions.png"), 2, 2) { _, _ -> 0xFFFFFFFF.toInt() }
        val png = bomb.readBytes()
        ByteBuffer.wrap(png).putInt(16, 100000).putInt(20, 100000)
        val checksum = CRC32().apply { update(png, 12, 17) }
        ByteBuffer.wrap(png).putInt(29, checksum.value.toInt())
        bomb.writeBytes(png)
        assertNull(cache.load(bomb.toURI().toString()))
    }
}
