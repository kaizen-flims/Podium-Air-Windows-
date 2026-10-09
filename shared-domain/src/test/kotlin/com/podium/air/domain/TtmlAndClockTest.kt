package com.podium.air.domain

import com.music.bitchord.data.lyrics.*
import com.music.bitchord.ui.player.LyricClockReconciler
import com.music.bitchord.ui.player.activeLyricRows
import kotlin.test.*

class TtmlAndClockTest {
    @Test fun ttmlKeepsSyllablesBackingVocalsAndDuetAlignment() {
        val xml = """<tt xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
            <p begin="1.0" end="3.0" ttm:agent="v1"><span begin="1.0" end="1.3">Hel</span><span begin="1.3" end="1.8">lo </span><span begin="1.8" end="2.2">world</span><span ttm:role="x-bg"><span begin="2.0" end="3.5">echo</span></span></p>
            <p begin="3.0" end="4.0" ttm:agent="v2">Reply</p>
            <p begin="4.0" end="5.0" ttm:agent="v1">Return</p>
            </div></body></tt>"""
        val lines = TtmlLyrics.parse(xml).filterNot { it.isGap }
        assertEquals("Hello world", lines[0].text)
        assertEquals(listOf("Hello", "world"), lines[0].words.map { it.text })
        assertEquals(1000L, lines[0].words[0].startMs); assertEquals(1800L, lines[0].words[0].endMs)
        assertEquals("echo", lines[0].background!!.text)
        assertEquals(listOf(LyricAlignment.Start, LyricAlignment.End, LyricAlignment.Start), lines.map { it.alignment })
        assertEquals(listOf(0, 1), activeLyricRows(lines, 3200))
    }
    @Test fun ttmlRefusesExternalEntitiesAndMalformedDocuments() {
        assertTrue(TtmlLyrics.parse("<!DOCTYPE tt [<!ENTITY secret SYSTEM 'file:///does-not-exist'>]><tt><p begin='1s'>&secret;</p></tt>").isEmpty())
        assertTrue(TtmlLyrics.parse("<tt><p").isEmpty())
    }
    @Test fun lyricClockStaysMonotonicOnDelayedReportsButResetsOnSeekAndPause() {
        val clock = LyricClockReconciler(1000, 0, true)
        assertEquals(1600, clock.reconcile(1600, 1450, 500, true))
        assertEquals(20000, clock.reconcile(1700, 20000, 600, true))
        assertEquals(20100, clock.reconcile(20200, 20100, 700, false))
    }
}
