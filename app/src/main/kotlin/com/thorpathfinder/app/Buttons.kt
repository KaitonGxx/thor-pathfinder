package com.thorpathfinder.app

import android.view.KeyEvent
import androidx.annotation.StringRes

/**
 * How Pathfinder may treat a button.
 *
 * SYSTEM buttons (Back, Home, the AYN button) are taken over once any of
 * their gestures is changed: Pathfinder swallows the press and does the
 * button's usual job itself whenever a gesture is left on "Normal".
 *
 * GAMEPAD buttons always reach the game untouched. Pathfinder only watches
 * them, so a plain press cannot be changed; double-press and hold can carry a
 * shortcut on top of what the game does.
 */
enum class ButtonKind { SYSTEM, GAMEPAD }

/**
 * The Thor buttons that can carry shortcuts, as Android reports them.
 *
 * System buttons are matched on their Linux scan code as well. Home and the
 * AYN button share KEYCODE_HOME (the AYN button is KEY_F24 on the gpio-keys
 * device, mapped to HOME), and key events Android injects itself, such as the
 * Back of an on-screen gesture or a global action, carry scan code 0 and must
 * pass through untouched.
 *
 * A "Normal" press is the real key, replayed on [device] (see [KeyReplay]).
 * The AYN button needs it: AYN's key handler consumes it and opens AYN's own
 * menu on a press and another panel on a long press, and no global action
 * does either. Back and Home have global actions, but those arrive with no
 * input device and no scan code, and apps that bind to the button itself
 * (RetroArch, for one) don't accept them. So with Shizuku they are replayed
 * too, and fall back to [normalAction] without it.
 */
enum class PhysicalButton(
    @StringRes val text: Int,
    val kind: ButtonKind,
    val keyCode: Int,
    val scanCode: Int? = null,
    /** The kernel input device a "Normal" press is replayed on, when no global action matches it. */
    val device: String? = null,
) {
    BACK(R.string.button_back, ButtonKind.SYSTEM, KeyEvent.KEYCODE_BACK, scanCode = 158, device = CONTROLLER),
    HOME(R.string.button_home, ButtonKind.SYSTEM, KeyEvent.KEYCODE_HOME, scanCode = 102, device = CONTROLLER),
    AYN(R.string.button_ayn, ButtonKind.SYSTEM, KeyEvent.KEYCODE_HOME, scanCode = 194, device = "gpio-keys"),
    SELECT(R.string.button_select, ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_SELECT),
    START(R.string.button_start, ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_START),
    L3(R.string.button_l3, ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_THUMBL),
    R3(R.string.button_r3, ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_THUMBR);

    /** Android's own stand-in for the button, when the real key can't be replayed; null when there is none. */
    val normalAction: ButtonAction?
        get() = when (this) {
            BACK -> ButtonAction.BACK
            HOME -> ButtonAction.HOME
            else -> null
        }

    /** What a "Normal" press does, in words. */
    fun normalName(words: Words): String = when (this) {
        AYN -> words.text(R.string.normal_ayn_menu)
        else -> words.text(normalAction?.text ?: text)
    }

    /** The gestures that can be changed on this button. */
    val gestures: List<Gesture>
        get() = if (kind == ButtonKind.SYSTEM) Gesture.entries else listOf(Gesture.DOUBLE, Gesture.HOLD)

    companion object {
        fun of(keyCode: Int, scanCode: Int): PhysicalButton? = entries.firstOrNull {
            it.keyCode == keyCode && (it.scanCode == null || it.scanCode == scanCode)
        }
    }
}

enum class Gesture(@StringRes val text: Int) {
    PRESS(R.string.gesture_press),
    DOUBLE(R.string.gesture_double),
    HOLD(R.string.gesture_hold),
}

/** The screen an "Open an app" shortcut opens its app on. */
enum class LaunchScreen(@StringRes val text: Int, @StringRes val short: Int) {
    TOP(R.string.launch_top, R.string.launch_top_short),
    BOTTOM(R.string.launch_bottom, R.string.launch_bottom_short),
    /** Put the question each time the shortcut runs. */
    ASK(R.string.launch_ask, R.string.launch_ask_short),
}

