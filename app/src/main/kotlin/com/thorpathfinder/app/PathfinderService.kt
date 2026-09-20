package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches the Thor's buttons and runs the user's shortcuts.
 *
 * It asks for key events only: no accessibility events and no window content,
 * so it never sees what is on screen.
 */
class PathfinderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var shortcuts: Shortcuts
    private lateinit var engine: GestureEngine
    private lateinit var worker: ExecutorService
    private lateinit var overlay: Overlay

    override fun onServiceConnected() {
        // Off a Thor, or on firmware older than the one verified, stay out of the way.
        if (!Device.current.ok) return
        shortcuts = Shortcuts(this)
        worker = Executors.newSingleThreadExecutor()
        overlay = Overlay(this)
        engine = GestureEngine(
            config = shortcuts,
            scheduler = { delay, task ->
                val runnable = Runnable { task() }
                handler.postDelayed(runnable, delay)
                Cancellable { handler.removeCallbacks(runnable) }
            },
            fire = ::perform,
        )
        running = true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::engine.isInitialized) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val button = PhysicalButton.of(event.keyCode, event.scanCode) ?: return false
        return engine.onKey(
            button = button,
            down = event.action == KeyEvent.ACTION_DOWN,
            repeat = event.repeatCount,
            time = event.eventTime,
            canceled = event.isCanceled,
        )
    }

    private fun perform(button: PhysicalButton, gesture: Gesture, action: ButtonAction) {
        val resolved = when {
            action != ButtonAction.NORMAL -> action
            button.kind != ButtonKind.SYSTEM -> return
            // No global action does what this button does: press the real key.
            button.normalAction == null -> return replay(button, long = gesture == Gesture.HOLD)
            else -> button.normalAction!!
        }
        // Buzz for shortcuts only, not for a long press left on Normal.
        val shortcut = action != ButtonAction.NORMAL && resolved != ButtonAction.NOTHING
        if (gesture != Gesture.PRESS && shortcut && shortcuts.vibrate) buzz()
        when (resolved) {
            ButtonAction.NORMAL, ButtonAction.NOTHING -> Unit
            ButtonAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ButtonAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            ButtonAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            ButtonAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            ButtonAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            ButtonAction.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            ButtonAction.POWER_MENU -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            ButtonAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            ButtonAction.SWAP_SCREENS -> worker.execute { swapOutcomeMessage(ScreenSwap.swap(this))?.let(::toast) }
            ButtonAction.CLOSE_ALL -> worker.execute { toast(closeAllOutcomeMessage(RecentTasks.closeAll(this, shortcuts.keepRunning))) }
            ButtonAction.SCREEN_RECORD -> worker.execute { screenRecordOutcomeMessage(ScreenRecord.open(this))?.let(::toast) }
            ButtonAction.MOUSE_MODE -> worker.execute {
                toast(
                    when (MouseMode.toggle()) {
                        true -> "Mouse mode on"
                        false -> "Mouse mode off"
                        null -> "Mouse mode needs Shizuku"
                    }
                )
            }
            ButtonAction.LAUNCH_APP -> launch(shortcuts.app(button, gesture), shortcuts.screen(button, gesture))
        }
    }

    /**
     * Presses [button]'s real key again, through its input device, so the
     * system does what the button itself does (the AYN button's menu, or its
     * long-press panel). The engine lets that one press through untouched.
     */
    private fun replay(button: PhysicalButton, long: Boolean) {
        val device = button.device ?: return
        val scanCode = button.scanCode ?: return
        if (!Shell.ready) {
            toast("The ${button.label}'s own menu needs Shizuku")
            return
        }
        engine.letThrough(button, SystemClock.uptimeMillis() + REPLAY_WINDOW_MS)
        worker.execute {
            if (!KeyReplay.press(device, scanCode, if (long) LONG_PRESS_MS else SHORT_PRESS_MS)) {
                handler.post { engine.letThrough(button, Long.MIN_VALUE) }
                toast("Couldn't press the ${button.label}")
            }
        }
    }

    /**
     * Opens [pkg] on the [screen] the shortcut chose. Both of the Thor's
     * screens are ordinary public displays, so an app may launch on either;
     * `setLaunchDisplayId` needs no extra permission for that.
     */
    private fun launch(pkg: String?, screen: LaunchScreen) {
        val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            toast("That app isn't installed")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val display = when (screen) {
            LaunchScreen.TOP -> Display.DEFAULT_DISPLAY
            LaunchScreen.BOTTOM -> {
                val other = ScreenSwap.otherDisplay(this)
                if (other == null) {
                    toast("No second screen found")
                    return
                }
                // It still opens there; this just says why nothing shows up.
                if (other.state == Display.STATE_OFF) toast("The other screen is off")
                other.displayId
            }
        }
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()
        if (runCatching { startActivity(intent, options) }.isSuccess) return
        // Shizuku can start it as the shell instead, the way a swap moves one.
        val component = intent.component?.flattenToShortString()
        if (!Shell.ready || component == null) {
            toast("Couldn't open that app")
            return
        }
        worker.execute {
            val started = Shell.run("am", "start", "--display", display.toString(), "-n", component)
            if (!started.ok) toast("Couldn't open that app")
        }
    }

    private fun buzz() {
        val vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    // Not a Toast: Android 13 would suppress one from a background service (see Overlay).
    private fun toast(message: String) = handler.post { overlay.show(message) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        if (::engine.isInitialized) engine.reset()
        if (::overlay.isInitialized) overlay.dismiss()
        handler.removeCallbacksAndMessages(null)
        if (::worker.isInitialized) worker.shutdown()
        return super.onUnbind(intent)
    }

    companion object {
        /** True while Android has the service connected. */
        @Volatile
        var running = false
            private set

        /** How long a replayed press may take to arrive and still be let through. */
        private const val REPLAY_WINDOW_MS = 3000L
        private const val SHORT_PRESS_MS = 50L

        /** Past AYN's long-press point (about 400 ms, when the key starts repeating). */
        private const val LONG_PRESS_MS = 900L
    }
}

/** A message for the user, or null when the swap simply worked. */
fun swapOutcomeMessage(outcome: ScreenSwap.Outcome): String? = when (outcome) {
    ScreenSwap.Outcome.Swapped, ScreenSwap.Outcome.Moved -> null
    ScreenSwap.Outcome.NothingToMove -> "No app to move"
    ScreenSwap.Outcome.NoSecondScreen -> "No second screen found"
    ScreenSwap.Outcome.SecondScreenOff -> "The other screen is off"
    ScreenSwap.Outcome.NeedsShizuku -> "Swapping screens needs Shizuku"
    is ScreenSwap.Outcome.Failed -> "Couldn't swap: ${outcome.message}"
}
