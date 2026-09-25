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
import androidx.annotation.StringRes
import com.thorpathfinder.app.ui.ProfileChoiceActivity
import com.thorpathfinder.app.ui.ScreenChoiceActivity
import com.thorpathfinder.app.ui.ShortcutMenuActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

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
    private lateinit var apps: AppWatcher

    /** Keys whose press closed the map, so that their release is kept from the app as well. */
    private val closedMap = mutableSetOf<Int>()

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
            fireCombo = ::performCombo,
            fire = ::perform,
        )
        apps = AppWatcher(this, ::announceApp)
        apps.start()
        running = true
        current = this
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!::engine.isInitialized) return false
        if (keptForMap(event)) return true
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val key = ComboKey.of(event.keyCode, event.scanCode, xboxStyle(event))
        val button = PhysicalButton.of(event.keyCode, event.scanCode)
        if (key == null && button == null) return false
        return engine.onKey(
            key = key,
            button = button,
            down = event.action == KeyEvent.ACTION_DOWN,
            repeat = event.repeatCount,
            time = event.eventTime,
            canceled = event.isCanceled,
        )
    }

    /** Whether [event] came from the Thor's controller in AYN's Xbox style (see [ComboKey.XBOX_STYLE_PRODUCT]). */
    private fun xboxStyle(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        return device.vendorId == ComboKey.AYN_VENDOR && device.productId == ComboKey.XBOX_STYLE_PRODUCT
    }

    /**
     * While the map is up, the buttons belong to it: a fresh press closes it
     * and goes no further, not to a shortcut and not to the app underneath,
     * and neither do that button's repeats and release. A button already down
     * when the map appeared, such as the hold that switched profiles, carries
     * on as usual, since its press arrived first. Volume is left alone. The
     * sticks can't be held back: they are motion, which Android 13 gives an
     * accessibility service no way to filter.
     */
    private fun keptForMap(event: KeyEvent): Boolean {
        val code = event.keyCode
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> when {
                code in closedMap -> true
                !overlay.mapShowing || event.repeatCount > 0 || code in PASSED_WITH_MAP -> false
                // Injected keys, Pathfinder's own replays among them, carry no
                // scan code; only a real press closes the map.
                event.scanCode == 0 -> false
                else -> {
                    overlay.dismissMap()
                    closedMap += code
                    true
                }
            }
            KeyEvent.ACTION_UP -> closedMap.remove(code)
            else -> false
        }
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
        val isShortcut = action != ButtonAction.NORMAL && resolved != ButtonAction.NOTHING
        if (gesture != Gesture.PRESS && isShortcut && shortcuts.vibrate) buzz()
        run(shortcuts.shortcut(button, gesture).copy(action = resolved))
    }

    /** A combo was pressed: only combos that do something are ever looked for. */
    private fun performCombo(keys: Set<ComboKey>) {
        val shortcut = shortcuts.combo(keys) ?: return
        if (shortcuts.vibrate) buzz()
        run(shortcut)
    }

    /** Carries out one shortcut, whether a button's, a combo's or one picked from the Shortcut menu. */
    private fun run(shortcut: Shortcut) {
        when (shortcut.action) {
            ButtonAction.NORMAL, ButtonAction.NOTHING -> Unit
            ButtonAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ButtonAction.HOME -> goHome(shortcut.home)
            ButtonAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            ButtonAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            ButtonAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            ButtonAction.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            ButtonAction.POWER_MENU -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            ButtonAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            ButtonAction.SWAP_SCREENS -> worker.execute { swapOutcomeMessage(words(), ScreenSwap.swap(this))?.let(::toast) }
            ButtonAction.CLOSE_ALL -> worker.execute {
                // Every way of closing shares the keep-running list: closed, never force-stopped.
                val keep = shortcuts.keepRunning
                val words = words()
                toast(
                    when (shortcut.close) {
                        CloseTarget.ALL -> closeAllOutcomeMessage(words, RecentTasks.closeAll(this, keep))
                        CloseTarget.BACKGROUND ->
                            closeBackgroundOutcomeMessage(words, RecentTasks.closeBackground(this, keep))
                        CloseTarget.FOCUSED -> closeAppsOutcomeMessage(words, RecentTasks.closeFocused(this, keep))
                        CloseTarget.TOP -> closeAppsOutcomeMessage(
                            words,
                            RecentTasks.closeOnScreen(this, Display.DEFAULT_DISPLAY, top = true, keep),
                        )
                        CloseTarget.BOTTOM -> closeAppsOutcomeMessage(
                            words,
                            RecentTasks.closeOnScreen(this, ScreenSwap.otherDisplay(this)?.displayId, top = false, keep),
                        )
                        CloseTarget.SPECIFIC -> closeAppsOutcomeMessage(
                            words,
                            RecentTasks.closeApps(this, shortcut.closeApps, keep),
                        )
                    }
                )
            }
            ButtonAction.SCREEN_RECORD -> worker.execute {
                screenRecordOutcomeMessage(words(), ScreenRecord.open(this))?.let(::toast)
            }
            ButtonAction.MOUSE_MODE -> worker.execute {
                // The Thor puts up its own message when mouse mode changes, so
                // Pathfinder only speaks when it couldn't change it.
                if (MouseMode.toggle() == null) toast(getString(R.string.msg_mouse_needs_shizuku))
            }
            // Unlike mouse mode, AYN says nothing when Focus Mode changes, so Pathfinder does.
            ButtonAction.FOCUS_MODE -> worker.execute {
                toast(focusModeMessage(words(), FocusMode.apply(this, shortcut.focus)))
            }
            ButtonAction.LAUNCH_APP -> openApps(shortcut)
            ButtonAction.PROFILE -> switchProfile(shortcut)
            ButtonAction.SHORTCUT_MENU ->
                ask(Intent(this, ShortcutMenuActivity::class.java), R.string.msg_couldnt_open_menu)
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
            if (fallback != null) {
                global(fallback)
            } else {
                toast(getString(R.string.msg_button_menu_needs_shizuku, getString(button.text)))
            }
            return
        }
        engine.letThrough(button, SystemClock.uptimeMillis() + REPLAY_WINDOW_MS)
        worker.execute {
            if (!KeyReplay.press(device, scanCode, if (long) LONG_PRESS_MS else SHORT_PRESS_MS)) {
                handler.post {
                    engine.letThrough(button, Long.MIN_VALUE)
                    if (fallback != null) global(fallback) else toast(getString(R.string.msg_couldnt_press, getString(button.text)))
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
    private fun openApps(shortcut: Shortcut) {
        val app = shortcut.app ?: run {
            toast(getString(R.string.msg_app_not_installed))
            return
        }
        val second = shortcut.second
        val screen = shortcut.screen
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
    private fun switchProfile(shortcut: Shortcut) {
        val id = shortcut.profileId
        val mode = shortcut.profile
        when {
            mode == ProfileSwitch.ASK -> ask(Intent(this, ProfileChoiceActivity::class.java), R.string.msg_couldnt_ask_profile)
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

    /**
     * A switch made for an app, or back to the chosen profile on leaving it:
     * a message, which is also the title of the Thor picture when switching
     * shows it.
     */
    private fun announceApp(profile: Profiles.Profile, app: String?) {
        val name = app?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
        }
        val message = AppProfiles.switchMessage(words(), profile.name, name)
        if (Profiles.showMap(this)) {
            overlay.showMap(message, ButtonMap.callouts(this, shortcuts))
        } else {
            overlay.show(message)
        }
    }

    /** Puts "top or bottom?" on the top screen, for a shortcut set to ask. */
    private fun ask(pkg: String) = ask(
        Intent(this, ScreenChoiceActivity::class.java).putExtra(ScreenChoiceActivity.EXTRA_PACKAGE, pkg),
        R.string.msg_couldnt_ask_screen,
    )

    /** Puts one of Pathfinder's questions on the top screen. */
    private fun ask(question: Intent, @StringRes problem: Int) {
        question.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        if (runCatching { startActivity(question, options) }.isFailure) toast(getString(problem))
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
        if (::apps.isInitialized) apps.stop()
        if (::overlay.isInitialized) overlay.dismiss()
        closedMap.clear()
        handler.removeCallbacksAndMessages(null)
        if (::worker.isInitialized) worker.shutdown()
        // Tell the watchdog now, while whatever switched the service off is
        // still in front: a switch-off in Android's settings is to be kept.
        val app = applicationContext
        thread(name = "watchdog-note") { runCatching { Watchdog.noteStopped(app) } }
        return super.onUnbind(intent)
    }

    companion object {
        /** True while Android has the service connected. */
        @Volatile
        var running = false
            private set

        /** Whether app profiles are watching which app is in front, for the diagnostics report. */
        val watchingApps: Boolean
            get() = current?.let { it::apps.isInitialized && it.apps.watching } == true

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

        /**
         * Runs a shortcut picked from the Shortcut menu, once. It waits for the
         * menu's window to finish closing, so that Back, a screenshot or a swap
         * acts on what is underneath rather than on the menu. False when the
         * service isn't running, so nothing can.
         */
        fun runFromMenu(shortcut: Shortcut): Boolean {
            val service = current ?: return false
            service.handler.postDelayed({ service.run(shortcut) }, MENU_CLOSE_MS)
            return true
        }

        /** Long enough for the Shortcut menu's closing animation to finish. */
        private const val MENU_CLOSE_MS = 400L

        /** How long a replayed press may take to arrive and still be let through. */
        private const val REPLAY_WINDOW_MS = 3000L
        private const val SHORT_PRESS_MS = 50L

        /** Past AYN's long-press point (about 400 ms, when the key starts repeating). */
        private const val LONG_PRESS_MS = 900L

        /** Keys that keep doing their own job while the map is up. */
        private val PASSED_WITH_MAP = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
        )
    }
}

/** A message for the user, or null when the swap simply worked. */
fun swapOutcomeMessage(words: Words, outcome: ScreenSwap.Outcome): String? = when (outcome) {
    ScreenSwap.Outcome.Swapped, ScreenSwap.Outcome.Moved -> null
    ScreenSwap.Outcome.NothingToMove -> words.text(R.string.msg_swap_nothing)
    ScreenSwap.Outcome.NoSecondScreen -> words.text(R.string.msg_no_second_screen)
    ScreenSwap.Outcome.SecondScreenOff -> words.text(R.string.msg_other_screen_off)
    ScreenSwap.Outcome.NeedsShizuku -> words.text(R.string.msg_swap_needs_shizuku)
    is ScreenSwap.Outcome.Failed -> words.text(R.string.msg_swap_failed, outcome.message)
}
