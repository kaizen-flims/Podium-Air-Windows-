// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import io.github.jaredmdobson.concentus.OpusDecoder
import net.sourceforge.jaad.aac.Decoder
import net.sourceforge.jaad.aac.SampleBuffer
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.pow
import kotlin.math.roundToInt

/** Bounded, disk-backed PCM preparation. Originals and metadata are never modified. */
class MediaFiles(private val directory: File = File(defaultDataDirectory(), "decoded")) {
    private val locks = Array(32) { Any() }
    fun prepare(source: File, checkCancelled: () -> Unit = {}): File {
        require(source.isFile) { "File is missing: ${source.name}. Reimport it or remove it from your library." }
        if (source.extension.lowercase() !in setOf("flac", "opus", "ogg", "m4a")) return source
        val identity = "v3|${source.canonicalPath}|${source.length()}|${source.lastModified()}"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            checkCancelled()
            val target = File(directory, "$key.wav")
            if (target.isFile && target.length() > 44) { target.setLastModified(System.currentTimeMillis()); return target }
            check(directory.isDirectory || directory.mkdirs()) { "Could not create the audio cache." }
            prune()
            val temp = Files.createTempFile(directory.toPath(), "decode-", ".tmp").toFile()
            try {
                when (source.extension.lowercase()) {
                    "flac" -> decodeFlac(source, temp, checkCancelled)
                    "m4a" -> decodeAac(source, temp, checkCancelled)
                    else -> decodeOpus(source, temp, checkCancelled)
                }
                checkCancelled()
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return target
            } finally { temp.delete() }
        }
    }
    private fun prune() {
        val files = directory.listFiles()?.filter { it.extension == "wav" }?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        for (file in files) { if (bytes < 1_500_000_000L) break; val size = file.length(); if (file.delete()) bytes -= size }
    }
    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0
    fun clear(): Int = directory.listFiles()?.count { it.isFile && it.delete() } ?: 0
}

private fun decodeAac(source: File, destination: File, checkCancelled: () -> Unit) {
    var wave: PcmWaveWriter? = null
    var frames = 0
    try {
        RandomAccessFile(source, "r").use { input ->
            val track = Mp4Aac(input)
            val decoder = Decoder(track.configuration)
            val buffer = SampleBuffer().apply { isBigEndian = false }
            var decoded = 0L; var skip = 0L
            for (index in track.sizes.indices) {
                checkCancelled()
                decoder.decodeFrame(track.read(index), buffer)
                require(buffer.channels in 1..2 && buffer.bitsPerSample == 16 && buffer.sampleRate in 8000..192000) { "Unsupported AAC output format." }
                if (wave == null) { wave = PcmWaveWriter(destination, buffer.sampleRate, buffer.channels); skip = track.skipTicks * buffer.sampleRate / track.mediaTimescale }
                val sampleCount = buffer.data.size / (buffer.channels * 2)
                val discarded = (skip - decoded).coerceIn(0, sampleCount.toLong()).toInt()
                wave!!.write(buffer.data.copyOfRange(discarded * buffer.channels * 2, buffer.data.size))
                decoded += sampleCount; frames++
            }
            track.durationTicks?.let { duration -> wave!!.trimFrames(minOf(decoded - skip, duration * buffer.sampleRate / track.movieTimescale)) }
            require(frames > 0) { "The AAC track contains no audio frames." }
        }
    } finally { wave?.close() }
}

