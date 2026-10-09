// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import java.io.RandomAccessFile

/** Non-fragmented ISO BMFF AAC sample tables. Unknown boxes are skipped by their declared bounds. */
internal class Mp4Aac(private val input: RandomAccessFile) {
    private data class Box(val type: String, val start: Long, val size: Long, val header: Long = 8) { val data get() = start + header; val end get() = start + size }
    private fun boxes(start: Long, end: Long): List<Box> {
        require(start >= 0 && end <= input.length() && end >= start)
        val result = mutableListOf<Box>(); var at = start
        while (at < end) {
            require(end - at >= 8) { "Truncated MP4 box header." }
            input.seek(at); val shortSize = input.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            val header = if (shortSize == 1L) 16L else 8L
            val size = when (shortSize) { 0L -> end - at; 1L -> input.readLong(); else -> shortSize }
            require(size >= header && size <= end - at) { "MP4 box $type has invalid bounds." }
            result += Box(type, at, size, header); require(result.size <= 10000) { "Too many MP4 boxes." }; at += size
        }
        return result
    }
    private fun children(box: Box) = boxes(box.data, box.end)
    private fun List<Box>.required(type: String): Box = firstOrNull { it.type == type } ?: error("Missing MP4 $type table.")
    private fun timescale(box: Box): Long {
        input.seek(box.data); val version = input.readUnsignedByte(); require(version in 0..1)
        input.seek(box.data + if (version == 0) 12 else 20)
        return (input.readInt().toLong() and 0xffffffffL).also { require(it > 0) }
    }
    val configuration: ByteArray
    val offsets: LongArray
    val sizes: IntArray
    val skipTicks: Long
    val durationTicks: Long?
    val mediaTimescale: Long
    val movieTimescale: Long
    init {
        val roots = boxes(0, input.length())
        require(roots.none { it.type == "moof" }) { "Fragmented M4A is not supported." }
        val movie = children(roots.required("moov"))
        movieTimescale = timescale(movie.required("mvhd"))
        val track = movie.filter { it.type == "trak" }.firstOrNull { candidate ->
            val media = children(candidate).firstOrNull { it.type == "mdia" } ?: return@firstOrNull false
            val handler = children(media).firstOrNull { it.type == "hdlr" } ?: return@firstOrNull false
            input.seek(handler.data + 8); val type = ByteArray(4).also(input::readFully).toString(Charsets.ISO_8859_1)
            type == "soun"
        } ?: error("M4A contains no audio track.")
        val trackBoxes = children(track)
        val media = children(trackBoxes.required("mdia"))
        mediaTimescale = timescale(media.required("mdhd"))
        val table = children(children(media.required("minf")).required("stbl"))
        val description = table.required("stsd")
        input.seek(description.data + 4); require(input.readInt() == 1) { "Multiple AAC sample descriptions are not supported." }
        val entry = boxes(description.data + 8, description.end).single()
        require(entry.type == "mp4a") { "Only unencrypted AAC M4A tracks are supported (no ALAC/DRM)." }
        input.seek(entry.data + 8); require(input.readUnsignedShort() == 0) { "Unsupported MP4 audio sample entry version." }
        val codecBoxes = boxes(entry.start + 36, entry.end)
        require(codecBoxes.none { it.type == "sinf" }) { "Protected audio is not supported." }
        val esds = codecBoxes.required("esds")
        require(esds.size <= 65536)
        input.seek(esds.data + 4)
        val descriptors = ByteArray((esds.end - esds.data - 4).toInt()).also(input::readFully)
        configuration = decoderConfiguration(descriptors) ?: error("Missing AAC decoder configuration.")
        val sampleSize = table.required("stsz")
        input.seek(sampleSize.data + 4); val uniform = input.readInt(); val count = input.readInt()
        require(count in 1..1_000_000 && uniform >= 0)
        require(uniform != 0 || sampleSize.end - input.filePointer >= count * 4L) { "Truncated MP4 sample sizes." }
        sizes = IntArray(count) { if (uniform != 0) uniform else input.readInt() }
        require(sizes.all { it in 1..2_000_000 }) { "Invalid AAC sample sizes." }
        val chunks = table.firstOrNull { it.type == "co64" } ?: table.required("stco")
        input.seek(chunks.data + 4); val chunkCount = input.readInt(); require(chunkCount in 1..count)
        require(chunks.end - input.filePointer >= chunkCount * if (chunks.type == "co64") 8L else 4L)
        val chunkOffsets = LongArray(chunkCount) { if (chunks.type == "co64") input.readLong() else input.readInt().toLong() and 0xffffffffL }
        val mapping = table.required("stsc")
        input.seek(mapping.data + 4); val mapCount = input.readInt(); require(mapCount in 1..chunkCount)
        require(mapping.end - input.filePointer >= mapCount * 12L)
        val map = List(mapCount) { Triple(input.readInt(), input.readInt(), input.readInt()) }
        require(map.first().first == 1 && map.all { it.second in 1..count && it.third == 1 } && map.zipWithNext().all { (a, b) -> a.first < b.first })
        offsets = LongArray(count); var sample = 0; var mapIndex = 0
        chunkOffsets.forEachIndexed { index, base ->
            while (mapIndex + 1 < map.size && map[mapIndex + 1].first <= index + 1) mapIndex++
            var offset = base
            repeat(map[mapIndex].second) {
                require(sample < count && offset >= 0 && sizes[sample] <= input.length() - offset) { "AAC sample points outside the file." }
                require(roots.any { it.type == "mdat" && offset >= it.data && offset + sizes[sample] <= it.end }) { "AAC sample points outside media data." }
                offsets[sample] = offset; offset += sizes[sample]; sample++
            }
        }
        require(sample == count) { "Incomplete AAC chunk mapping." }
        val edits = trackBoxes.firstOrNull { it.type == "edts" }?.let(::children)?.firstOrNull { it.type == "elst" }
        var skip = 0L; var duration: Long? = null
        if (edits != null) {
            input.seek(edits.data); val version = input.readUnsignedByte(); require(version in 0..1)
            input.seek(edits.data + 4); val editCount = input.readInt(); require(editCount in 1..2)
            repeat(editCount) {
                val length = if (version == 1) input.readLong() else input.readInt().toLong() and 0xffffffffL
                val time = if (version == 1) input.readLong() else input.readInt().toLong()
                require(input.readShort() == 1.toShort() && input.readShort() == 0.toShort()) { "Unsupported MP4 edit playback rate." }
                if (time >= 0) { require(duration == null) { "Multiple nonempty MP4 edits are not supported." }; skip = time; duration = length }
            }
        }
        skipTicks = skip; durationTicks = duration
    }
    fun read(index: Int): ByteArray = ByteArray(sizes[index]).also { input.seek(offsets[index]); input.readFully(it) }
}

private fun decoderConfiguration(bytes: ByteArray): ByteArray? {
    fun find(start: Int, end: Int): ByteArray? {
        var at = start
        while (at < end) {
            val tag = bytes[at++].toInt() and 255
            var length = 0; var complete = false
            repeat(4) {
                if (!complete) {
                    require(at < end); val value = bytes[at++].toInt() and 255
                    length = (length shl 7) or (value and 127); complete = value and 128 == 0
                }
            }
            require(complete && length <= end - at) { "Invalid MP4 descriptor bounds." }
            val stop = at + length
            if (tag == 5) return bytes.copyOfRange(at, stop).also { require(it.size in 2..64) }
            val child = when (tag) {
                3 -> {
                    require(length >= 3); var cursor = at + 3; val flags = bytes[at + 2].toInt() and 255
                    if (flags and 128 != 0) cursor += 2
                    if (flags and 64 != 0) { require(cursor < stop); cursor += 1 + (bytes[cursor].toInt() and 255) }
                    if (flags and 32 != 0) cursor += 2
                    cursor
                }
                4 -> at + 13
                else -> stop
            }
            require(child <= stop)
            if (child < stop) find(child, stop)?.let { return it }
            at = stop
        }
        return null
    }
    return find(0, bytes.size)
}
