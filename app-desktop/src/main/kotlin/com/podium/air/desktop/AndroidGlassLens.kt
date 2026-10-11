/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations.
 * Adapted from Podium Air's vendored Kyant0/backdrop v2.0.0 Lens.kt and Shaders.kt,
 * Android source 48902e6b20fdcfeb1723e02d4744849e82d5067a.
 * Windows changes: bind the original dispersion shader to a Skia image filter;
 * cache the compiled effect and close temporary builders after filter creation.
 */
package com.podium.air.desktop

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.LayoutDirection
import com.kashif_e.backdrop.BackdropEffectScope
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

private val androidLensEffect by lazy { RuntimeEffect.makeForShader(AndroidGlassRefractionShader) }

internal fun BackdropEffectScope.androidGlassLens(refractionHeight: Float, refractionAmount: Float) {
    if (refractionHeight <= 0 || refractionAmount <= 0) return
    val corners = shape as? CornerBasedShape ?: return
    padding = (padding - refractionHeight).coerceAtLeast(0f)
    val ltr = layoutDirection == LayoutDirection.Ltr
    val radii = listOf(if (ltr) corners.topStart else corners.topEnd, if (ltr) corners.topEnd else corners.topStart,
        if (ltr) corners.bottomEnd else corners.bottomStart, if (ltr) corners.bottomStart else corners.bottomEnd)
        .map { it.toPx(size, this).coerceAtMost(size.minDimension / 2f) }
    RuntimeShaderBuilder(androidLensEffect).use { builder ->
        builder.uniform("size", size.width, size.height)
        builder.uniform("offset", -padding, -padding)
        builder.uniform("cornerRadii", radii[0], radii[1], radii[2], radii[3])
        builder.uniform("refractionHeight", refractionHeight)
        builder.uniform("refractionAmount", -refractionAmount)
        builder.uniform("depthEffect", 1f)
        builder.uniform("chromaticAberration", 1f)
        ImageFilter.makeRuntimeShader(runtimeShaderBuilder = builder, shaderNames = arrayOf("content"), inputs = arrayOf(imageFilter))?.let { imageFilter = it }
    }
}

private const val AndroidGlassRefractionShader = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;
    
    half4 color = half4(0.0);
    
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;
    
    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    
    return color;
}"""
