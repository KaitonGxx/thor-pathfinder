package com.thorpathfinder.app

import android.os.Environment
import android.util.Log
import org.json.JSONObject

/**
 * AYN's gamepad-to-mouse mode.
 *
 * Its on/off switch is the system setting `global_gamepad_to_mouse_mode`
 * (0 or 1); com.odin.mapping watches it and switches at once. Apps may not
 * write AYN's own system settings, so the flip goes through Shizuku.
 *
 * Scroll direction: in mouse mode the right stick is the RIGHT_JOYSTICK entry
 * of type 3003 (ST_MOUSE_MOVE_TOUCHSCREEN), which drags a virtual finger, so
 * pushing up scrolls the page down. AYN's native engine (librsinput) copies
 * that entry's `reverseJoystick` into the stick's state and multiplies both
 * swipe axes by -1 when it is set; `reverseJoystick1` (labelled "axis Y" in
 * AYN's editor) is ignored for this type. com.odin.settings keeps the config
 * at <sdcard>/<ro.product.vendor.model>_Settings/global_mouse_mode_config.json
 * and loads it once, at start-up, so a change applies after a restart.
 * Blocking calls throughout: run them off the main thread.
 */
object MouseMode {

    private const val TAG = "PathfinderMouse"
    private const val SETTING = "global_gamepad_to_mouse_mode"
    private const val CONFIG_FILE = "global_mouse_mode_config.json"
    private const val BACKUP_SUFFIX = ".pathfinder-backup"

    /** Flips mouse mode; returns the new state, or null if it could not. */
    fun toggle(): Boolean? {
        if (!Shell.ready) return null
        val on = Shell.run("settings", "get", "system", SETTING).out.trim() == "1"
        val ok = Shell.run("settings", "put", "system", SETTING, if (on) "0" else "1").ok
        return if (ok) !on else null
    }

    private fun configPath(): String {
        val model = Shell.run("getprop", "ro.product.vendor.model").out.trim().replace(" ", "_")
        return "${Environment.getExternalStorageDirectory().path}/${model}_Settings/$CONFIG_FILE"
    }

    /** Whether the right stick scrolls reversed; null if AYN's config can't be read. */
    fun isScrollReversed(): Boolean? {
        if (!Shell.ready) return null
        val read = Shell.run("cat", configPath())
        if (!read.ok) return null
        return try {
            val sticks = JSONObject(read.out).getJSONArray("joystickConfigs")
            (0 until sticks.length()).map { sticks.getJSONObject(it) }
                .firstOrNull { it.optString("name") == "RIGHT_JOYSTICK" }
                ?.optBoolean("reverseJoystick")
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable mouse config: ${e.message}")
            null
        }
    }

    /** Sets the right stick's `reverseJoystick`, keeping a one-time backup of AYN's file. */
    fun setScrollReversed(reversed: Boolean): Boolean {
        if (!Shell.ready) return false
        val path = configPath()
        val read = Shell.run("cat", path)
        if (!read.ok) return false
        val edited = withRightStickReverse(read.out, reversed) ?: return false
        if (edited == read.out) return true
        // Keep AYN's original once, then replace the file through a temp copy.
        if (!Shell.sh("[ -e \"$1$2\" ] || cp \"$1\" \"$1$2\"", path, BACKUP_SUFFIX).ok) return false
        val written = Shell.sh("cat > \"$1.tmp\" && mv \"$1.tmp\" \"$1\"", path, input = edited).ok
        return written && isScrollReversed() == reversed
    }

    /**
     * Rewrites only the `reverseJoystick` value inside the RIGHT_JOYSTICK
     * object, leaving the rest of AYN's file byte for byte. Null if the entry
     * or the field is missing.
     */
    fun withRightStickReverse(json: String, reversed: Boolean): String? {
        val name = Regex(""""name"\s*:\s*"RIGHT_JOYSTICK"""").find(json) ?: return null
        val start = json.lastIndexOf('{', name.range.first)
        val end = json.indexOf('}', name.range.last)
        if (start < 0 || end < 0) return null
        val field = Regex("""("reverseJoystick"\s*:\s*)(true|false)""")
        val block = json.substring(start, end)
        if (!field.containsMatchIn(block)) return null
        return json.substring(0, start) + field.replace(block) { it.groupValues[1] + reversed } + json.substring(end)
    }

    /** Restarts the Thor (the shell user holds android.permission.REBOOT). */
    fun restart() {
        Shell.run("svc", "power", "reboot")
    }
}
