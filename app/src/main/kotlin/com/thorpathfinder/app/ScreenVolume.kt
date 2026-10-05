package com.thorpathfinder.app

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Each screen's volume, swapped along with the apps (an opt-in).
 *
 * On the Thor the top screen's volume is the ordinary media volume
 * (STREAM_MUSIC, 0 is muted) and the bottom screen's is the system setting
 * `secondary_screen_volume_level`, which AYN's volume panel writes. Both are on
 * the same 0..15 scale. A swap exchanges the two numbers. The media volume
 * follows the output in use (speaker, USB headset), so headphones need nothing
 * special: with a USB headset in, the panel still wrote that setting for the
 * bottom screen and never `secondary_screen_volume_level_for_headphones`
 * (checked on the Thor). Blocking calls: run them off the main thread.
 */
object ScreenVolume {

    private const val TAG = "PathfinderSwap"
    private const val PREFS = "ui"
    private const val KEY = "swapVolume"
    private const val SECONDARY = "secondary_screen_volume_level"
    private const val STREAM_MUSIC = "3"

    /** Whether a swap should exchange the screens' volumes too. Off until the user says so. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY, on) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The level in `cmd media_session volume --get`'s "volume is 5 in range [0..15]". */
    fun parseLevel(output: String): Int? =
        Regex("""volume is (\d+) in range""").find(output)?.groupValues?.get(1)?.toIntOrNull()

    /** A stored level, or null when it is missing ("null") or not a number. */
    fun parseSetting(output: String): Int? = output.trim().toIntOrNull()?.takeIf { it >= 0 }

    /** The two levels read before a swap: the top screen's (media volume) and the bottom's. */
    data class Levels(val top: Int, val bottom: Int)

    /** Reads both screens' volumes, or null if either can't be read. */
    fun read(): Levels? {
        val top = parseLevel(Shell.run("cmd", "media_session", "volume", "--stream", STREAM_MUSIC, "--get").out)
        // An unset bottom volume has never been changed from the panel: nothing to carry over.
        val bottom = parseSetting(Shell.run("settings", "get", "system", SECONDARY).out)
        if (top == null || bottom == null) {
            Log.w(TAG, "volume not swapped: top=$top bottom=$bottom")
            return null
        }
        return Levels(top, bottom)
    }

    /**
     * Shell commands that exchange the levels, each started in the background so
     * they run side by side. They go in the same shell command as the app moves,
     * so the moved apps spend only a moment at the other screen's old volume;
     * run as separate commands afterwards, the gap was about 200 ms and audible.
     * The caller must `wait` for them.
     */
    fun commands(levels: Levels): String =
        "cmd media_session volume --stream $STREAM_MUSIC --set ${levels.bottom} >/dev/null 2>&1 & " +
            "settings put system $SECONDARY ${levels.top} & "
}
