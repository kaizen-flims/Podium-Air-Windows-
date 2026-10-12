// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import kotlinx.serialization.Serializable

@Serializable
data class ListeningEntry(val trackId: String, val day: String, val milliseconds: Long = 0, val plays: Int = 0, val hourMilliseconds: Map<Int, Long> = emptyMap())

/** Counts elapsed audible time, never a seek distance or a file's advertised duration. */
internal class ListeningRecorder {
    private var observedAt: Long? = null
    private var previousTrack: String? = null
    private var previousDay = ""
    private var previousPlaying = false
    private var previousHour = -1
    private var countedKey: String? = null
    private val pending = mutableMapOf<Pair<String, String>, ListeningEntry>()
    fun sample(trackId: String?, entryKey: String?, playing: Boolean, nowMs: Long, day: String, hour: Int = -1) {
        val elapsed = observedAt?.let { (nowMs - it).coerceIn(0, 2000) } ?: 0
        if (previousPlaying && previousTrack != null && elapsed > 0) add(previousTrack!!, previousDay, elapsed, 0, previousHour)
        if (playing && trackId != null && entryKey != countedKey) { add(trackId, day, 0, 1); countedKey = entryKey }
        previousTrack = trackId; previousDay = day; previousPlaying = playing; observedAt = nowMs; previousHour = hour
    }
    private fun add(id: String, day: String, ms: Long, plays: Int, hour: Int = -1) {
        val key = id to day; val old = pending[key] ?: ListeningEntry(id, day)
        val hours = if (hour in 0..23 && ms > 0) old.hourMilliseconds + (hour to ((old.hourMilliseconds[hour] ?: 0) + ms)) else old.hourMilliseconds
        pending[key] = old.copy(milliseconds = old.milliseconds + ms, plays = old.plays + plays, hourMilliseconds = hours)
    }
    fun drain(): List<ListeningEntry> = pending.values.toList().also { pending.clear() }
}
internal fun mergeListening(existing: List<ListeningEntry>, added: List<ListeningEntry>): List<ListeningEntry> =
    (existing + added).groupBy { it.trackId to it.day }.map { (_, values) ->
        val hours = values.flatMap { it.hourMilliseconds.entries }.groupBy { it.key }.mapValues { (_, rows) -> rows.sumOf { it.value } }
        values.first().copy(milliseconds = values.sumOf { it.milliseconds }, plays = values.sumOf { it.plays }, hourMilliseconds = hours)
    }.sortedByDescending { it.day }.take(20000)

