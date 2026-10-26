package com.conreo.couchytv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fork: smooth edges on older Android TV.
 *
 * The squircle (SmoothCornerShape) is a generic path, and before Android 13 a path clip is not
 * anti-aliased: every card, the dock and the status-bar panels had stair-stepped edges on an Android 9
 * box. A rounded rectangle clips through the view outline, which IS smoothed. At these radii the
 * squircle's easing is barely visible; the jaggies were not.
 */
fun smoothShape(radius: Dp): Shape =
    if (Build.VERSION.SDK_INT >= 33) SmoothCornerShape(radius) else RoundedCornerShape(radius)

/**
 * Fork: the glass for the dock and the status-bar panels. There is no backdrop blur before Android 12
 * (and none over a SurfaceView video at all), so the glass is light rather than frost: a body that is
 * a shade lighter at the top, a lit hairline rim, and enough tint that icons and text read over any
 * video wallpaper.
 */
fun Modifier.glassPanel(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0x8C1C2128), Color(0xB30B0D11))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))), shape)
