package com.podium.air.domain

import com.music.bitchord.data.model.Song
import kotlin.random.Random
import kotlin.test.*

class QueueTest {
    private fun song(id: String) = Song(id, id, "Artist", null)
    @Test fun duplicateEntriesAreIndependentlyEditable() {
        val q = PlaybackQueue.from(listOf(song("a"), song("a"), song("b")), 1)
        assertEquals(3, q.entries.map { it.key }.distinct().size)
        val r = q.remove(q.entries[0].key)
        assertEquals(q.current?.key, r.current?.key)
        assertEquals(2, r.entries.size)
    }
    @Test fun removalOfCurrentSelectsFollowingTrack() {
        val q = PlaybackQueue.from(listOf(song("a"), song("b"), song("c")), 1)
        assertEquals("c", q.remove(q.current!!.key).current?.song?.videoId)
    }
    @Test fun removingLastEntryLeavesNoSelection() {
        val q = PlaybackQueue.from(listOf(song("a")))
        assertNull(q.remove(q.current!!.key).current)
        assertEquals(-1, q.remove(q.current!!.key).cursor)
    }
    @Test fun repeatOneIsAutomaticOnly() {
        val q = PlaybackQueue.from(listOf(song("a"), song("b")), repeat = RepeatMode.ONE)
        assertEquals(0, q.nextIndex(automatic = true)); assertEquals(1, q.nextIndex())
    }
    @Test fun repeatAllWrapsAndRepeatOffStops() {
        val q = PlaybackQueue.from(listOf(song("a"), song("b")), 1)
        assertNull(q.nextIndex()); assertEquals(0, q.copy(repeat = RepeatMode.ALL).nextIndex())
        assertNull(PlaybackQueue().nextIndex())
    }
    @Test fun shuffleRestoresSurvivorsAndKeepsAddedTracks() {
        val q = PlaybackQueue.from(listOf(song("a"), song("a"), song("b"), song("c")), 1)
        val shuffled = q.toggleShuffle(Random(1))
        assertEquals(q.entries.take(2), shuffled.entries.take(2))
        val removed = q.entries[2].key
        val restored = shuffled.remove(removed).append(song("d")).toggleShuffle()
        assertEquals(listOf("a", "a", "c", "d"), restored.entries.map { it.song.videoId })
        assertEquals(q.current?.key, restored.current?.key)
    }
    @Test fun queueMovesKeepTheSameCurrentEntry() {
        val q = PlaybackQueue.from(listOf(song("a"), song("b"), song("c")), 1)
        assertEquals(q.current?.key, q.move(q.current!!.key, -1).current?.key)
        assertEquals(q.current?.key, q.move(q.entries.last().key, -2).current?.key)
    }
    @Test fun playNextGoesImmediatelyAfterCurrent() {
        val q = PlaybackQueue.from(listOf(song("a"), song("b"))).append(song("x"), true)
        assertEquals(listOf("a", "x", "b"), q.entries.map { it.song.videoId })
    }
}
