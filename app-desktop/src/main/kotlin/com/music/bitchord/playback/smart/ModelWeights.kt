// SPDX-License-Identifier: GPL-3.0-only
package com.music.bitchord.playback.smart

import com.podium.air.desktop.defaultDataDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Exact Android assets, verified at build time and again before local extraction. */
object ModelWeights {
    private val hashes = mapOf(
        "beat_this_int8.onnx" to "9dc29f1fcd713d18f48a2755109fce01429ba6d1639607af8ae5c7449b47070f",
        "vocals_umxhq_int8.onnx" to "a2be987b55a29bc149d3a6ae99b08175d81f85ee292a8ea21f96c3a473bc94cb",
    )
    @Synchronized fun extract(name: String): File {
        val expected = requireNotNull(hashes[name]) { "Unknown Automix model." }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val directory = File(defaultDataDirectory(), "automix/models-$expected").apply { mkdirs() }
        val target = File(directory, name)
        if (target.isFile && target.length() in 1..20_000_000 && hash(target.readBytes()) == expected) return target
        val bytes = requireNotNull(javaClass.getResourceAsStream("/models/$name")) { "The Automix model is missing from this installation." }.use { it.readNBytes(20_000_001) }
        check(bytes.size <= 20_000_000 && hash(bytes) == expected) { "The Automix model checksum is invalid." }
        val temporary = Files.createTempFile(directory.toPath(), "model-", ".onnx")
        try { Files.write(temporary, bytes); Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temporary) }
        return target
    }
}
