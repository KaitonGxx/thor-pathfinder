package com.thorpathfinder.app

import android.content.Context
import android.provider.Settings

/**
 * AYN's "APP Auto Launch Manage" list, which keeps Pathfinder's accessibility
 * service from ever starting.
 *
 * That page in the Thor's own settings stores the apps switched on in it in
 * `Settings.System` [KEY], comma-separated, and its tip says a selected app
 * has its startup services turned off. The framework means it: on every
 * attempt to bind or start a service it reads the list and refuses a listed
 * app unless one of the app's processes is in the foreground, and logs
 * nothing. Android binds accessibility services at boot and when their switch
 * changes, and Pathfinder is not in front at either moment, so the switch
 * stays on while the service never starts.
 *
 * Coming off the list is not enough by itself, because Android does not try
 * again until the switch changes; [ServiceSwitch] switches the service off
 * and on straight afterwards. No restart is needed.
 */
object AutoLaunchList {

    const val KEY = "boot_auto_launch_list"

    // The rules below are plain functions so they can be checked without a device.

    /**
     * The packages on the list. Split on commas and compared as they are,
     * the same way the framework reads it.
     */
    fun parse(stored: String?): List<String> = stored.orEmpty().split(',').filter { it.isNotEmpty() }

    /** What to store once [pkg] is off the list, everyone else in order; null when nobody is left. */
    fun without(stored: String?, pkg: String): String? =
        parse(stored).filter { it != pkg }.joinToString(",").ifEmpty { null }

    /** The list as stored, or null when there is none. */
    fun stored(context: Context): String? =
        runCatching { Settings.System.getString(context.contentResolver, KEY) }.getOrNull()

    /** Whether Pathfinder is on it, which alone keeps its service from starting. */
    fun blocksUs(context: Context): Boolean = context.packageName in parse(stored(context))

    /**
     * Takes Pathfinder off the list and leaves every other app on it as it
     * was. Only the shell user may change this setting for another app, so it
     * needs Shizuku. Null when it went through or there was nothing to do, a
     * message when it didn't.
     *
     * Blocking: run it off the main thread.
     */
    fun removeUs(context: Context): String? {
        // Read what is there now, never a remembered copy: writing a stale
        // list would change somebody else's setting.
        val stored = stored(context)
        if (context.packageName !in parse(stored)) return null
        val left = without(stored, context.packageName)
        val result = if (left == null) {
            // Nobody left: the setting goes, as on a Thor that never used the page.
            Shell.run("settings", "delete", "system", KEY)
        } else {
            Shell.run("settings", "put", "system", KEY, left)
        }
        if (result.ok) return null
        return result.err.lineSequence().firstOrNull { it.isNotBlank() } ?: "couldn't change AYN's auto launch list"
    }
}
