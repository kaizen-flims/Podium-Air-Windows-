package com.podium.air.domain
import com.music.bitchord.data.lyrics.EnhancedLrc
import kotlin.test.*
class LyricsTest {
    @Test fun wordTimingsAndEntitiesSurvivePort() {
        val lines = EnhancedLrc.parse("[00:01.00]<00:01.00>Let&#x27;s <00:02.00>go\n[00:04.00]<00:04.00>again")
        val line = lines.first { !it.isGap }
        assertEquals("Let's go", line.text)
        assertEquals(1000, line.words[0].startMs)
        assertEquals(2000, line.words[0].endMs)
        assertTrue(line.revealedChars(1500) in 2.0f..3.0f)
    }
    @Test fun ordinaryLrcFallsBackToLineParser() { assertTrue(EnhancedLrc.parse("[00:01.00]Hello").isEmpty()) }
}
