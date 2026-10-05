package com.thorpathfinder.app

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import java.util.concurrent.Executors

/**
 * The Brightness and Volume sliders: what a shortcut opens, what the D-pad
 * does while one is up, and the writing of the levels.
 *
 * The slider sits on the top screen, like Pathfinder's other messages. Left
 * and Right change the level (held, they keep going), and Up and Down move
 * between the top screen, both, and the bottom one. While it is up its window
 * has focus, so the controller goes to it rather than the app: the Thor's
 * D-pad is a hat, which reaches only the focused window. Back or B close it,
 * and it goes away by itself [IDLE_MS] after the last press or touch.
 *
 * Top volume is the media volume, which Pathfinder may set itself. The rest
 * takes the shell user, through the Shizuku helper ([AppWatcher.useHelper]):
 * both screens' brightness (the display manager's per-screen setBrightness,
 * as AYN's dual-screen panel does) and the bottom screen's volume. Without
 * Shizuku only the top screen's volume can be changed.
 *
 * Main thread only, apart from the writes, which go to a thread of their own
 * and are merged so a fast hold never queues up stale levels.
 */
class LevelControl(
    private val context: Context,
    private val apps: AppWatcher,
    private val say: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val io = Executors.newSingleThreadExecutor { Thread(it, "pathfinder-levels") }

    private var view: LevelPanelView? = null
    private var kind = LevelKind.VOLUME
    private var screens = LevelScreens.TOP
    private var levels = Levels.Pair(0, 0)
    private var bottomDisplay: Int? = null

    /** Whether the user has moved the level since the slider opened, so a late read can't undo it. */
    private var moved = false

    /** A word in place of the caption for a moment, such as why the bottom screen can't be reached. */
    private var note: String? = null

    /** D-pad keys whose press went to the slider, so their repeats and release go there too. */
    private val held = mutableSetOf<Int>()

    /** The D-pad as a hat (each axis -1, 0 or 1), as last reported to the slider's window. */
    private var hatX = 0
    private var hatY = 0

    /** Which way a held Left or Right is going: -1, 1, or 0 for no hold. */
    private var repeatDirection = 0

    val showing: Boolean get() = view != null

    /** Opens the [kind] slider on the top screen, or brings it back there if it is already up. */
    fun open(kind: LevelKind) {
        if (kind == LevelKind.BRIGHTNESS && !Shell.ready) {
            say(context.getString(R.string.msg_brightness_needs_shizuku))
            return
        }
        bottomDisplay = ScreenSwap.otherDisplay(context)?.displayId
        this.kind = kind
        moved = false
        note = null
        screens = LevelScreens.TOP
        levels = read(kind)
        if (Shell.ready) {
            apps.useHelper(KEEP_HELPER_MS) { helper -> onHelper(helper) }
        }
        val panel = view ?: create() ?: return
        view = panel
        render()
        keepOpen()
    }

    /** Takes the slider down; keys already pressed still have their release taken. */
    fun close() {
        stopRepeat()
        hatX = 0
        hatY = 0
        handler.removeCallbacks(closeLater)
        handler.removeCallbacks(clearNote)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    /** For when the service stops. */
    fun shutdown() {
        close()
        held.clear()
        io.shutdown()
    }

    /** A key event; true when the slider takes it. */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code !in DPAD) return false
        when (event.action) {
            KeyEvent.ACTION_UP -> {
                if (!held.remove(code)) return false
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) stopRepeat()
                return true
            }
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return code in held
                // Injected keys carry no scan code; only a real press drives the slider.
                if (view == null || event.scanCode == 0) return false
                held += code
                dpad(code)
                return true
            }
        }
        return false
    }

    private fun canReachBottom() = Shell.ready && bottomDisplay != null

    private fun bottomProblem(): String = context.getString(
        if (bottomDisplay == null) R.string.msg_no_second_screen else R.string.needs_shizuku,
    )

    /** Both screens' levels as they are now, as far as Pathfinder can read them by itself. */
    private fun read(kind: LevelKind): Levels.Pair {
        val resolver = context.contentResolver
        return when (kind) {
            LevelKind.VOLUME -> {
                val top = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val bottom = runCatching { Settings.System.getInt(resolver, SECONDARY_VOLUME) }.getOrDefault(top)
                Levels.Pair(top, bottom.coerceIn(0, Levels.VOLUME_MAX))
            }
            LevelKind.BRIGHTNESS -> {
                val stored = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }
                    .getOrDefault(128)
                val top = Levels.brightnessToPercent(Levels.storedToBrightness(stored))
                Levels.Pair(top, Levels.lastBottomBrightness(context) ?: top)
            }
        }
    }

    /**
     * The helper is there: brightness is read again from Android itself (the
     * bottom screen's can't be read any other way), and anything waiting for
     * it is written.
     */
    private fun onHelper(helper: ITaskWatcher) {
        if (view != null && kind == LevelKind.BRIGHTNESS) {
            val display = bottomDisplay
            io.execute {
                val top = runCatching { helper.getBrightness(Display.DEFAULT_DISPLAY) }.getOrDefault(-1f)
                val bottom = display?.let { runCatching { helper.getBrightness(it) }.getOrDefault(-1f) } ?: -1f
                handler.post {
                    if (view == null || kind != LevelKind.BRIGHTNESS || moved) return@post
                    levels = Levels.Pair(
                        if (top >= 0) Levels.brightnessToPercent(top) else levels.top,
                        if (bottom >= 0) Levels.brightnessToPercent(bottom) else levels.bottom,
                    )
                    if (bottom >= 0) Levels.setLastBottomBrightness(context, levels.bottom)
                    render()
                }
            }
        }
        synchronized(lock) { parked?.let { parked = null; queue(it) } }
    }

    /** A D-pad press, however it arrived. */
    private fun dpad(code: Int) {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> press(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> press(1)
            KeyEvent.KEYCODE_DPAD_UP -> move(up = true)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(up = false)
        }
    }

    /**
     * The Thor's D-pad, which is a hat, not keys: Android turns it into arrow
     * keys only inside the window with focus, after the accessibility service
     * has had its chance, so the slider's window takes focus and reads it here.
     */
    private fun hat(x: Int, y: Int) {
        if (x != hatX) {
            stopRepeat()
            hatX = x
            if (x != 0) press(x)
        }
        if (y != hatY) {
            hatY = y
            if (y != 0) move(up = y < 0)
        }
    }

    /** A key that reached the slider's window: the D-pad as keys (if a controller sends them), or Back or B to close. */
    private fun windowKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code in DPAD) {
            when {
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> dpad(code)
                event.action == KeyEvent.ACTION_UP &&
                    (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) -> stopRepeat()
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP) close()
            return true
        }
        return false
    }

    private fun press(direction: Int) {
        nudge(direction)
        stopRepeat()
        repeatDirection = direction
        handler.postDelayed(repeat, REPEAT_DELAY_MS)
    }

    private val repeat = object : Runnable {
        override fun run() {
            if (repeatDirection == 0 || view == null) return
            nudge(repeatDirection)
            handler.postDelayed(this, Levels.holdSpeed(context).intervalMs)
        }
    }

    private fun stopRepeat() {
        handler.removeCallbacks(repeat)
        repeatDirection = 0
    }

    private fun nudge(direction: Int) {
        set(Levels.nudge(levels, screens, direction * Levels.step(context, kind), Levels.min(kind), Levels.max(kind)))
    }

    private fun set(new: Levels.Pair) {
        keepOpen()
        if (new == levels) return
        val top = new.top != levels.top
        val bottom = new.bottom != levels.bottom
        levels = new
        moved = true
        write(top, bottom)
        render()
    }

    /** Up or Down: towards the top screen or the bottom one, Both between them. */
    private fun move(up: Boolean) {
        keepOpen()
        val target = if (up) screens.up else screens.down
        if (target == screens) return
        if (target != LevelScreens.TOP && !canReachBottom()) {
            note = bottomProblem()
            handler.removeCallbacks(clearNote)
            handler.postDelayed(clearNote, NOTE_MS)
        } else {
            screens = target
        }
        render()
    }

    private val clearNote = Runnable {
        note = null
        render()
    }

    private val closeLater = Runnable { close() }

    private fun keepOpen() {
        handler.removeCallbacks(closeLater)
        handler.postDelayed(closeLater, IDLE_MS)
        // However long the slider is used, the helper stays for a while after.
        if (Shell.ready) apps.keepHelper(KEEP_HELPER_MS)
    }

    private fun render() {
        val panel = view ?: return
        val shownTop = if (screens == LevelScreens.BOTTOM) levels.bottom else levels.top
        val value = if (screens == LevelScreens.BOTH && levels.top != levels.bottom) {
            "${format(levels.top)} · ${format(levels.bottom)}"
        } else {
            format(shownTop)
        }
        panel.show(kind, screens, levels, note ?: context.getString(screens.text), value)
    }

    private fun format(level: Int): String =
        if (kind == LevelKind.BRIGHTNESS) context.getString(R.string.levels_percent, level) else level.toString()

    private fun create(): LevelPanelView? {
        val panel = LevelPanelView(context, object : LevelPanelView.Listener {
            override fun onDrag(value: Int) =
                set(Levels.drag(levels, screens, value, Levels.min(kind), Levels.max(kind)))

            override fun onScreens(up: Boolean) = move(up)

            override fun onHat(x: Int, y: Int) = hat(x, y)

            override fun onKey(event: KeyEvent): Boolean = windowKey(event)
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Focusable, unlike Pathfinder's other overlays: the D-pad only reaches the window with
            // focus. Touches outside it still go to the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            val bottom = Levels.place(context) == Levels.Place.BOTTOM
            gravity = (if (bottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.CENTER_HORIZONTAL
            y = (28 * context.resources.displayMetrics.density).toInt()
            title = "Thor Pathfinder slider"
        }
        if (runCatching { windowManager.addView(panel, params) }.isFailure) return null
        panel.requestFocus()
        return panel
    }

    /** One set of levels to write: null leaves that screen alone. */
    private data class Write(val kind: LevelKind, val top: Int?, val bottom: Int?, val bottomDisplay: Int?)

    private val lock = Any()
    private var queued: Write? = null

    /** A write that needed the helper before it was there; written once it is. */
    private var parked: Write? = null

    private fun write(top: Boolean, bottom: Boolean) {
        queue(Write(kind, levels.top.takeIf { top }, levels.bottom.takeIf { bottom }, bottomDisplay))
    }

    /** Hands [write] to the writing thread, merged with any not yet written, newest levels winning. */
    private fun queue(write: Write) {
        val start: Boolean
        synchronized(lock) {
            start = queued == null
            queued = merge(queued, write)
        }
        if (start) {
            runCatching {
                io.execute {
                    val next = synchronized(lock) { queued.also { queued = null } }
                    if (next != null) apply(next)
                }
            }
        }
    }

    private fun merge(old: Write?, new: Write): Write =
        if (old == null || old.kind != new.kind) new else new.copy(top = new.top ?: old.top, bottom = new.bottom ?: old.bottom)

    /** Writing thread: puts the levels in place, parking what needs the helper until it is there. */
    private fun apply(write: Write) {
        val helper = apps.connected
        var waiting: Write? = null
        when (write.kind) {
            LevelKind.VOLUME -> {
                write.top?.let { level ->
                    runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
                        .onFailure { Log.w(TAG, "couldn't set the media volume", it) }
                }
                write.bottom?.let { level ->
                    if (helper == null) {
                        waiting = write.copy(top = null)
                    } else {
                        runCatching { helper.setBottomVolume(level) }.onFailure { Log.w(TAG, "bottom volume", it) }
                    }
                }
            }
            LevelKind.BRIGHTNESS -> {
                if (helper == null) {
                    waiting = write
                } else {
                    write.top?.let { percent ->
                        runCatching { helper.setBrightness(Display.DEFAULT_DISPLAY, Levels.percentToBrightness(percent)) }
                            .onFailure { Log.w(TAG, "top brightness", it) }
                    }
                    val display = write.bottomDisplay
                    if (write.bottom != null && display != null) {
                        runCatching { helper.setBrightness(display, Levels.percentToBrightness(write.bottom)) }
                            .onFailure { Log.w(TAG, "bottom brightness", it) }
                        Levels.setLastBottomBrightness(context, write.bottom)
                    }
                }
            }
        }
        waiting?.let { w -> synchronized(lock) { parked = merge(parked, w) } }
    }

    private companion object {
        const val TAG = "PathfinderLevels"
        const val SECONDARY_VOLUME = "secondary_screen_volume_level"

        val DPAD = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
        )

        /** How long the slider stays up after the last press or touch. */
        const val IDLE_MS = 3000L

        /** A held Left or Right starts repeating after this, then goes at the chosen speed. */
        const val REPEAT_DELAY_MS = 350L
        const val NOTE_MS = 1500L

        /** How long the helper is kept after the slider was last opened. */
        const val KEEP_HELPER_MS = 30_000L
    }
}
