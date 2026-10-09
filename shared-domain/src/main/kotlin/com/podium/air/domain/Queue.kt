// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.domain

import com.music.bitchord.data.model.Song
import java.util.UUID
import kotlin.random.Random

data class QueueEntry(val key: String = UUID.randomUUID().toString(), val song: Song)
enum class RepeatMode { OFF, ALL, ONE }

/** Entry identity is independent of song identity, so duplicate tracks remain editable. */
data class PlaybackQueue(
    val entries: List<QueueEntry> = emptyList(),
    val cursor: Int = -1,
    val repeat: RepeatMode = RepeatMode.OFF,
    val originalOrder: List<String>? = null,
) {
    val current: QueueEntry? get() = entries.getOrNull(cursor)
    val shuffled: Boolean get() = originalOrder != null
    fun nextIndex(automatic: Boolean = false): Int? = when {
        entries.isEmpty() -> null
        automatic && repeat == RepeatMode.ONE && current != null -> cursor
        cursor + 1 in entries.indices -> cursor + 1
        repeat == RepeatMode.ALL -> 0
        else -> null
    }
    fun next(automatic: Boolean = false) = nextIndex(automatic)?.let { copy(cursor = it) } ?: this
    fun previous() = if (entries.isEmpty()) this else copy(cursor = (cursor - 1).coerceAtLeast(0))
    fun select(index: Int) = if (index in entries.indices) copy(cursor = index) else this
    fun append(song: Song, playNext: Boolean = false): PlaybackQueue {
        val item = QueueEntry(song = song)
        val at = if (playNext && cursor >= 0) cursor + 1 else entries.size
        return copy(entries = entries.toMutableList().apply { add(at, item) }, cursor = if (cursor < 0) 0 else cursor)
    }
    fun remove(key: String): PlaybackQueue {
        val at = entries.indexOfFirst { it.key == key }
        if (at < 0) return this
        val remaining = entries.filterNot { it.key == key }
        val newCursor = when { remaining.isEmpty() -> -1; at < cursor -> cursor - 1; else -> cursor.coerceAtMost(remaining.lastIndex) }
        return copy(entries = remaining, cursor = newCursor)
    }
    fun move(key: String, offset: Int): PlaybackQueue {
        val from = entries.indexOfFirst { it.key == key }
        if (from < 0) return this
        val to = (from + offset).coerceIn(entries.indices)
        val selected = current?.key
        val reordered = entries.toMutableList().apply { add(to, removeAt(from)) }
        return copy(entries = reordered, cursor = reordered.indexOfFirst { it.key == selected })
    }
    fun toggleShuffle(random: Random = Random.Default): PlaybackQueue {
        if (originalOrder != null) {
            val order = originalOrder.withIndex().associate { it.value to it.index }
            val selected = current?.key
            val restored = entries.sortedBy { order[it.key] ?: Int.MAX_VALUE }
            return copy(entries = restored, cursor = restored.indexOfFirst { it.key == selected }, originalOrder = null)
        }
        if (current == null) return this
        // Android policy: keep already played items and the current track in place.
        return copy(entries = entries.take(cursor + 1) + entries.drop(cursor + 1).shuffled(random), originalOrder = entries.map { it.key })
    }
    companion object {
        fun from(songs: List<Song>, start: Int = 0, repeat: RepeatMode = RepeatMode.OFF) = PlaybackQueue(
            songs.map { QueueEntry(song = it) }, if (songs.isEmpty()) -1 else start.coerceIn(songs.indices), repeat,
        )
    }
}
