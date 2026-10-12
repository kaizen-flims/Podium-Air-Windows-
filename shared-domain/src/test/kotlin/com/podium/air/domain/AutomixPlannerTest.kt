// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.domain

import com.music.bitchord.playback.smart.*
import kotlin.test.*

class AutomixPlannerTest {
    @Test fun completedInteriorCueIsReplannedAtTheActualPlayhead() {
        val analysis = TrackAnalysis(status = "ready", duration = 120.0, contentEndTime = 120.0,
            mixOutCandidates = listOf(MixCandidate(108.0, 2.0, "outro_start")))
        val incoming = TrackAnalysis(status = "ready", duration = 120.0)
        val early = planTransition(analysis, incoming, currentTime = 0.0, duration = 120.0, mode = CrossfadeMode.SMART)
        val late = planTransition(analysis, incoming, currentTime = 110.0, duration = 120.0, mode = CrossfadeMode.SMART)
        assertEquals(108.0, early.transitionEnd)
        assertEquals(120.0, late.transitionEnd)
        assertFalse(late.blocked); assertTrue(late.transitionStart > 110.0)
    }
    @Test fun uncertaintyCannotAuthorizeTimeStretch() {
        val metadataOnly = TrackAnalysis(bpm = 120.0, duration = 180.0, key = "C minor")
        assertEquals(TransitionTier.PLAIN_CROSSFADE, assessTransitionTier(metadataOnly, metadataOnly).tier)
        val fade = planTransition(metadataOnly, metadataOnly, duration = 180.0, mode = CrossfadeMode.SMART)
        assertEquals(TransitionStyle.EQUAL_POWER, fade.transitionStyle)
        assertEquals(1.0, fade.incomingPlaybackRate)
    }
    @Test fun shortOrSpokenTracksDoNotMix() {
        assertTrue(planTransition(duration = 20.0, mode = CrossfadeMode.SMART).blocked)
        val speech = TransitionTrackInfo("podcast", 180000, "Podcast episode")
        assertTrue(planTransition(currentTrack = speech, duration = 180.0, mode = CrossfadeMode.SMART).blocked)
    }
    @Test fun staleAnalysisAndNonfiniteValuesFallBackSafely() {
        val stale = TrackAnalysis(status = "loading", trackId = "old", bpm = Double.NaN)
        val result = planTransition(stale, stale, TransitionTrackInfo("new", 180000), duration = 180.0, currentTime = 178.0, mode = CrossfadeMode.SMART)
        assertTrue(result.shouldStart)
        assertEquals(TransitionStyle.EQUAL_POWER, result.transitionStyle)
        assertTrue(result.fadeSeconds.isFinite())
    }
    @Test fun octaveTempoIsNormalizedAndVocalEvidenceIsRespected() {
        assertEquals(126.0, alignTempoOctave(126.0, 63.0))
        val analysis = TrackAnalysis(energyCurve = listOf(EnergySample(0.0, 1.0), EnergySample(1.0, 1.0)), vocalActivityMask = listOf(0.9, 0.8))
        assertTrue(isVocalClash(vocalActivityBetween(analysis, 0.0, 2.0), 0.85))
        assertNull(vocalActivityBetween(TrackAnalysis(), 0.0, 2.0))
    }
}

