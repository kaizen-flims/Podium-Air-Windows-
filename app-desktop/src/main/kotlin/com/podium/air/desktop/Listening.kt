// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import kotlinx.serialization.Serializable

@Serializable
data class ListeningEntry(val trackId: String, val day: String, val milliseconds: Long = 0, val plays: Int = 0)

/** Counts elapsed audible time, never a seek distance or a file's advertised duration. */
internal class ListeningRecorder {
    private var observedAt: Long? = null
    private var previousTrack: String? = null
    private var previousDay = ""
    private var previousPlaying = false
    private var countedKey: String? = null
    private val pending = mutableMapOf<Pair<String, String>, ListeningEntry>()
    fun sample(trackId: String?, entryKey: String?, playing: Boolean, nowMs: Long, day: String) {
        val elapsed = observedAt?.let { (nowMs - it).coerceIn(0, 2000) } ?: 0
        if (previousPlaying && previousTrack != null && elapsed > 0) add(previousTrack!!, previousDay, elapsed, 0)
        if (playing && trackId != null && entryKey != countedKey) { add(trackId, day, 0, 1); countedKey = entryKey }
        previousTrack = trackId; previousDay = day; previousPlaying = playing; observedAt = nowMs
    }
    private fun add(id: String, day: String, ms: Long, plays: Int) {
        val key = id to day; val old = pending[key] ?: ListeningEntry(id, day)
        pending[key] = old.copy(milliseconds = old.milliseconds + ms, plays = old.plays + plays)
    }
    fun drain(): List<ListeningEntry> = pending.values.toList().also { pending.clear() }
}
internal fun mergeListening(existing: List<ListeningEntry>, added: List<ListeningEntry>): List<ListeningEntry> =
    (existing + added).groupBy { it.trackId to it.day }.map { (_, values) ->
        values.first().copy(milliseconds = values.sumOf { it.milliseconds }, plays = values.sumOf { it.plays })
    }.sortedByDescending { it.day }.take(20000)
