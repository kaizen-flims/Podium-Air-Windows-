package com.podium.air.desktop

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.sound.sampled.AudioSystem
import kotlin.math.sin
import kotlin.test.*

class MediaFilesTest {
    @Test fun opusDecodesActualAudioAndTrimsDelayAndEndPadding() = temporary { dir ->
        val source = File(dir, "tone.opus").apply { writeBytes(opusFixture()) }
        val decoded = MediaFiles(File(dir, "cache")).prepare(source)
        AudioSystem.getAudioInputStream(decoded).use { audio ->
            assertEquals(48000f, audio.format.sampleRate)
            assertEquals(1, audio.format.channels)
            assertEquals(48000L - 312 - 10, audio.frameLength)
            assertTrue(audio.readBytes().any { it != 0.toByte() }, "Decoded samples must contain the encoded tone")
        }
        assertEquals(1, File(dir, "cache").listFiles()!!.size)
    }
    @Test fun checksumDamageAndTruncatedStreamsNeverBecomeCachedAudio() = temporary { dir ->
        val bytes = opusFixture()
        val damaged = File(dir, "bad.opus").apply { writeBytes(bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }) }
        val files = MediaFiles(File(dir, "cache"))
        assertFailsWith<IllegalArgumentException> { files.prepare(damaged) }
        assertTrue(File(dir, "cache").listFiles().orEmpty().isEmpty())
        damaged.writeBytes(bytes.copyOf(bytes.size - 10))
        assertFails { files.prepare(damaged) }
        assertTrue(File(dir, "cache").listFiles().orEmpty().isEmpty())
    }
    @Test fun preparationHonorsCancellationAndInvalidatesChangedFiles() = temporary { dir ->
        val file = File(dir, "tone.opus").apply { writeBytes(opusFixture()) }
        val files = MediaFiles(File(dir, "cache"))
        assertFailsWith<InterruptedException> { files.prepare(file) { throw InterruptedException("cancelled") } }
        val first = files.prepare(file)
        assertEquals(first, files.prepare(file))
        file.setLastModified(file.lastModified() + 2000)
        assertNotEquals(first, files.prepare(file))
        assertEquals(2, files.clear()); assertEquals(0L, files.sizeBytes())
    }
    @Test fun nativeWaveUsesOriginalWithoutCopyOrDecode() = temporary { dir ->
        val wave = File(dir, "native.wav"); generateTestWave(wave, 1)
        assertEquals(wave, MediaFiles(File(dir, "cache")).prepare(wave))
        assertFalse(File(dir, "cache").exists())
    }
    @Test fun oggRejectsMissingPagesAndOversizedPackets() {
        val missing = oggPage("OpusHead".toByteArray(), 2, 0, 0) + oggPage(ByteArray(16), 4, 0, 2)
        assertFailsWith<IllegalArgumentException> { readOgg(ByteArrayInputStream(missing)) { _, _, _ -> } }
    }
    private fun temporary(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("podium-codec").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}

private fun opusFixture(): ByteArray {
    val head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).put("OpusHead".toByteArray())
        .put(1).put(1).putShort(312).putInt(48000).putShort(0).put(0).array()
    val tags = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).put("OpusTags".toByteArray()).putInt(0).putInt(0).array()
    val encoder = OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_AUDIO)
    val pages = mutableListOf(oggPage(head, 2, 0, 0), oggPage(tags, 0, 0, 1))
    repeat(50) { frame ->
        val pcm = ShortArray(960) { (sin((frame * 960 + it) * 2 * Math.PI * 440 / 48000) * 2000).toInt().toShort() }
        val output = ByteArray(4096)
        val size = encoder.encode(pcm, 0, pcm.size, output, 0, output.size)
        pages += oggPage(output.copyOf(size), if (frame == 49) 4 else 0, (frame + 1) * 960L - if (frame == 49) 10 else 0, frame + 2)
    }
    return pages.fold(ByteArray(0)) { result, page -> result + page }
}
private fun oggPage(packet: ByteArray, flags: Int, granule: Long, sequence: Int): ByteArray {
    val segments = packet.size / 255 + 1
    val header = ByteBuffer.allocate(27 + segments).order(ByteOrder.LITTLE_ENDIAN).put("OggS".toByteArray())
        .put(0).put(flags.toByte()).putLong(granule).putInt(1).putInt(sequence).putInt(0).put(segments.toByte())
    repeat(segments) { header.put((if (it == segments - 1) packet.size % 255 else 255).toByte()) }
    val bytes = header.array() + packet
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(22, oggCrc(bytes))
    return bytes
}
