// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

/** Slow artwork-derived color motion; disabled while paused or when reduced motion is requested. */
@Composable
internal fun ArtworkBackground(uri: String?, preferences: Preferences) {
    var palette by remember(uri) { mutableStateOf(listOf(AccentRed)) }
    LaunchedEffect(uri) {
        palette = withContext(Dispatchers.IO) {
            runCatching {
                val image = uri?.let { ImageIO.read(File(URI(it))) } ?: return@runCatching listOf(AccentRed)
                val buckets = mutableMapOf<Int, Int>()
                for (x in 0 until 24) for (y in 0 until 24) {
                    val rgb = image.getRGB(x * image.width / 24, y * image.height / 24)
                    val key = ((rgb shr 16 and 255) / 32 shl 6) or ((rgb shr 8 and 255) / 32 shl 3) or ((rgb and 255) / 32)
                    buckets[key] = (buckets[key] ?: 0) + 1
                }
                buckets.entries.sortedByDescending { it.value }.take(3).map { (key, _) ->
                    Color(((key shr 6 and 7) * 32 + 16) / 255f, ((key shr 3 and 7) * 32 + 16) / 255f, ((key and 7) * 32 + 16) / 255f)
                }.ifEmpty { listOf(AccentRed) }
            }.getOrDefault(listOf(AccentRed))
        }
    }
    val transition = rememberInfiniteTransition(label = "artwork")
    val phase = if (!preferences.reducedMotion) transition.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(32000, easing = LinearEasing)), label = "artwork drift").value else 0f
    Canvas(Modifier.fillMaxSize()) {
        palette.forEachIndexed { index, color ->
            val angle = phase + index * 2.1f
            val center = Offset(size.width * (0.5f + 0.32f * cos(angle)), size.height * (0.5f + 0.32f * sin(angle)))
            drawRect(Brush.radialGradient(listOf(color.copy(alpha = if (preferences.dark) 0.26f else 0.15f), Color.Transparent), center, size.maxDimension * 0.75f))
        }
    }
}
