package com.thorpathfinder.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.PathParser
import kotlin.math.roundToInt

/**
 * The Brightness or Volume slider, drawn by hand like [ThorMapView]: an icon
 * (a sun, or a speaker) with a triangle above it for the top screen and one
 * below for the bottom one, lit for the screens being changed (both lit for
 * both), then a caption, the level, and the slider. With both screens at
 * different levels, a hollow second thumb marks the bottom one's.
 *
 * Touch works too: drag along the slider, or tap the triangles' column (its
 * top half moves up, its bottom half down).
 */
@SuppressLint("ViewConstructor")
class LevelPanelView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        /** The slider was dragged to [value], on the slider's own scale. */
        fun onDrag(value: Int)

        /** The triangles' column was tapped: towards the top screen, or the bottom one. */
        fun onScreens(up: Boolean)

        /** The D-pad, which the Thor reports as a hat: each axis -1, 0 or 1. */
        fun onHat(x: Int, y: Int)

        /** A key reached the slider's window; true when it was used. */
        fun onKey(event: KeyEvent): Boolean
    }

    init {
        // The window takes focus while it is up, so the controller comes here (see LevelControl).
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = listener.onKey(event) || super.dispatchKeyEvent(event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.actionMasked != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event)
        }
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        listener.onHat(hat(x), hat(y))
        // Used, so Android doesn't also turn it into arrow keys; the sticks are let be.
        return true
    }

    private fun hat(value: Float) = when {
        value < -0.5f -> -1
        value > 0.5f -> 1
        else -> 0
    }

    private var kind = LevelKind.VOLUME
    private var screens = LevelScreens.TOP
    private var levels = Levels.Pair(0, 0)
    private var caption = ""
    private var value = ""

    /** Shows the slider's state; cheap to call on every change. */
    fun show(kind: LevelKind, screens: LevelScreens, levels: Levels.Pair, caption: String, value: String) {
        this.kind = kind
        this.screens = screens
        this.levels = levels
        this.caption = caption
        this.value = value
        invalidate()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val panelWidth = dp(400f)
    private val panelHeight = dp(104f)
    private val column = dp(64f)
    private val pad = dp(18f)

    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0262820.toInt() }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3DFFFFFF }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hollow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = ACCENT
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt()
        textSize = dp(15f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(17f)
        isFakeBoldText = true
        textAlign = Paint.Align.RIGHT
    }

    private val sun = PathParser.createPathFromPathData(SUN)
    private val speaker = PathParser.createPathFromPathData(SPEAKER)
    private val muted = PathParser.createPathFromPathData(MUTED)
    private val iconPath = Path()
    private val triangle = Path()
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(panelWidth.toInt(), panelHeight.toInt())
    }

    private val trackLeft get() = column + dp(6f)
    private val trackRight get() = width - pad
    private val trackY get() = height - dp(32f)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, dp(28f), dp(28f), background)

        // The column: ▲ (top screen), the icon, ▼ (bottom screen).
        val cx = column / 2 + dp(6f)
        val shown = if (screens == LevelScreens.BOTTOM) levels.bottom else levels.top
        drawTriangle(canvas, cx, dp(14f), up = true, lit = screens.top)
        drawTriangle(canvas, cx, h - dp(14f), up = false, lit = screens.bottom)
        val icon = when {
            kind == LevelKind.BRIGHTNESS -> sun
            shown == 0 -> muted
            else -> speaker
        }
        val size = dp(32f)
        iconPath.set(icon)
        iconPath.transform(Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(cx - size / 2, h / 2 - size / 2)
        })
        canvas.drawPath(iconPath, iconPaint)

        // Caption and level.
        val textY = dp(38f)
        canvas.drawText(caption, trackLeft, textY, captionPaint)
        canvas.drawText(value, trackRight, textY, valuePaint)

        // The slider.
        val y = trackY
        val radius = dp(4f)
        rect.set(trackLeft, y - radius, trackRight, y + radius)
        canvas.drawRoundRect(rect, radius, radius, track)
        val max = Levels.max(kind).toFloat()
        val x = trackLeft + (trackRight - trackLeft) * (shown / max)
        rect.set(trackLeft, y - radius, x, y + radius)
        canvas.drawRoundRect(rect, radius, radius, accent)
        if (screens == LevelScreens.BOTH && levels.bottom != levels.top) {
            val bx = trackLeft + (trackRight - trackLeft) * (levels.bottom / max)
            canvas.drawCircle(bx, y, dp(8f), background)
            canvas.drawCircle(bx, y, dp(7f), hollow)
        }
        canvas.drawCircle(x, y, dp(9f), accent)
    }

    private fun drawTriangle(canvas: Canvas, cx: Float, cy: Float, up: Boolean, lit: Boolean) {
        val half = dp(8f)
        val tall = dp(7f)
        triangle.reset()
        if (up) {
            triangle.moveTo(cx - half, cy + tall / 2)
            triangle.lineTo(cx + half, cy + tall / 2)
            triangle.lineTo(cx, cy - tall / 2)
        } else {
            triangle.moveTo(cx - half, cy - tall / 2)
            triangle.lineTo(cx + half, cy - tall / 2)
            triangle.lineTo(cx, cy + tall / 2)
        }
        triangle.close()
        canvas.drawPath(triangle, if (lit) accent else dim)
    }

    /** Whether the touch going on began on the slider (rather than on the triangles). */
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = event.x >= column
                if (dragging) drag(event.x) else listener.onScreens(up = event.y < height / 2f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) drag(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun drag(x: Float) {
        val fraction = ((x - trackLeft) / (trackRight - trackLeft)).coerceIn(0f, 1f)
        listener.onDrag((fraction * Levels.max(kind)).roundToInt())
    }

    private companion object {
        /** Pathfinder's green, as in the app. */
        const val ACCENT = 0xFFBCD08F.toInt()

        // Material icons (Apache 2.0): light_mode, volume_up, volume_off.
        const val SUN = "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5S14.76,7 12,7L12,7zM2,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1S1.45,13 2,13zM20,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1S19.45,13 20,13zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1S11,1.45 11,2zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1C11.45,19 11,19.45 11,20zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0c0.39,-0.39 0.39,-1.03 0,-1.41L18.36,16.95zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L19.42,5.99zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L7.05,18.36z"
        const val SPEAKER = "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"
        const val MUTED = "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z"
    }
}
