package com.thorpathfinder.app

import android.content.Context
import androidx.core.content.edit

/**
 * The user's shortcuts and timings, in SharedPreferences.
 *
 * SharedPreferences keeps its values in memory after the first read, so the
 * accessibility service can look actions up on every key event.
 */
class Shortcuts(context: Context) : GestureConfig {

    private val prefs = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    override fun action(button: PhysicalButton, gesture: Gesture): ButtonAction {
        val stored = prefs.getString(key(button, gesture), null)
        return stored?.let { name -> ButtonAction.entries.firstOrNull { it.name == name } }
            ?: DEFAULTS[button to gesture]
            ?: ButtonAction.NORMAL
    }

    /** The app a LAUNCH_APP gesture opens. */
    fun app(button: PhysicalButton, gesture: Gesture): String? =
        prefs.getString(key(button, gesture) + ".app", null)

    /** The screen a LAUNCH_APP gesture opens its app on. */
    fun screen(button: PhysicalButton, gesture: Gesture): LaunchScreen =
        prefs.getString(key(button, gesture) + ".screen", null)
            ?.let { name -> LaunchScreen.entries.firstOrNull { it.name == name } }
            ?: LaunchScreen.TOP

    fun set(
        button: PhysicalButton,
        gesture: Gesture,
        action: ButtonAction,
        app: String? = null,
        screen: LaunchScreen = LaunchScreen.TOP,
    ) {
        prefs.edit {
            putString(key(button, gesture), action.name)
            if (action == ButtonAction.LAUNCH_APP) {
                putString(key(button, gesture) + ".app", app)
                putString(key(button, gesture) + ".screen", screen.name)
            } else {
                remove(key(button, gesture) + ".app")
                remove(key(button, gesture) + ".screen")
            }
        }
    }

    override var holdMs: Long
        get() = prefs.getLong("holdMs", DEFAULT_HOLD_MS)
        set(value) = prefs.edit { putLong("holdMs", value) }

    override var doubleMs: Long
        get() = prefs.getLong("doubleMs", DEFAULT_DOUBLE_MS)
        set(value) = prefs.edit { putLong("doubleMs", value) }

    var vibrate: Boolean
        get() = prefs.getBoolean("vibrate", true)
        set(value) = prefs.edit { putBoolean("vibrate", value) }

    /**
     * Apps Close all apps removes from the task view but never force-stops.
     * The returned set is a copy: SharedPreferences hands out its own.
     */
    var keepRunning: Set<String>
        get() = prefs.getStringSet("keepRunning", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("keepRunning", value.toSet()) }

    var setupDone: Boolean
        get() = prefs.getBoolean("setupDone", false)
        set(value) = prefs.edit { putBoolean("setupDone", value) }

    private fun key(button: PhysicalButton, gesture: Gesture) = "map.${button.name}.${gesture.name}"

    companion object {
        /** What a fresh install does: the familiar Back gestures, plus Select for mouse mode. */
        val DEFAULTS = mapOf(
            (PhysicalButton.BACK to Gesture.DOUBLE) to ButtonAction.RECENTS,
            (PhysicalButton.BACK to Gesture.HOLD) to ButtonAction.SWAP_SCREENS,
            (PhysicalButton.SELECT to Gesture.DOUBLE) to ButtonAction.MOUSE_MODE,
        )
        const val DEFAULT_HOLD_MS = 800L
        const val DEFAULT_DOUBLE_MS = 300L
        val HOLD_CHOICES = (400L..1500L step 100L).toList()
        val DOUBLE_CHOICES = (150L..500L step 50L).toList()
    }
}