/** What a "Close app(s)" shortcut closes. */
enum class CloseTarget(@StringRes val text: Int) {
    ALL(R.string.close_all),
    BACKGROUND(R.string.close_background),
    FOCUSED(R.string.close_focused),
    TOP(R.string.close_top),
    BOTTOM(R.string.close_bottom),
    SPECIFIC(R.string.close_specific),
}

/** What a "Profile switcher" shortcut does when pressed. */
enum class ProfileSwitch(@StringRes val text: Int) {
    CYCLE(R.string.profile_switch_cycle),
    /** Turn one named profile on, or go back to the main one when it already is. */
    ENABLE(R.string.profile_switch_enable),
    ASK(R.string.profile_switch_ask),
}

/** The screens a "Home" shortcut sends home. */
enum class HomeTarget(@StringRes val text: Int, @StringRes val short: Int) {
    TOP(R.string.home_top, R.string.home_top_short),
    BOTTOM(R.string.home_bottom, R.string.home_bottom_short),
    BOTH(R.string.home_both, R.string.home_both_short),
}

/** What a "Focus Mode" shortcut does when pressed. */
enum class FocusSwitch(@StringRes val text: Int) {
    TOP(R.string.focus_top),
    BOTTOM(R.string.focus_bottom),
    CYCLE(R.string.focus_cycle),
    /** Between the two screens only; from Auto-lock it goes to the top first. */
    SWAP(R.string.focus_swap),
}

/** The kernel input device of the Thor's Back and Home buttons. */
private const val CONTROLLER = "Odin Controller"

enum class ButtonAction(@StringRes val text: Int, val needsShizuku: Boolean = false) {
    /** The button's own behaviour (system) or nothing on top of the game (gamepad). */
    NORMAL(R.string.action_normal),
    NOTHING(R.string.action_nothing),
    BACK(R.string.action_back),
    HOME(R.string.action_home),
    RECENTS(R.string.action_recents),
    // Named CLOSE_ALL for the mappings already stored; which apps it closes is a CloseTarget.
    CLOSE_ALL(R.string.action_close_all, needsShizuku = true),
    SWAP_SCREENS(R.string.action_swap_screens, needsShizuku = true),
    MOUSE_MODE(R.string.action_mouse_mode, needsShizuku = true),
    // Which way it moves Focus Mode is a FocusSwitch.
    FOCUS_MODE(R.string.action_focus_mode, needsShizuku = true),
    NOTIFICATIONS(R.string.action_notifications),
    QUICK_SETTINGS(R.string.action_quick_settings),
    SCREENSHOT(R.string.action_screenshot),
    SCREEN_RECORD(R.string.action_screen_record, needsShizuku = true),
    POWER_MENU(R.string.action_power_menu),
    LOCK_SCREEN(R.string.action_lock_screen),
    /** Every other shortcut in one list, run once on the spot: see [menuChoices]. */
    SHORTCUT_MENU(R.string.action_shortcut_menu),
    LAUNCH_APP(R.string.action_launch_app),
    PROFILE(R.string.action_profile);

    companion object {
        /** The choices offered for a button; "Do nothing" only matters where Pathfinder can block. */
        fun choicesFor(button: PhysicalButton): List<ButtonAction> =
            if (button.kind == ButtonKind.SYSTEM) entries else entries - NOTHING

        /**
         * What the Shortcut menu offers: every action that does something
         * without a button behind it. Normal is a button's own job and Do
         * nothing does nothing, and the menu doesn't list itself.
         */
        val menuChoices: List<ButtonAction> = entries - listOf(NORMAL, NOTHING, SHORTCUT_MENU)

        /** What a combo can do: anything but Normal and Do nothing, which a combo has no use for. */
        val comboChoices: List<ButtonAction> = entries - listOf(NORMAL, NOTHING)
    }
}

/** What a gesture left on NORMAL means, for display. */
fun normalLabel(words: Words, button: PhysicalButton, gesture: Gesture): String = when {
    button.kind == ButtonKind.GAMEPAD -> words.text(R.string.normal_nothing_extra)
    gesture == Gesture.PRESS -> words.text(R.string.normal_is, button.normalName(words))
    gesture == Gesture.DOUBLE -> words.text(R.string.normal_two_presses)
    button.normalAction == null -> words.text(R.string.normal_long_press)
    else -> words.text(R.string.normal_a_press)
}
