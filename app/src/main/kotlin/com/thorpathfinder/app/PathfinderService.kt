package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.thorpathfinder.app.ui.ProfileChoiceActivity
import com.thorpathfinder.app.ui.ScreenChoiceActivity
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
    private var watcher: ContentObserver? = null
    private lateinit var shortcuts: Shortcuts
    private lateinit var engine: GestureEngine
    private lateinit var worker: ExecutorService
    private lateinit var overlay: Overlay

    override fun onServiceConnected() {
        watcher = ServiceLog.watch(this, handler)
        // Off a Thor, or on firmware older than the one verified, stay out of the way.
        if (!Device.current.ok) {
            ServiceLog.add(this, "service started but this device isn't supported, so it does nothing")
            return
        }
        ServiceLog.noteStart(this)
        shortcuts = Shortcuts(this)
        worker = Executors.newSingleThreadExecutor()
        overlay = Overlay(this)
        // If the last stop went unexplained, the log still holds what happened.
        worker.execute { ServiceLog.captureIfPending(this) }
        engine = GestureEngine(
            config = shortcuts,
            scheduler = { delay, task ->
                val runnable = Runnable { task() }
                handler.postDelayed(runnable, delay)
                Cancellable { handler.removeCallbacks(runnable) }
            },
            // Back and Home have Android's own action to fall back on; the AYN
            // button has only its real key, which takes Shizuku to press.
            canRestore = { it.normalAction != null || Shell.ready },
            fire = ::perform,
        )
        running = true
        current = this
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::engine.isInitialized) return false
        // The map waits for a dismissal, and a press is one. The key still
        // does its own job, so nothing is swallowed to close a window.
        overlay.dismissMapOnKey()
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
            // The real key where there is one (see PhysicalButton): the AYN button always,
            // Back and Home whenever Shizuku can press them.
            button.device != null && (button.normalAction == null || Shell.ready) ->
                return replay(button, long = gesture == Gesture.HOLD)
            else -> button.normalAction!!
        }
        // Buzz for shortcuts only, not for a long press left on Normal.
        val shortcut = action != ButtonAction.NORMAL && resolved != ButtonAction.NOTHING
        if (gesture != Gesture.PRESS && shortcut && shortcuts.vibrate) buzz()
        when (resolved) {
            ButtonAction.NORMAL, ButtonAction.NOTHING -> Unit
            ButtonAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ButtonAction.HOME -> goHome(shortcuts.home(button, gesture))
            ButtonAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            ButtonAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            ButtonAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            ButtonAction.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            ButtonAction.POWER_MENU -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            ButtonAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            ButtonAction.SWAP_SCREENS -> worker.execute { swapOutcomeMessage(ScreenSwap.swap(this))?.let(::toast) }
            ButtonAction.CLOSE_ALL -> worker.execute {
                // Every way of closing shares the keep-running list: closed, never force-stopped.
                val keep = shortcuts.keepRunning
                toast(
                    when (shortcuts.close(button, gesture)) {
                        CloseTarget.ALL -> closeAllOutcomeMessage(RecentTasks.closeAll(this, keep))
                        CloseTarget.BACKGROUND ->
                            closeBackgroundOutcomeMessage(RecentTasks.closeBackground(this, keep))
                        CloseTarget.FOCUSED -> closeAppsOutcomeMessage(RecentTasks.closeFocused(this, keep))
                        CloseTarget.TOP -> closeAppsOutcomeMessage(
                            RecentTasks.closeOnScreen(this, Display.DEFAULT_DISPLAY, "the top screen", keep)
                        )
                        CloseTarget.BOTTOM -> closeAppsOutcomeMessage(
                            RecentTasks.closeOnScreen(this, ScreenSwap.otherDisplay(this)?.displayId, "the bottom screen", keep)
                        )
                        CloseTarget.SPECIFIC -> closeAppsOutcomeMessage(
                            RecentTasks.closeApps(this, shortcuts.closeApps(button, gesture), keep)
                        )
                    }
                )
            }
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
            ButtonAction.LAUNCH_APP -> openApps(button, gesture)
            ButtonAction.PROFILE -> switchProfile(button, gesture)
        }
    }

    /**
     * Presses [button]'s real key again, through its input device, so apps and
     * the system see exactly what the button itself sends. The engine lets that
     * one press through untouched. Back and Home fall back to Android's own
     * global action whenever the key can't be pressed.
     */
    private fun replay(button: PhysicalButton, long: Boolean) {
        val device = button.device ?: return
        val scanCode = button.scanCode ?: return
        val fallback = button.normalAction
        if (!Shell.ready) {
            if (fallback != null) global(fallback) else toast("The ${button.label}'s own menu needs Shizuku")
            return
        }
        engine.letThrough(button, SystemClock.uptimeMillis() + REPLAY_WINDOW_MS)
        worker.execute {
            if (!KeyReplay.press(device, scanCode, if (long) LONG_PRESS_MS else SHORT_PRESS_MS)) {
                handler.post {
                    engine.letThrough(button, Long.MIN_VALUE)
                    if (fallback != null) global(fallback) else toast("Couldn't press the ${button.label}")
                }
            }
        }
    }

    /** Android's own version of Back or Home. */
    private fun global(action: ButtonAction) {
        when (action) {
            ButtonAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ButtonAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            else -> Unit
        }
    }

    /** A "Home" shortcut: the screens it names, or Android's own Home if it names none. */
    private fun goHome(target: HomeTarget?) {
        if (target == null) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            worker.execute { Launcher.goHome(this, target)?.let(::toast) }
        }
    }

    /** "Open an app": one app on its screen, one on each screen, or ask which. */
    private fun openApps(button: PhysicalButton, gesture: Gesture) {
        val app = shortcuts.app(button, gesture) ?: run {
            toast("That app isn't installed")
            return
        }
        val second = shortcuts.second(button, gesture)
        val screen = shortcuts.screen(button, gesture)
        when {
            second != null -> worker.execute { Launcher.openPair(this, app, second)?.let(::toast) }
            screen == LaunchScreen.ASK -> ask(app)
            else -> worker.execute { Launcher.open(this, app, screen)?.let(::toast) }
        }
    }

    /**
     * A "Profile switcher" shortcut: the next profile, one named profile (which
     * goes back to the main one when it is already on), or ask. A named profile
     * that has since been deleted falls back to cycling.
     */
    private fun switchProfile(button: PhysicalButton, gesture: Gesture) {
        val id = shortcuts.profileId(button, gesture)
        val mode = shortcuts.profile(button, gesture)
        when {
            mode == ProfileSwitch.ASK -> ask(Intent(this, ProfileChoiceActivity::class.java), "Couldn't ask which profile")
            mode == ProfileSwitch.ENABLE && Profiles.has(this, id) -> announce(Profiles.enable(this, id))
            else -> announce(Profiles.cycle(this))
        }
    }

    /** What a switch looks like: the profile's button map, or just its name. */
    private fun announce(profile: Profiles.Profile) {
        if (Profiles.showMap(this)) {
            overlay.showMap(profile.name, ButtonMap.callouts(this, shortcuts))
        } else {
            toast(profile.name)
        }
    }

    /** Puts "top or bottom?" on the top screen, for a shortcut set to ask. */
    private fun ask(pkg: String) = ask(
        Intent(this, ScreenChoiceActivity::class.java).putExtra(ScreenChoiceActivity.EXTRA_PACKAGE, pkg),
        "Couldn't ask which screen",
    )

    /** Puts one of Pathfinder's questions on the top screen. */
    private fun ask(question: Intent, problem: String) {
        question.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        if (runCatching { startActivity(question, options) }.isFailure) toast(problem)
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
        ServiceLog.add(this, "service stopped")
        ServiceLog.stop(this, watcher)
        watcher = null
        running = false
        current = null
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

        /** The connected service, so a switch made in the app can be shown too. */
        @Volatile
        private var current: PathfinderService? = null

        /**
         * Says a profile was switched to somewhere other than a shortcut: the
         * settings screen, or the question an "Ask" shortcut puts up.
         */
        fun profileSwitched(context: Context) {
            val service = current ?: return
            service.handler.post { service.announce(Profiles.active(context)) }
        }

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
