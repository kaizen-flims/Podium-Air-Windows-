package com.podium.air.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.*

class EmbeddedLyricsTest {
    private fun temporary(block: (File) -> Unit) { val dir = Files.createTempDirectory("podium-tags").toFile(); try { block(dir) } finally { dir.deleteRecursively() } }
    private fun integer(n: Int, little: Boolean = false) = ByteBuffer.allocate(4).order(if (little) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN).putInt(n).array()
    private fun synchsafe(n: Int) = byteArrayOf((n shr 21 and 127).toByte(), (n shr 14 and 127).toByte(), (n shr 7 and 127).toByte(), (n and 127).toByte())
    private fun frame(id: String, body: ByteArray, version: Int = 3) = id.toByteArray() + (if (version == 4) synchsafe(body.size) else integer(body.size)) + byteArrayOf(0, 0) + body
    private fun tag(body: ByteArray, version: Int = 3) = "ID3".toByteArray() + byteArrayOf(version.toByte(), 0, 0) + synchsafe(body.size) + body
    private fun box(type: String, data: ByteArray) = integer(data.size + 8) + type.toByteArray(Charsets.ISO_8859_1) + data

    @Test fun usltPreservesPlainTextUnicodeAndInheritedUtf16ByteOrder() = temporary { dir ->
        val file = File(dir, "歌.mp3")
        for (encoding in listOf(0, 1, 2, 3)) {
            val text = if (encoding == 0) "First line\nSecond line" else "বাংলা গান\nSecond line"
            val charset = when (encoding) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16LE; 2 -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
            val descriptor = if (encoding == 1) byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0, 0) else if (encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)
            file.writeBytes(tag(frame("USLT", byteArrayOf(encoding.toByte()) + "eng".toByteArray() + descriptor + text.toByteArray(charset))))
            val result = assertNotNull(EmbeddedLyrics.read(file))
            assertEquals(text.lines(), result.lines.map { it.text }); assertTrue(result.lines.all { it.timeMs == 0L && !it.isWordSynced })
        }
    }
    @Test fun millisecondSyltIsReadInBothId3VersionsWithoutInventingMpegFrameTimes() = temporary { dir ->
        val file = File(dir, "timed.mp3")
        for (version in listOf(3, 4)) {
            val body = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(2, 1, 0) + "First".toByteArray() + byteArrayOf(0) + integer(1000) + "Second".toByteArray() + byteArrayOf(0) + integer(2500)
            file.writeBytes(tag(frame("SYLT", body, version), version))
            assertEquals(listOf(1000L, 2500L), EmbeddedLyrics.read(file)!!.lines.map { it.timeMs })
            file.writeBytes(tag(frame("SYLT", body.copyOf().also { it[4] = 1 }, version), version))
            assertNull(EmbeddedLyrics.read(file))
        }
    }
    @Test fun androidEnhancedVorbisCommentWinsOverPlainFallback() = temporary { dir ->
        val entries = listOf("LYRICS=Wrong fallback", "BITCHORD_LYRICS=[00:01.00]<00:01.00>Actual <00:01.50>words\n[00:03.00]End")
        val comments = integer(0, true) + integer(entries.size, true) + entries.fold(byteArrayOf()) { bytes, entry -> val value = entry.toByteArray(); bytes + integer(value.size, true) + value }
        val file = File(dir, "android.flac")
        file.writeBytes("fLaC".toByteArray() + byteArrayOf(0x84.toByte(), (comments.size shr 16).toByte(), (comments.size shr 8).toByte(), comments.size.toByte()) + comments)
        val lyrics = assertNotNull(EmbeddedLyrics.read(file)).lines
        assertTrue(lyrics.any { it.text == "Actual words" && it.isWordSynced }); assertFalse(lyrics.any { "Wrong" in it.text })
    }
    @Test fun mp4LyricsAndFreeformTagsAreReadAfterLargeAudioWithoutScanningMdat() = temporary { dir ->
        val data = box("data", integer(1) + integer(0) + "[00:01.00]Standard".toByteArray())
        val enhanced = box("----", box("mean", integer(0) + "com.apple.iTunes".toByteArray()) + box("name", integer(0) + "BITCHORD_LYRICS".toByteArray()) + box("data", integer(1) + integer(0) + "[00:00.00]<00:00.00>Enhanced <00:00.50>words".toByteArray()))
        for (freeform in listOf(false, true)) {
            val file = File(dir, "late.m4a")
            val moov = box("moov", box("udta", box("meta", integer(0) + box("ilst", box("©lyr", data) + if (freeform) enhanced else byteArrayOf()))))
            RandomAccessFile(file, "rw").use { it.setLength(64L * 1024 * 1024); it.seek(0); it.write(integer((64L * 1024 * 1024).toInt()) + "mdat".toByteArray()); it.seek(it.length()); it.write(moov) }
            val lines = assertNotNull(EmbeddedLyrics.read(file)).lines
            assertEquals(if (freeform) "Enhanced words" else "Standard", lines.last().text)
        }
    }
    @Test fun emptyOrInvalidSidecarFallsBackAndUtf16SidecarOverridesTags() = temporary { dir ->
        val audio = File(dir, "song.mp3").apply { writeBytes(tag(frame("USLT", byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + "Embedded fallback".toByteArray()))) }
        val track = StoredTrack("id", audio.path, "Song", "Artist", "Album").song()
        val library = LocalLibrary(dir)
        File(dir, "song.ttml").writeText("<tt><body></body></tt>"); File(dir, "song.lrc").writeText("")
        assertEquals("Embedded fallback", library.lyrics(track).single().text)
        File(dir, "song.lrc").writeBytes(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + "[00:01.00]Sidecar বাংলা".toByteArray(Charsets.UTF_16LE))
        assertEquals("Sidecar বাংলা", library.lyrics(track).single().text)
    }
}
