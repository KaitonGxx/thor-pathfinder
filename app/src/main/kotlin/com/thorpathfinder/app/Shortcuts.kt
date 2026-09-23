package com.thorpathfinder.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The user's shortcuts and timings, in SharedPreferences.
 *
 * SharedPreferences keeps its values in memory after the first read, so the
 * accessibility service can look actions up on every key event.
 */
class Shortcuts(private val context: Context) : GestureConfig {

    /**
     * The profile in use, looked up on each read so that switching profiles
     * takes effect on the very next key event, with nothing to reload.
     * SharedPreferences instances are cached per file, so this is a lookup,
     * not a re-read of the file.
     */
    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(Profiles.fileName(Profiles.activeId(context)), Context.MODE_PRIVATE)

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

    /** With two apps, the one for the bottom screen; [app] is then the top one. */
    fun second(button: PhysicalButton, gesture: Gesture): String? =
        prefs.getString(key(button, gesture) + ".app2", null)

    /** What a CLOSE_ALL gesture closes; mappings from before the choice existed close all. */
    fun close(button: PhysicalButton, gesture: Gesture): CloseTarget =
        prefs.getString(key(button, gesture) + ".close", null)
            ?.let { name -> CloseTarget.entries.firstOrNull { it.name == name } }
            ?: CloseTarget.ALL

    /** The apps a CLOSE_ALL gesture set to [CloseTarget.SPECIFIC] closes. */
    fun closeApps(button: PhysicalButton, gesture: Gesture): Set<String> =
        prefs.getStringSet(key(button, gesture) + ".closeApps", null)?.toSet() ?: emptySet()

    /** What a PROFILE gesture does; mappings from before the choice existed cycle. */
    fun profile(button: PhysicalButton, gesture: Gesture): ProfileSwitch =
        prefs.getString(key(button, gesture) + ".profile", null)
            ?.let { name -> ProfileSwitch.entries.firstOrNull { it.name == name } }
            ?: ProfileSwitch.CYCLE

    /** The profile a PROFILE gesture set to [ProfileSwitch.ENABLE] turns on. */
    fun profileId(button: PhysicalButton, gesture: Gesture): Int =
        prefs.getInt(key(button, gesture) + ".profileId", Profiles.ORIGINAL)

    /** The screens a HOME gesture sends home; null for the old way, Android's own Home. */
    fun home(button: PhysicalButton, gesture: Gesture): HomeTarget? =
        prefs.getString(key(button, gesture) + ".home", null)
            ?.let { name -> HomeTarget.entries.firstOrNull { it.name == name } }

    fun set(
        button: PhysicalButton,
        gesture: Gesture,
        action: ButtonAction,
        app: String? = null,
        screen: LaunchScreen = LaunchScreen.TOP,
        second: String? = null,
        home: HomeTarget? = null,
        close: CloseTarget = CloseTarget.ALL,
        closeApps: Set<String> = emptySet(),
        profile: ProfileSwitch = ProfileSwitch.CYCLE,
        profileId: Int = Profiles.ORIGINAL,
    ) {
        val key = key(button, gesture)
        prefs.edit {
            putString(key, action.name)
            if (action == ButtonAction.LAUNCH_APP) {
                putString("$key.app", app)
                putString("$key.screen", screen.name)
                if (second != null) putString("$key.app2", second) else remove("$key.app2")
            } else {
                remove("$key.app")
                remove("$key.screen")
                remove("$key.app2")
            }
            if (action == ButtonAction.HOME && home != null) putString("$key.home", home.name) else remove("$key.home")
            if (action == ButtonAction.CLOSE_ALL) putString("$key.close", close.name) else remove("$key.close")
            if (action == ButtonAction.CLOSE_ALL && close == CloseTarget.SPECIFIC) {
                putStringSet("$key.closeApps", closeApps.toSet())
            } else {
                remove("$key.closeApps")
            }
            if (action == ButtonAction.PROFILE) putString("$key.profile", profile.name) else remove("$key.profile")
            if (action == ButtonAction.PROFILE && profile == ProfileSwitch.ENABLE) {
                putInt("$key.profileId", profileId)
            } else {
                remove("$key.profileId")
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

    /**
     * Whether setup has been walked through. About the device rather than any
     * one profile, so [Profiles] keeps it beside the list of profiles and no
     * new or deleted profile can make setup run again.
     */
    var setupDone: Boolean
        get() = Profiles.setupDone(context)
        set(value) = Profiles.setSetupDone(context, value)

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