private fun decodeFlac(source: File, destination: File, checkCancelled: () -> Unit) {
    var info: StreamInfo? = null
    var samples = 0L
    var wave: PcmWaveWriter? = null
    try {
        source.inputStream().buffered().use { input ->
            val decoder = FLACDecoder(input)
            decoder.addPCMProcessor(object : PCMProcessor {
                override fun processStreamInfo(stream: StreamInfo) {
                    require(stream.channels in 1..2 && stream.bitsPerSample in 8..24 && stream.sampleRate in 8000..192000) {
                        "FLAC playback currently supports mono/stereo, 8–24-bit, up to 192 kHz."
                    }
                    info = stream; wave = PcmWaveWriter(destination, stream.sampleRate, stream.channels)
                }
                override fun processPCM(pcm: ByteData) {
                    checkCancelled()
                    val stream = requireNotNull(info)
                    val width = (stream.bitsPerSample + 7) / 8
                    require(pcm.len % width == 0)
                    val output = ByteArray(pcm.len / width * 2)
                    for (index in 0 until pcm.len / width) {
                        var value = 0
                        for (byte in 0 until width) value = value or ((pcm.data[index * width + byte].toInt() and 255) shl (byte * 8))
                        // jFLAC's 8-bit output is unsigned; other widths are little-endian signed PCM.
                        value = if (width == 1) value - 128 else (value shl (32 - width * 8)) shr (32 - width * 8)
                        val scaled = if (stream.bitsPerSample > 16) value shr (stream.bitsPerSample - 16) else value shl (16 - stream.bitsPerSample)
                        output[index * 2] = scaled.toByte(); output[index * 2 + 1] = (scaled shr 8).toByte()
                    }
                    samples += pcm.len / width / stream.channels
                    wave!!.write(output)
                }
            })
            decoder.decode()
            require(samples > 0 && (info!!.totalSamples == 0L || samples == info!!.totalSamples)) { "FLAC is truncated or contains damaged frames." }
        }
    } finally { wave?.close() }
}

/** RFC 3533/7845: one Ogg Opus stream, mapping family 0, including pre-skip and end trimming. */
internal fun decodeOpus(source: File, destination: File, checkCancelled: () -> Unit = {}) {
    var channels = 0; var preSkip = 0; var gain = 1.0
    var decoder: OpusDecoder? = null; var wave: PcmWaveWriter? = null
    var packetIndex = 0; var decoded = 0L; var endGranule = -1L; var ended = false
    try {
        source.inputStream().buffered().use { input ->
            readOgg(input, checkCancelled) { packet, granule, eos ->
                checkCancelled()
                when (packetIndex++) {
                    0 -> {
                        require(packet.size >= 19 && packet.copyOfRange(0, 8).contentEquals("OpusHead".toByteArray())) { "This Ogg file is not Opus. Ogg Vorbis is not supported." }
                        require((packet[8].toInt() and 255) <= 15) { "Unsupported Opus header version." }
                        channels = packet[9].toInt() and 255
                        require(channels in 1..2 && packet[18] == 0.toByte()) { "Opus supports mono/stereo mapping family 0 in this build." }
                        preSkip = (packet[10].toInt() and 255) or ((packet[11].toInt() and 255) shl 8)
                        val gainDb = ((packet[16].toInt() and 255) or ((packet[17].toInt() and 255) shl 8)).toShort().toInt()
                        gain = 10.0.pow(gainDb / (20.0 * 256))
                        decoder = OpusDecoder(48000, channels); wave = PcmWaveWriter(destination, 48000, channels)
                    }
                    1 -> require(packet.size >= 16 && packet.copyOfRange(0, 8).contentEquals("OpusTags".toByteArray())) { "Missing Opus comment header." }
                    else -> {
                        require(!ended && packet.isNotEmpty() && packet.size <= 127_500) { "Invalid or chained Opus audio." }
                        val pcm = ShortArray(5760 * channels)
                        val count = decoder!!.decode(packet, 0, packet.size, pcm, 0, 5760, false)
                        require(count > 0)
                        val skip = (preSkip - decoded).coerceIn(0, count.toLong()).toInt()
                        val bytes = ByteArray((count - skip) * channels * 2)
                        for (i in skip * channels until count * channels) {
                            val value = (pcm[i] * gain).roundToInt().coerceIn(-32768, 32767)
                            val offset = (i - skip * channels) * 2
                            bytes[offset] = value.toByte(); bytes[offset + 1] = (value shr 8).toByte()
                        }
                        wave!!.write(bytes); decoded += count
                        if (eos) { require(granule in preSkip.toLong()..decoded) { "Invalid Opus end position." }; endGranule = granule; ended = true }
                    }
                }
            }
        }
        require(ended && packetIndex > 2) { "Opus stream is truncated (missing end page)." }
        wave!!.trimFrames(endGranule - preSkip)
    } finally { wave?.close() }
}

