package com.thorpathfinder.app

import android.view.KeyEvent

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
    val label: String,
    val kind: ButtonKind,
    val keyCode: Int,
    val scanCode: Int? = null,
    /** The kernel input device a "Normal" press is replayed on, when no global action matches it. */
    val device: String? = null,
) {
    BACK("Back", ButtonKind.SYSTEM, KeyEvent.KEYCODE_BACK, scanCode = 158, device = CONTROLLER),
    HOME("Home", ButtonKind.SYSTEM, KeyEvent.KEYCODE_HOME, scanCode = 102, device = CONTROLLER),
    AYN("AYN button", ButtonKind.SYSTEM, KeyEvent.KEYCODE_HOME, scanCode = 194, device = "gpio-keys"),
    SELECT("Select", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_SELECT),
    START("Start", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_START),
    L3("L3 (click left stick)", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_THUMBL),
    R3("R3 (click right stick)", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_THUMBR);

    /** Android's own stand-in for the button, when the real key can't be replayed; null when there is none. */
    val normalAction: ButtonAction?
        get() = when (this) {
            BACK -> ButtonAction.BACK
            HOME -> ButtonAction.HOME
            else -> null
        }

    /** What a "Normal" press does, in words. */
    val normalName: String
        get() = when (this) {
            AYN -> "AYN menu"
            else -> normalAction?.label ?: label
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

enum class Gesture(val label: String) {
    PRESS("Press"),
    DOUBLE("Double-press"),
    HOLD("Hold"),
}

/** The screen an "Open an app" shortcut opens its app on. */
enum class LaunchScreen(val label: String, val short: String) {
    TOP("Top screen", "top"),
    BOTTOM("Bottom screen", "bottom"),
    /** Put the question each time the shortcut runs. */
    ASK("Ask", "ask"),
}

/** The screens a "Home" shortcut sends home. */
enum class HomeTarget(val label: String, val short: String) {
    TOP("Top screen only", "top"),
    BOTTOM("Bottom screen only", "bottom"),
    BOTH("Both screens", "both"),
}

/** The kernel input device of the Thor's Back and Home buttons. */
private const val CONTROLLER = "Odin Controller"

enum class ButtonAction(val label: String, val needsShizuku: Boolean = false) {
    /** The button's own behaviour (system) or nothing on top of the game (gamepad). */
    NORMAL("Normal"),
    NOTHING("Do nothing"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recent apps"),
    CLOSE_ALL("Close all apps", needsShizuku = true),
    SWAP_SCREENS("Swap screens", needsShizuku = true),
    MOUSE_MODE("Mouse mode on/off", needsShizuku = true),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"),
    SCREENSHOT("Screenshot"),
    SCREEN_RECORD("Screen record (Testing)", needsShizuku = true),
    POWER_MENU("Power menu"),
    LOCK_SCREEN("Lock screen"),
    LAUNCH_APP("Open an app");

    companion object {
        /** The choices offered for a button; "Do nothing" only matters where Pathfinder can block. */
        fun choicesFor(button: PhysicalButton): List<ButtonAction> =
            if (button.kind == ButtonKind.SYSTEM) entries else entries - NOTHING
    }
}

/** What a gesture left on NORMAL means, for display. */
fun normalLabel(button: PhysicalButton, gesture: Gesture): String = when {
    button.kind == ButtonKind.GAMEPAD -> "Nothing extra"
    gesture == Gesture.PRESS -> "Normal (${button.normalName})"
    gesture == Gesture.DOUBLE -> "Normal (two presses)"
    button.normalAction == null -> "Normal (long press)"
    else -> "Normal (a press)"
}
