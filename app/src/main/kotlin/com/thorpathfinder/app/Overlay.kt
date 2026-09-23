package com.thorpathfinder.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * A short message drawn over whatever is on the top screen, styled like a
 * toast.
 *
 * Android 13 suppresses ordinary toasts from an app in the background unless
 * it holds the notification permission ("Suppressing toast ... by user
 * request" in logcat), and Pathfinder's actions all run from its service.
 * An accessibility service may add TYPE_ACCESSIBILITY_OVERLAY windows without
 * any permission, so the message is drawn as one of those instead.
 * Main thread only.
 */
class Overlay(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private val hide = Runnable { dismiss() }
    private var map: View? = null
    private var mapShownAt = 0L

    /** Shows [message] for about two seconds; a newer message replaces it. */
    fun show(message: String) {
        val text = view ?: create()
        if (view == null) {
            if (runCatching { windowManager.addView(text, layoutParams()) }.isFailure) return
            view = text
        }
        text.text = message
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, DURATION_MS)
    }

    fun dismiss() {
        handler.removeCallbacks(hide)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        dismissMap()
    }

    /**
     * Shows the Thor with this profile's shortcuts beside it, and leaves it
     * there until it is dismissed. Unlike the message it takes touches, since
     * it has a button; it stays out of the way of keys, which still reach
     * whatever is underneath.
     */
    fun showMap(title: String, callouts: List<ButtonMap.Callout>) {
        dismissMap()
        val metrics = context.resources.displayMetrics
        val params = layoutParams().apply {
            width = (metrics.widthPixels * 0.92f).toInt()
            height = (metrics.heightPixels * 0.86f).toInt()
            gravity = Gravity.CENTER
            y = 0
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        val view = ThorMapView(context, title, callouts, onDismiss = ::dismissMap)
        if (runCatching { windowManager.addView(view, params) }.isFailure) return
        map = view
        mapShownAt = SystemClock.uptimeMillis()
    }

    /**
     * A button press puts the map away too, so a Thor whose touchscreen is
     * busy elsewhere is never stuck behind it. The press that opened it does
     * not count: a hold fires while the button is still down, and its release
     * would arrive the moment the map appeared.
     */
    fun dismissMapOnKey() {
        if (map != null && SystemClock.uptimeMillis() - mapShownAt > KEY_GRACE_MS) dismissMap()
    }

    fun dismissMap() {
        map?.let { runCatching { windowManager.removeView(it) } }
        map = null
    }

    private fun create(): TextView = TextView(context).apply {
        val density = resources.displayMetrics.density
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val h = (24 * density).toInt()
        val v = (14 * density).toInt()
        setPadding(h, v, h, v)
        background = GradientDrawable().apply {
            cornerRadius = 24 * density
            setColor(0xEE323232.toInt())
        }
        elevation = 6 * density
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = (72 * context.resources.displayMetrics.density).toInt()
        title = "Thor Pathfinder"
    }

    private companion object {
        const val DURATION_MS = 2000L

        /** Long enough for the press that opened the map to finish arriving. */
        const val KEY_GRACE_MS = 900L
    }
}
