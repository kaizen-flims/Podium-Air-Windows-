// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.data.lyrics.LyricLine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

/** Reads tags, never audio into memory. Android's BITCHORD_LYRICS field keeps its word timing. */
internal object EmbeddedLyrics {
    private const val LIMIT = 8 * 1024 * 1024
    private val enhancedNames = setOf("BITCHORD_LYRICS", "PODIUM_LYRICS")
    private val plainNames = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "LYRICSUNSYNCED", "SYNCEDLYRICS")
    private data class Candidate(val priority: Int, val source: String, val lines: List<LyricLine>)
    data class Result(val lines: List<LyricLine>, val source: String)

    fun read(file: File): Result? {
        if (!file.isFile) return null
        val candidates = mutableListOf<Candidate>()
        fun text(name: String, raw: String, source: String) {
            val key = name.uppercase(java.util.Locale.ROOT)
            if (key !in enhancedNames && key !in plainNames || raw.length > LIMIT) return
            val lines = parseLyricText(raw.trim('\u0000', '\uFEFF', ' ', '\r', '\n'))
            if (lines.any { it.text.isNotBlank() }) candidates += Candidate(if (key in enhancedNames) 0 else 2, source, lines)
        }
        runCatching {
            RandomAccessFile(file, "r").use { input ->
                val magic = ByteArray(4); if (input.length() >= 4) input.readFully(magic)
                when {
                    String(magic, Charsets.ISO_8859_1).startsWith("ID3") -> id3(input, ::text) { lines ->
                        if (lines.any { it.text.isNotBlank() }) candidates += Candidate(1, "Embedded ID3 SYLT", lines)
                    }
                    magic.contentEquals("fLaC".toByteArray()) -> flac(input, ::text)
                    magic.contentEquals("OggS".toByteArray()) -> ogg(input, ::text)
                    file.extension.lowercase() in setOf("m4a", "mp4") -> mp4(input, ::text)
                }
            }
        }
        // Also covers standard tags in WAV/AIFF and container variants handled by jaudiotagger.
        if (candidates.isEmpty() && file.extension.lowercase() in setOf("wav", "aif", "aiff")) runCatching {
            AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS)?.let { text("LYRICS", it, "Embedded lyrics") }
        }
        return candidates.minByOrNull { it.priority }?.let { Result(it.lines, it.source) }
    }

    private fun flac(input: RandomAccessFile, text: (String, String, String) -> Unit) {
        input.seek(4)
        var budget = LIMIT
        repeat(4096) {
            if (input.filePointer + 4 > input.length()) return
            val flags = input.readUnsignedByte()
            val length = (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            require(length <= budget && length <= input.length() - input.filePointer)
            budget -= length
            if (flags and 127 == 4) {
                val bytes = ByteArray(length); input.readFully(bytes)
                comments(bytes, 0, "Embedded FLAC", text)
            } else input.seek(input.filePointer + length)
            if (flags and 128 != 0) return
        }
    }

    /** Only bounded header packets of the first Opus/Vorbis stream are examined. */
    private fun ogg(input: RandomAccessFile, text: (String, String, String) -> Unit) {
        input.seek(0)
        val packet = ByteArrayOutputStream()
        var bytesRead = 0; var serial: Int? = null; var packets = 0
        while (input.filePointer + 27 <= input.length() && bytesRead < LIMIT && packets < 4) {
            val header = ByteArray(27); input.readFully(header); bytesRead += 27
            require(String(header, 0, 4, Charsets.US_ASCII) == "OggS" && header[4].toInt() == 0)
            val pageSerial = le32(header, 14)
            if (serial == null) serial = pageSerial
            require(serial == pageSerial)
            val count = header[26].toInt() and 255
            val laces = ByteArray(count); input.readFully(laces); bytesRead += count
            for (lace in laces) {
                val length = lace.toInt() and 255
                require(bytesRead + length <= LIMIT && packet.size() + length <= LIMIT)
                val part = ByteArray(length); input.readFully(part); packet.write(part); bytesRead += length
                if (length < 255) {
                    val payload = packet.toByteArray(); packet.reset(); packets++
                    if (payload.size >= 8 && String(payload, 0, 8, Charsets.US_ASCII) == "OpusTags") {
                        comments(payload, 8, "Embedded Opus", text); return
                    }
                    if (payload.size >= 7 && payload[0].toInt() == 3 && String(payload, 1, 6, Charsets.US_ASCII) == "vorbis") {
                        comments(payload, 7, "Embedded Vorbis", text); return
                    }
                }
            }
        }
    }

    private fun comments(bytes: ByteArray, from: Int, source: String, text: (String, String, String) -> Unit) {
        var pos = from
        fun length(): Int { require(pos + 4 <= bytes.size); val n = le32(bytes, pos); pos += 4; require(n >= 0 && n <= bytes.size - pos); return n }
        val vendorLength = length(); pos += vendorLength
        require(pos + 4 <= bytes.size)
        val count = le32(bytes, pos); pos += 4; require(count in 0..4096)
        repeat(count) {
            val n = length(); val entry = String(bytes, pos, n, Charsets.UTF_8); pos += n
            val equal = entry.indexOf('=')
            if (equal > 0) text(entry.substring(0, equal), entry.substring(equal + 1), source)
        }
    }

    /** Walk real box boundaries, including moov after a very large mdat. Never scan audio for tag markers. */
    private fun mp4(input: RandomAccessFile, text: (String, String, String) -> Unit) {
        var boxes = 0; var budget = LIMIT
        fun walk(from: Long, end: Long, depth: Int, inIlst: Boolean = false, item: String? = null) {
            require(depth <= 8)
            var pos = from
            while (pos + 8 <= end) {
                require(++boxes <= 10000)
                input.seek(pos)
                val shortSize = input.readInt().toLong() and 0xffffffffL
                val typeBytes = ByteArray(4); input.readFully(typeBytes)
                val type = String(typeBytes, Charsets.ISO_8859_1)
                var header = 8L
                val size = when (shortSize) { 0L -> end - pos; 1L -> { header = 16; input.readLong() }; else -> shortSize }
                require(size >= header && size <= end - pos)
                val start = pos + header; val stop = pos + size
                when {
                    type in setOf("moov", "udta") -> walk(start, stop, depth + 1)
                    type == "meta" -> { require(stop - start >= 4); walk(start + 4, stop, depth + 1) }
                    type == "ilst" -> walk(start, stop, depth + 1, inIlst = true)
                    inIlst && type == "©lyr" -> walk(start, stop, depth + 1, item = "LYRICS")
                    inIlst && type == "----" -> {
                        var name: String? = null; var raw: String? = null; var p = start
                        while (p + 8 <= stop) {
                            require(++boxes <= 10000); input.seek(p)
                            val n = input.readInt().toLong() and 0xffffffffL
                            val b = ByteArray(4); input.readFully(b); val kind = String(b, Charsets.US_ASCII)
                            require(n >= 8 && n <= stop - p)
                            if (kind == "name" && n >= 12 && n - 12 <= budget) {
                                input.skipBytes(4); val value = ByteArray((n - 12).toInt()); input.readFully(value); budget -= value.size
                                name = String(value, Charsets.UTF_8)
                            } else if (kind == "data") raw = mp4Text(input, p + 8, p + n, budget)?.also { budget -= (n - 16).toInt() }
                            p += n
                        }
                        if (name != null && raw != null) text(name!!, raw!!, "Embedded M4A freeform")
                    }
                    item != null && type == "data" -> mp4Text(input, start, stop, budget)?.let { budget -= (stop - start - 8).toInt(); text(item, it, "Embedded M4A ©lyr") }
                }
                pos = stop
            }
        }
        walk(0, input.length(), 0)
    }

    private fun mp4Text(input: RandomAccessFile, start: Long, end: Long, budget: Int): String? {
        if (end - start < 8 || end - start - 8 > budget) return null
        input.seek(start)
        val type = input.readInt() and 0xffffff; input.readInt()
        if (type !in setOf(1, 2)) return null
        val bytes = ByteArray((end - start - 8).toInt()); input.readFully(bytes)
        return String(bytes, if (type == 2) Charsets.UTF_16BE else Charsets.UTF_8)
    }

    private fun id3(input: RandomAccessFile, text: (String, String, String) -> Unit, synced: (List<LyricLine>) -> Unit) {
        input.seek(0); val header = ByteArray(10); input.readFully(header)
        val version = header[3].toInt(); require(version in 2..4)
        val size = synchsafe(header, 6); require(size in 1..LIMIT && size <= input.length() - 10)
        var body = ByteArray(size); input.readFully(body)
        val flags = header[5].toInt() and 255
        if (version == 2 && flags and 64 != 0) return // obsolete whole-tag compression
        if (version < 4 && flags and 128 != 0) body = deunsync(body)
        var pos = 0
        if (version > 2 && flags and 64 != 0) {
            require(body.size >= 4)
            pos = if (version == 4) synchsafe(body, 0) else be32(body, 0) + 4
            require(pos in 4..body.size)
        }
        while (pos + (if (version == 2) 6 else 10) <= body.size) {
            val headerSize = if (version == 2) 6 else 10
            val id = String(body, pos, if (version == 2) 3 else 4, Charsets.US_ASCII)
            if (id.all { it == '\u0000' }) break
            val n = if (version == 2) ((body[pos + 3].toInt() and 255) shl 16) or ((body[pos + 4].toInt() and 255) shl 8) or (body[pos + 5].toInt() and 255)
                else if (version == 4) synchsafe(body, pos + 4) else be32(body, pos + 4)
            require(n >= 0 && n <= body.size - pos - headerSize)
            val frameFlags = if (version == 2) 0 else body[pos + 9].toInt() and 255
            var payload = body.copyOfRange(pos + headerSize, pos + headerSize + n)
            pos += headerSize + n
            if (version == 3 && frameFlags and 0xe0 != 0 || version == 4 && frameFlags and 0x4c != 0) continue // compression, encryption, grouping
            if (version == 4 && (frameFlags and 2 != 0 || flags and 128 != 0)) payload = deunsync(payload)
            if (version == 4 && frameFlags and 1 != 0) { if (payload.size < 4) continue; payload = payload.copyOfRange(4, payload.size) }
            if (payload.isEmpty()) continue
            val encoding = payload[0].toInt(); if (encoding !in 0..3) continue
            when (id) {
                "USLT", "ULT" -> if (payload.size > 4) {
                    val (_, next) = terminated(payload, 4, encoding)
                    text("LYRICS", decode(payload, next, payload.size, encoding), "Embedded ID3 USLT")
                }
                "TXXX", "TXX" -> {
                    val (name, next) = terminated(payload, 1, encoding)
                    text(name, decode(payload, next, payload.size, encoding), "Embedded ID3 $name")
                }
                "SYLT", "SLT" -> if (payload.size > 6 && payload[4].toInt() == 2 && payload[5].toInt() == 1) {
                    // Format 2 is milliseconds. MPEG-frame timestamps need a frame clock; never pretend they are milliseconds.
                    var at = terminated(payload, 6, encoding).second
                    val lines = mutableListOf<LyricLine>()
                    while (at < payload.size && lines.size < 10000) {
                        val (value, next) = terminated(payload, at, encoding)
                        if (next + 4 > payload.size) break
                        val ms = be32(payload, next).toLong() and 0xffffffffL; at = next + 4
                        if (value.isNotBlank()) lines += LyricLine(ms, value.trim('\u0000', '\r', '\n'))
                    }
                    synced(lines.sortedBy { it.timeMs })
                }
            }
        }
    }

    private fun terminated(bytes: ByteArray, from: Int, encoding: Int): Pair<String, Int> {
        val width = if (encoding in 1..2) 2 else 1
        var at = from
        while (at + width <= bytes.size) {
            if (bytes[at].toInt() == 0 && (width == 1 || bytes[at + 1].toInt() == 0)) return decode(bytes, from, at, encoding) to at + width
            at += width
        }
        return decode(bytes, from, bytes.size, encoding) to bytes.size
    }
    private fun decode(bytes: ByteArray, from: Int, end: Int, encoding: Int): String {
        if (from >= end) return ""
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> if (bytes[from] == 0xff.toByte() || bytes[from] == 0xfe.toByte()) Charsets.UTF_16 else {
                // Some v2.3 writers put a BOM only on the description. Preserve that byte order.
                val little = (1 until minOf(10, bytes.size - 1)).any { bytes[it] == 0xff.toByte() && bytes[it + 1] == 0xfe.toByte() }
                if (little) Charsets.UTF_16LE else Charsets.UTF_16BE
            }
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(bytes, from, end - from, charset).trim('\u0000', '\uFEFF')
    }
    private fun deunsync(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size); var pos = 0
        while (pos < bytes.size) { val value = bytes[pos++]; out.write(value.toInt()); if (value.toInt() and 255 == 255 && pos < bytes.size && bytes[pos].toInt() == 0) pos++ }
        return out.toByteArray()
    }
    private fun le32(bytes: ByteArray, at: Int): Int = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8) or ((bytes[at + 2].toInt() and 255) shl 16) or ((bytes[at + 3].toInt() and 255) shl 24)
    private fun be32(bytes: ByteArray, at: Int): Int = ((bytes[at].toInt() and 255) shl 24) or ((bytes[at + 1].toInt() and 255) shl 16) or ((bytes[at + 2].toInt() and 255) shl 8) or (bytes[at + 3].toInt() and 255)
    private fun synchsafe(bytes: ByteArray, at: Int): Int {
        require((at until at + 4).all { bytes[it].toInt() and 128 == 0 })
        return (0 until 4).fold(0) { result, i -> (result shl 7) or (bytes[at + i].toInt() and 127) }
    }
}
