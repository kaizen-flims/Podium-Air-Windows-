// Reused from Podium Air source commit 48902e6b20fdcfeb1723e02d4744849e82d5067a.
// Windows adaptation: 9 October 2026. GPL-3.0; original implementation and comments retained.
package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine

/** Keep every unfinished vocal visible, including overlaps spanning more than two rows. */
// Visibility made public for the separate desktop module.
fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    val latest = lines.indexOfLast { it.timeMs <= positionMs }
    if (latest < 0) return emptyList()
    return (0..latest).filter { index ->
        val line = lines[index]
        index == latest || (!line.isGap &&
            (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
            line.timeMs <= positionMs && positionMs < line.endMs)
    }
}
