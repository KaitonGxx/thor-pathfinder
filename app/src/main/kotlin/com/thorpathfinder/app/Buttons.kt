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
 */
enum class PhysicalButton(
    val label: String,
    val kind: ButtonKind,
    val keyCode: Int,
    val scanCode: Int? = null,
) {
    BACK("Back", ButtonKind.SYSTEM, KeyEvent.KEYCODE_BACK, scanCode = 158),
    HOME("Home", ButtonKind.SYSTEM, KeyEvent.KEYCODE_HOME, scanCode = 102),
    AYN("AYN button", ButtonKind.SYSTEM, KeyEvent.KEYCODE_HOME, scanCode = 194),
    SELECT("Select", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_SELECT),
    START("Start", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_START),
    L3("L3 (click left stick)", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_THUMBL),
    R3("R3 (click right stick)", ButtonKind.GAMEPAD, KeyEvent.KEYCODE_BUTTON_THUMBR);

    /** What "Normal" does for a system button's press. */
    val normalAction: ButtonAction
        get() = if (this == BACK) ButtonAction.BACK else ButtonAction.HOME

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

enum class ButtonAction(val label: String, val needsShizuku: Boolean = false) {
    /** The button's own behaviour (system) or nothing on top of the game (gamepad). */
    NORMAL("Normal"),
    NOTHING("Do nothing"),
    BACK("Back"),
    HOME("Home"),
    RECENTS("Recent apps"),
    SWAP_SCREENS("Swap screens", needsShizuku = true),
    MOUSE_MODE("Mouse mode on/off", needsShizuku = true),
    NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"),
    SCREENSHOT("Screenshot"),
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
    gesture == Gesture.PRESS -> "Normal (${button.normalAction.label})"
    gesture == Gesture.DOUBLE -> "Normal (two presses)"
    else -> "Normal (a press)"
}
