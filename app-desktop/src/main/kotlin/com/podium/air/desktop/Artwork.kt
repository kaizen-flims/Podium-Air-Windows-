// SPDX-License-Identifier: GPL-3.0-only
// Desktop adapter of Podium Air's full-artwork blur and pair-of-textures fade.
// Source: ArtworkMeshBackdrop.kt, Android commit 48902e6b20fdcfeb1723e02d4744849e82d5067a.
package com.podium.air.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** One prepared 128px blur: no full-screen blur or perpetual drift/render loop. */
@Composable
internal fun ArtworkBackground(uri: String?, preferences: Preferences) {
    var prepared by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        prepared = withContext(Dispatchers.IO) {
            ArtworkBackdropCache.shared.load(uri)?.toComposeImageBitmap()
        }
    }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var incoming by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(prepared, preferences.reducedMotion) {
        val next = prepared
        incoming?.let { shown = it }
        incoming = null
        if (next == null || shown == null || preferences.reducedMotion) {
            shown = next
        } else if (next !== shown) {
            incoming = next
            fade.snapTo(0f)
            fade.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            shown = next
            incoming = null
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        shown?.let { drawArtworkCrop(it, 1f) }
        incoming?.let { drawArtworkCrop(it, fade.value) }
        // Desktop respects the selected light/dark theme and keeps dense lyrics legible.
        val scrim = if (preferences.dark) Color.Black else Color.White
        drawRect(Brush.verticalGradient(
            0f to scrim.copy(alpha = if (preferences.dark) 0.62f else 0.84f),
            0.55f to scrim.copy(alpha = if (preferences.dark) 0.70f else 0.90f),
            1f to scrim.copy(alpha = if (preferences.dark) 0.78f else 0.94f),
        ))
    }
}

private fun DrawScope.drawArtworkCrop(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0.001f || size.width < 1f || size.height < 1f) return
    val ratio = size.width / size.height
    val sourceWidth = minOf(image.width, (image.height * ratio).roundToInt()).coerceAtLeast(1)
    val sourceHeight = minOf(image.height, (image.width / ratio).roundToInt()).coerceAtLeast(1)
    drawImage(image,
        srcOffset = IntOffset((image.width - sourceWidth) / 2, (image.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight), dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha, filterQuality = FilterQuality.Low)
}
