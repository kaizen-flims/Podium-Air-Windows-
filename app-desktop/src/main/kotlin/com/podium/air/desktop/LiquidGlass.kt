// SPDX-License-Identifier: GPL-3.0-only
// Integration adapted from Podium Air/BitChord LiquidGlass.kt at 48902e6b20fdcfeb1723e02d4744849e82d5067a.
// Rendering: Kashif-E/KMPLiquidGlass (Kyant0/backdrop family), Apache-2.0; bundled license and notices.
package com.podium.air.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow

internal val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
internal val LocalGlassEnabled = staticCompositionLocalOf { false }
internal val LocalChromeInset = staticCompositionLocalOf { 0.dp }

/** The Android parameters in logical pixels, using the native Skia backdrop pipeline. */
@Composable internal fun Modifier.liquidGlass(shape: CornerBasedShape): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val scheme = MaterialTheme.colorScheme
    if (!LocalGlassEnabled.current || backdrop == null) return clip(shape)
        .background(scheme.surface, shape).border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
    val density = LocalDensity.current
    val tint = if (scheme.surface.luminance() > 0.5f) Color(0xFFFAFAFA) else Color(0xFF121212)
    val blurRadius = with(density) { 8.dp.toPx() }
    val refraction = with(density) { 24.dp.toPx() }
    return drawBackdrop(
        backdrop = backdrop, shape = { shape },
        effects = {
            colorControls(saturation = 1.5f)
            blur(blurRadius)
            androidGlassLens(refractionHeight = refraction, refractionAmount = refraction)
        },
        highlight = { Highlight.Default }, shadow = { Shadow.Default },
        onDrawSurface = { drawRect(tint.copy(alpha = 0.4f)) },
    ).clip(shape)
}