internal fun readOgg(input: InputStream, checkCancelled: () -> Unit = {}, packet: (ByteArray, Long, Boolean) -> Unit) {
    var serial: Int? = null; var sequence = 0
    val partial = ByteArrayOutputStream()
    var ended = false
    while (true) {
        checkCancelled()
        val first = input.read(); if (first < 0) break
        require(!ended) { "Chained or multiplexed Ogg streams are not supported." }
        val header = byteArrayOf(first.toByte()) + input.readExactly(26)
        require(header.copyOfRange(0, 4).contentEquals("OggS".toByteArray()) && header[4] == 0.toByte()) { "Invalid Ogg page." }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val flags = header[5].toInt() and 255
        val pageSerial = buffer.getInt(14)
        if (serial == null) { serial = pageSerial; require(flags and 2 != 0) { "Missing Ogg start page." } }
        require(serial == pageSerial && buffer.getInt(18) == sequence++) { "Missing, reordered or multiplexed Ogg pages." }
        require((flags and 1 != 0) == (partial.size() > 0)) { "Invalid Ogg packet continuation." }
        val lacing = input.readExactly(header[26].toInt() and 255)
        val body = input.readExactly(lacing.sumOf { it.toInt() and 255 })
        val checksum = buffer.getInt(22)
        for (i in 22..25) header[i] = 0
        require(oggCrc(header + lacing + body) == checksum) { "Ogg checksum failed; the file is damaged." }
        val completed = lacing.indexOfLast { (it.toInt() and 255) < 255 }
        var offset = 0
        lacing.forEachIndexed { index, value ->
            val length = value.toInt() and 255
            require(partial.size() + length <= 1_048_576) { "Ogg packet exceeds the supported size limit." }
            partial.write(body, offset, length); offset += length
            if (length < 255) {
                packet(partial.toByteArray(), if (index == completed) buffer.getLong(6) else -1, flags and 4 != 0 && index == completed)
                partial.reset()
            }
        }
        if (flags and 4 != 0) { require(partial.size() == 0 && completed >= 0); ended = true }
    }
    require(serial != null && ended && partial.size() == 0) { "Ogg stream is empty or truncated." }
}
internal fun oggCrc(bytes: ByteArray): Int {
    var crc = 0
    for (byte in bytes) {
        crc = crc xor ((byte.toInt() and 255) shl 24)
        repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x04c11db7 else crc shl 1 }
    }
    return crc
}
private fun InputStream.readExactly(count: Int): ByteArray = ByteArray(count).also { bytes ->
    var offset = 0
    while (offset < count) { val got = read(bytes, offset, count - offset); if (got <= 0) throw EOFException("Truncated Ogg page"); offset += got }
}

internal class PcmWaveWriter(file: File, private val rate: Int, private val channels: Int) : AutoCloseable {
    private val output = RandomAccessFile(file, "rw").apply { setLength(0); write(ByteArray(44)) }
    private var size = 0L
    fun write(bytes: ByteArray) { require(size + bytes.size <= 750_000_000L) { "Decoded audio exceeds the 750 MB per-track cache limit." }; output.write(bytes); size += bytes.size }
    fun trimFrames(frames: Long) { val bytes = frames * channels * 2; require(bytes in 0..size); size = bytes; output.setLength(44 + size) }
    override fun close() {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt((36 + size).toInt()).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort())
            .putShort(16).put("data".toByteArray()).putInt(size.toInt())
        output.seek(0); output.write(header.array()); output.close()
    }
}
