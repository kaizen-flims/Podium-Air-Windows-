// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import com.music.bitchord.playback.smart.TrackFeatures

/** Stores only Podium Air credentials in the current Windows user's credential set. */
interface SecretStore {
    fun read(): String?
    fun write(value: String)
    fun delete()
}

internal class WindowsSecretStore(private val key: String = "listenbrainz") : SecretStore {
    init { require(key.matches(Regex("[a-zA-Z0-9-]{1,100}"))) }
    private fun ready() { check(TrackFeatures.available) { "Windows credential storage is unavailable." } }
    override fun read(): String? { ready(); return WindowsCredentials.read(key)?.let { bytes -> try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) } } }
    override fun write(value: String) {
        ready(); val bytes = value.toByteArray(Charsets.UTF_8)
        try { require(bytes.size in 1..2048); WindowsCredentials.write(key, bytes) } finally { bytes.fill(0) }
    }
    override fun delete() { ready(); WindowsCredentials.delete(key) }
}

internal object WindowsCredentials {
    external fun read(key: String): ByteArray?
    external fun write(key: String, value: ByteArray)
    external fun delete(key: String)
}
