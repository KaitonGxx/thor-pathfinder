package com.thorpathfinder.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.PhysicalButton

/** The symbol shown beside a button's name on its card. */
internal fun buttonSymbol(button: PhysicalButton): ImageVector = when (button) {
    PhysicalButton.BACK -> BackSymbol
    PhysicalButton.HOME -> Icons.Filled.Home
    PhysicalButton.AYN -> AynSymbol
    PhysicalButton.SELECT -> SelectSymbol
    PhysicalButton.START -> Icons.Filled.PlayArrow
    PhysicalButton.L3, PhysicalButton.R3 -> StickSymbol
}

/**
 * The AYN button as it looks: a rounded rectangle with AYN inside. The letters
 * are all straight strokes, so they are drawn as lines rather than text.
 */
private val AynSymbol: ImageVector = ImageVector.Builder(
    name = "AynSymbol",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val ink = SolidColor(Color.Black)
    path(stroke = ink, strokeLineWidth = 1.5f) {
        moveTo(3.5f, 6.5f)
        lineTo(20.5f, 6.5f)
        quadTo(23f, 6.5f, 23f, 9f)
        lineTo(23f, 15f)
        quadTo(23f, 17.5f, 20.5f, 17.5f)
        lineTo(3.5f, 17.5f)
        quadTo(1f, 17.5f, 1f, 15f)
        lineTo(1f, 9f)
        quadTo(1f, 6.5f, 3.5f, 6.5f)
        close()
    }
    path(stroke = ink, strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        // A
        moveTo(4.3f, 15f)
        lineTo(6.5f, 9f)
        lineTo(8.7f, 15f)
        moveTo(5.03f, 13f)
        lineTo(7.97f, 13f)
        // Y
        moveTo(10f, 9f)
        lineTo(12f, 12f)
        lineTo(14f, 9f)
        moveTo(12f, 12f)
        lineTo(12f, 15f)
        // N
        moveTo(15.3f, 15f)
        lineTo(15.3f, 9f)
        lineTo(19.7f, 15f)
        lineTo(19.7f, 9f)
    }
}.build()

/** An analog stick seen from above: the ring it moves in, and its cap. */
private val StickSymbol: ImageVector = ImageVector.Builder(
    name = "StickSymbol",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val ink = SolidColor(Color.Black)
    path(stroke = ink, strokeLineWidth = 2f) {
        moveTo(4f, 12f)
        arcToRelative(8f, 8f, 0f, true, true, 16f, 0f)
        arcToRelative(8f, 8f, 0f, true, true, -16f, 0f)
        close()
    }
    path(fill = ink) {
        moveTo(7.8f, 12f)
        arcToRelative(4.2f, 4.2f, 0f, true, true, 8.4f, 0f)
        arcToRelative(4.2f, 4.2f, 0f, true, true, -8.4f, 0f)
        close()
    }
}.build()

/**
 * A nearly full circle with its arrowhead at the top, pointing left: Material's
 * "replay" symbol, which the core icon set doesn't carry.
 */
private val BackSymbol: ImageVector = ImageVector.Builder(
    name = "BackSymbol",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(
        "M12,5V1L7,6l5,5V7c3.31,0 6,2.69 6,6s-2.69,6 -6,6 -6,-2.69 -6,-6H4" +
            "c0,4.42 3.58,8 8,8s8,-3.58 8,-8 -3.58,-8 -8,-8z",
    ).toNodes(),
    fill = SolidColor(Color.Black),
).build()

/** An outlined square with softened corners; filled, it would read as "stop". */
private val SelectSymbol: ImageVector = ImageVector.Builder(
    name = "SelectSymbol",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
        moveTo(7f, 5f)
        lineTo(17f, 5f)
        quadTo(19f, 5f, 19f, 7f)
        lineTo(19f, 17f)
        quadTo(19f, 19f, 17f, 19f)
        lineTo(7f, 19f)
        quadTo(5f, 19f, 5f, 17f)
        lineTo(5f, 7f)
        quadTo(5f, 5f, 7f, 5f)
        close()
    }
}.build()
