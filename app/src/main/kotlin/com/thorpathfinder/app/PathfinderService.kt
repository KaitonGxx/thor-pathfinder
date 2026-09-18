package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
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
        val resolved = when (action) {
            ButtonAction.NORMAL -> if (button.kind == ButtonKind.SYSTEM) button.normalAction else return
            else -> action
        }
        if (gesture != Gesture.PRESS && resolved != ButtonAction.NOTHING && shortcuts.vibrate) buzz()
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
            ButtonAction.CLOSE_ALL -> worker.execute { toast(closeAllOutcomeMessage(RecentTasks.closeAll(this))) }
            ButtonAction.MOUSE_MODE -> worker.execute {
                toast(
                    when (MouseMode.toggle()) {
                        true -> "Mouse mode on"
                        false -> "Mouse mode off"
                        null -> "Mouse mode needs Shizuku"
                    }
                )
            }
            ButtonAction.LAUNCH_APP -> launch(shortcuts.app(button, gesture))
        }
    }

    private fun launch(pkg: String?) {
        val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (intent == null) {
            toast("That app isn't installed")
            return
        }
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
