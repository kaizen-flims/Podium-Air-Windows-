package com.podium.air.desktop

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.*

/** Independent ISO-BMFF fixtures exercise boundary/security behavior without decoding fake audio. */
class Mp4AacTest {
    @Test fun sampleTableReadsInlineAudioAndSkipsUnknownMetadata() {
        readFixture(mp4()) { track ->
            assertContentEquals(byteArrayOf(0x12, 0x10), track.configuration)
            assertEquals(48000L, track.mediaTimescale)
            assertContentEquals(longArrayOf(8), track.offsets)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), track.read(0))
        }
    }
    @Test fun externalReferencesAndProtectedSampleEntriesAreRejected() {
        assertFailsWith<IllegalArgumentException> { readFixture(mp4(inline = false)) {} }
        assertFailsWith<IllegalArgumentException> { readFixture(mp4(codec = "enca")) {} }
    }
    @Test fun samplePointersAndTruncatedTablesAreRejected() {
        assertFailsWith<IllegalArgumentException> { readFixture(mp4(offset = 16)) {} }
        assertFailsWith<IllegalArgumentException> { readFixture(mp4(truncatedSizes = true)) {} }
    }
    @Test fun deeplyNestedDescriptorsAndFragmentedMediaAreRejected() {
        var data = byteArrayOf(5, 2, 0x12, 0x10)
        repeat(40) { data = descriptor(4, ByteArray(13) + data) }
        assertFailsWith<IllegalArgumentException> { readFixture(mp4(configDescriptor = data)) {} }
        assertFailsWith<IllegalArgumentException> { readFixture(mp4() + box("moof", ByteArray(0))) {} }
    }
    private fun readFixture(bytes: ByteArray, inspect: (Mp4Aac) -> Unit) {
        val file = Files.createTempFile("podium-mp4-test", ".m4a").toFile()
        try { file.writeBytes(bytes); RandomAccessFile(file, "r").use { inspect(Mp4Aac(it)) } }
        finally { file.delete() }
    }
}

private fun binary(write: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { DataOutputStream(it).use(write) }.toByteArray()
private fun box(type: String, data: ByteArray) = binary { writeInt(data.size + 8); writeBytes(type); write(data) }
private fun full(type: String, data: ByteArray, flags: Int = 0) = box(type, binary { writeInt(flags); write(data) })
private fun descriptor(tag: Int, data: ByteArray): ByteArray = binary {
    writeByte(tag)
    val groups = mutableListOf(data.size and 127); var left = data.size ushr 7
    while (left > 0) { groups.add(0, (left and 127) or 128); left = left ushr 7 }
    groups.forEach(::writeByte); write(data)
}
private fun mp4(inline: Boolean = true, codec: String = "mp4a", offset: Int = 8, truncatedSizes: Boolean = false,
                configDescriptor: ByteArray = byteArrayOf(5, 2, 0x12, 0x10)): ByteArray {
    val esds = full("esds", configDescriptor)
    val sample = box(codec, binary {
        write(ByteArray(6)); writeShort(1); writeShort(0); writeShort(0); writeInt(0)
        writeShort(2); writeShort(16); writeShort(0); writeShort(0); writeInt(48000 shl 16); write(esds)
    })
    val table = box("stbl", full("stsd", binary { writeInt(1); write(sample) }) +
        full("stsz", binary { writeInt(0); writeInt(1); if (!truncatedSizes) writeInt(4) }) +
        full("stco", binary { writeInt(1); writeInt(offset) }) +
        full("stsc", binary { writeInt(1); writeInt(1); writeInt(1); writeInt(1) }))
    val references = box("dinf", full("dref", binary { writeInt(1); write(full("url ", if (inline) ByteArray(0) else "https://example.invalid/audio\u0000".toByteArray(), if (inline) 1 else 0)) }))
    val media = box("mdia", full("mdhd", binary { writeInt(0); writeInt(0); writeInt(48000); writeInt(48000) }) +
        full("hdlr", binary { writeInt(0); writeBytes("soun"); write(ByteArray(12)) }) + box("minf", references + table))
    val movie = box("moov", full("mvhd", binary { writeInt(0); writeInt(0); writeInt(1000); writeInt(1000) }) +
        box("free", ByteArray(20)) + box("trak", media))
    return box("mdat", byteArrayOf(1, 2, 3, 4)) + movie
}
