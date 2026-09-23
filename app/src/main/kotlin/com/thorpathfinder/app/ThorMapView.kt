package com.thorpathfinder.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * The Thor drawn in outline, with a box beside every button that carries a
 * shortcut in the profile just switched to.
 *
 * The picture is Pathfinder's own: a lid, a control deck, two sticks, a
 * D-pad, four face buttons and the five small buttons, laid out where they
 * are on the real thing (see [ButtonMap.SPOTS]). Boxes hang off the nearer
 * side and are stacked in the order the buttons run down the device, each
 * joined to its button by a line.
 */
@SuppressLint("ViewConstructor")
class ThorMapView(
    context: Context,
    private val title: String,
    private val callouts: List<ButtonMap.Callout>,
    private val onDismiss: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density

    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF21B1F1B.toInt() }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFBFD7BF.toInt()
        strokeWidth = 2f * density
    }
    private val screenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33BFD7BF }
    private val control = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF7F9A7F.toInt()
        strokeWidth = 1.6f * density
    }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9CCC9C.toInt()
        strokeWidth = 2.4f * density
    }
    private val leader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9CCC9C.toInt()
        strokeWidth = 1.6f * density
    }
    private val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E9CCC9C }
    private val titleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 17f * density
        isFakeBoldText = true
    }
    private val nameText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
        isFakeBoldText = true
    }
    private val lineText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD6E4D6.toInt()
        textSize = 12f * density
    }
    private val buttonFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x469CCC9C }
    private val buttonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f * density
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    /** Where Dismiss is, for the tap. */
    private val dismissBox = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = 12f * density
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            18f * density,
            18f * density,
            panel,
        )
        canvas.drawText(title, pad + 6f * density, pad + titleText.textSize, titleText)

        val top = pad + titleText.textSize + 10f * density
        // The picture keeps the Thor's shape; the boxes take the room left over,
        // and Dismiss keeps a strip of its own along the bottom.
        val buttonHeight = 36f * density
        val pictureHeight = height - top - pad - buttonHeight - 10f * density
        val pictureWidth = pictureHeight * DEVICE_ASPECT
        val left = (width - pictureWidth) / 2f
        val device = RectF(left, top, left + pictureWidth, top + pictureHeight)

        drawThor(canvas, device)
        drawCallouts(canvas, device, pad)
        drawDismiss(canvas, buttonHeight, pad)
    }

    /** The way out. The whole panel takes a tap too, so a miss still works. */
    private fun drawDismiss(canvas: Canvas, buttonHeight: Float, pad: Float) {
        val buttonWidth = 150f * density
        val centreX = width / 2f
        dismissBox.set(
            centreX - buttonWidth / 2f,
            height - pad - buttonHeight,
            centreX + buttonWidth / 2f,
            height - pad,
        )
        val radius = buttonHeight / 2f
        canvas.drawRoundRect(dismissBox, radius, radius, buttonFill)
        canvas.drawRoundRect(dismissBox, radius, radius, leader)
        val baseline = dismissBox.centerY() - (buttonText.descent() + buttonText.ascent()) / 2f
        canvas.drawText("Dismiss", centreX, baseline, buttonText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Anywhere on the panel puts it away, so a stray tap can never strand
        // the user behind a window they cannot see past.
        if (event.action == MotionEvent.ACTION_UP) onDismiss()
        return true
    }

    /** The device itself: lid above, control deck below. */
    private fun drawThor(canvas: Canvas, d: RectF) {
        val w = d.width()
        val h = d.height()
        fun x(f: Float) = d.left + f * w
        fun y(f: Float) = d.top + f * h
        val corner = 0.035f * w

        // Lid, with the top screen inside it.
        canvas.drawRoundRect(RectF(x(0.02f), y(0f), x(0.98f), y(0.475f)), corner, corner, outline)
        canvas.drawRoundRect(RectF(x(0.07f), y(0.04f), x(0.93f), y(0.435f)), corner / 2f, corner / 2f, screenFill)

        // Control deck, with the bottom screen inside it.
        canvas.drawRoundRect(RectF(x(0f), y(0.495f), x(1f), y(1f)), corner, corner, outline)
        canvas.drawRoundRect(RectF(x(0.25f), y(0.564f), x(0.726f), y(0.915f)), corner / 2f, corner / 2f, screenFill)

        // Sticks: left one above the D-pad, right one below the face buttons.
        canvas.drawCircle(x(0.088f), y(0.660f), 0.055f * w, control)
        canvas.drawCircle(x(0.874f), y(0.819f), 0.055f * w, control)

        // D-pad, as a plus.
        val arm = 0.05f * w
        val thick = 0.018f * w
        val cx = x(0.088f)
        val cy = y(0.819f)
        canvas.drawRoundRect(RectF(cx - thick, cy - arm, cx + thick, cy + arm), thick / 2f, thick / 2f, control)
        canvas.drawRoundRect(RectF(cx - arm, cy - thick, cx + arm, cy + thick), thick / 2f, thick / 2f, control)

        // The four face buttons, in a diamond.
        val fx = x(0.874f)
        val fy = y(0.666f)
        val spread = 0.042f * w
        val dot = 0.017f * w
        canvas.drawCircle(fx, fy - spread, dot, control)
        canvas.drawCircle(fx, fy + spread, dot, control)
        canvas.drawCircle(fx - spread, fy, dot, control)
        canvas.drawCircle(fx + spread, fy, dot, control)

        // The small buttons Pathfinder can map, drawn whether mapped or not.
        for ((button, spot) in ButtonMap.SPOTS) {
            if (button == PhysicalButton.L3 || button == PhysicalButton.R3) continue
            val bx = x(spot.x)
            val by = y(spot.y)
            if (button == PhysicalButton.AYN) {
                canvas.drawRoundRect(
                    RectF(bx - 0.035f * w, by - 0.014f * w, bx + 0.035f * w, by + 0.014f * w),
                    0.014f * w,
                    0.014f * w,
                    control,
                )
            } else {
                canvas.drawCircle(bx, by, 0.018f * w, control)
            }
        }
    }

    /**
     * A box per mapped button, stacked down whichever side it belongs to.
     *
     * Stacking them in the order the buttons run down the device is not
     * enough: a button just below the boxes takes a short steep line, one at
     * the bottom centre takes a long shallow one, and the two cross. Two
     * leaders that cross can always be uncrossed by swapping their boxes, and
     * that swap always shortens them, so neighbours are swapped while it
     * shortens the total and the crossings go on their own.
     */
    private fun drawCallouts(canvas: Canvas, d: RectF, pad: Float) {
        val boxWidth = (d.left - pad * 2f).coerceAtLeast(60f * density)
        for (side in ButtonMap.Side.entries) {
            val onSide = callouts
                .filter { ButtonMap.SPOTS[it.button]?.side == side }
                .sortedBy { ButtonMap.SPOTS[it.button]?.y ?: 0f }
                .toMutableList()
            if (onSide.isEmpty()) continue
            val anchorX = if (side == ButtonMap.Side.LEFT) pad + boxWidth else width - pad - boxWidth
            uncross(onSide, d, anchorX, boxWidth, side, pad)
            val boxes = stack(onSide, d, boxWidth, side, pad)
            onSide.forEachIndexed { index, callout ->
                val box = boxes[index]
                val target = target(callout, d) ?: return@forEachIndexed
                drawBox(canvas, box, callout)
                canvas.drawLine(anchorX, anchorY(box, target.y), target.x, target.y, leader)
                canvas.drawCircle(target.x, target.y, 0.026f * d.width(), marker)
            }
        }
    }

    /** Where one button is, in the view's own pixels. */
    private fun target(callout: ButtonMap.Callout, d: RectF): PointF? =
        ButtonMap.SPOTS[callout.button]?.let { PointF(d.left + it.x * d.width(), d.top + it.y * d.height()) }

    /** A leader leaves its box level with its own button, where the box allows. */
    private fun anchorY(box: RectF, targetY: Float): Float =
        targetY.coerceIn(box.top + 10f * density, box.bottom - 10f * density)

    /** The boxes of one side, centred as a group down the picture. */
    private fun stack(
        onSide: List<ButtonMap.Callout>,
        d: RectF,
        boxWidth: Float,
        side: ButtonMap.Side,
        pad: Float,
    ): List<RectF> {
        val gap = 6f * density
        val heights = onSide.map { boxHeight(it) }
        val total = heights.sum() + gap * (onSide.size - 1)
        var top = d.top + ((d.height() - total) / 2f).coerceAtLeast(0f)
        return onSide.indices.map { index ->
            val box = if (side == ButtonMap.Side.LEFT) {
                RectF(pad, top, pad + boxWidth, top + heights[index])
            } else {
                RectF(width - pad - boxWidth, top, width - pad, top + heights[index])
            }
            top += heights[index] + gap
            box
        }
    }

    /** Swaps neighbours while it shortens the leaders, which is what uncrosses them. */
    private fun uncross(
        onSide: MutableList<ButtonMap.Callout>,
        d: RectF,
        anchorX: Float,
        boxWidth: Float,
        side: ButtonMap.Side,
        pad: Float,
    ) {
        if (onSide.size < 2) return
        repeat(onSide.size) {
            var swapped = false
            val boxes = stack(onSide, d, boxWidth, side, pad)
            for (i in 0 until onSide.size - 1) {
                val first = target(onSide[i], d) ?: continue
                val second = target(onSide[i + 1], d) ?: continue
                val now = leaderLength(anchorX, boxes[i], first) + leaderLength(anchorX, boxes[i + 1], second)
                val other = leaderLength(anchorX, boxes[i], second) + leaderLength(anchorX, boxes[i + 1], first)
                if (other < now - 0.5f) {
                    val held = onSide[i]
                    onSide[i] = onSide[i + 1]
                    onSide[i + 1] = held
                    swapped = true
                }
            }
            if (!swapped) return
        }
    }

    private fun leaderLength(anchorX: Float, box: RectF, target: PointF): Float =
        hypot(target.x - anchorX, target.y - anchorY(box, target.y))

    private fun boxHeight(callout: ButtonMap.Callout): Float =
        10f * density + nameText.textSize + callout.lines.size * (lineText.textSize + 5f * density) + 8f * density

    private fun drawBox(canvas: Canvas, box: RectF, callout: ButtonMap.Callout) {
        val radius = 10f * density
        canvas.drawRoundRect(box, radius, radius, boxFill)
        canvas.drawRoundRect(box, radius, radius, leader)
        val textLeft = box.left + 10f * density
        var baseline = box.top + 10f * density + nameText.textSize
        canvas.drawText(callout.title, textLeft, baseline, nameText)
        for (line in callout.lines) {
            baseline += lineText.textSize + 5f * density
            canvas.drawText(
                ellipsise(line, box.width() - 20f * density),
                textLeft,
                baseline,
                lineText,
            )
        }
    }

    /** Boxes are narrow, so a long line is cut rather than allowed to spill. */
    private fun ellipsise(line: String, maxWidth: Float): String {
        if (lineText.measureText(line) <= maxWidth) return line
        var cut = line.length
        while (cut > 1 && lineText.measureText(line.take(cut) + "…") > maxWidth) cut--
        return line.take(cut) + "…"
    }

    private companion object {
        /** The open Thor is a little taller than it is wide. */
        const val DEVICE_ASPECT = 0.894f
    }
}
