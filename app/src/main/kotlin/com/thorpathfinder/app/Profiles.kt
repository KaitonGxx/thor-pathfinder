package com.thorpathfinder.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Named sets of shortcuts, one of which is in use at a time.
 *
 * Each profile keeps its shortcuts in a SharedPreferences file of its own,
 * and [ORIGINAL] keeps the file Pathfinder used before profiles existed, so
 * an update turns whatever the user already had into a profile with nothing
 * to migrate and nothing to lose.
 *
 * Which profile is the *main* one is the user's choice, not that storage
 * detail: the main profile is the one an "Enable" shortcut returns to, the
 * one a deleted profile falls back to, and the one that cannot be deleted.
 * It starts as [ORIGINAL] and can be moved to any profile.
 *
 * Mouse mode is not in here: it is AYN's own system setting and config file,
 * shared by the whole device, so a profile has nothing of it to remember.
 */
object Profiles {

    /** The profile that owns the shortcuts file from before profiles existed. */
    const val ORIGINAL = 0

    const val DEFAULT_MAIN_NAME = "Main profile"

    /** Enough to be useful, few enough that the dropdown stays a glance. */
    const val LIMIT = 8

    const val MAX_NAME = 20

    private const val PREFS = "profiles"
    private const val SHORTCUTS = "shortcuts"

    data class Profile(val id: Int, val name: String)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The preference file one profile's shortcuts live in. */
    fun fileName(id: Int): String = if (id == ORIGINAL) SHORTCUTS else "$SHORTCUTS.$id"

    // The rules below are plain functions so they can be checked without a device.

    /** The stored order, "0,2,3"; one profile before anything is stored. */
    fun parseIds(stored: String?): List<Int> =
        stored.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.distinct()
            .ifEmpty { listOf(ORIGINAL) }

    /** The profile after [active], wrapping round; the first one if [active] has gone. */
    fun after(ids: List<Int>, active: Int): Int =
        if (ids.isEmpty()) ORIGINAL else ids[(ids.indexOf(active) + 1) % ids.size]

    /** Enabling the profile already in use goes back to the main one. */
    fun toggled(active: Int, target: Int, main: Int): Int = if (active == target) main else target

    /**
     * The id a new profile takes. [stored] is the counter, kept so that
     * deleting the newest profile and making another cannot hand the new one
     * the old one's preference file.
     */
    fun nextId(ids: List<Int>, stored: Int): Int = maxOf(stored, (ids.maxOrNull() ?: ORIGINAL) + 1)

    /** A name for a new profile that no existing one already has. */
    fun newName(existing: List<String>): String {
        var n = existing.size + 1
        while (existing.any { it.equals("Profile $n", ignoreCase = true) }) n++
        return "Profile $n"
    }

    /** What the user typed, tidied; blank keeps [fallback]. */
    fun cleanName(typed: String, fallback: String): String =
        typed.trim().take(MAX_NAME).ifBlank { fallback }

    fun ids(context: Context): List<Int> = parseIds(prefs(context).getString("ids", null))

    fun name(context: Context, id: Int): String =
        prefs(context).getString("name.$id", null)
            ?: if (id == ORIGINAL) DEFAULT_MAIN_NAME else "Profile ${id + 1}"

    fun all(context: Context): List<Profile> = ids(context).map { Profile(it, name(context, it)) }

    fun has(context: Context, id: Int): Boolean = id in ids(context)

    /** The profile "Enable" shortcuts return to, and the one that can't be deleted. */
    fun mainId(context: Context): Int {
        val ids = ids(context)
        val stored = prefs(context).getInt("main", ORIGINAL)
        return if (stored in ids) stored else ids.first()
    }

    fun main(context: Context): Profile = mainId(context).let { Profile(it, name(context, it)) }

    /** Moves the main profile, which is the user's to choose. */
    fun setMain(context: Context, id: Int) {
        if (!has(context, id)) return
        prefs(context).edit { putInt("main", id) }
    }

    /** The profile in use, or the main one when what was stored has been deleted. */
    fun activeId(context: Context): Int {
        val stored = prefs(context).getInt("active", ORIGINAL)
        return if (has(context, stored)) stored else mainId(context)
    }

    fun active(context: Context): Profile = activeId(context).let { Profile(it, name(context, it)) }

    /**
     * Whether setup has been walked through: about the device rather than any
     * one profile, so it lives beside the list instead of in a profile that
     * could be deleted. Carried over once from where it used to be kept.
     */
    fun setupDone(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains("setupDone")) return prefs.getBoolean("setupDone", false)
        val before = context.getSharedPreferences(fileName(ORIGINAL), Context.MODE_PRIVATE)
            .getBoolean("setupDone", false)
        if (before) prefs.edit(commit = true) { putBoolean("setupDone", true) }
        return before
    }

    fun setSetupDone(context: Context, done: Boolean) {
        prefs(context).edit { putBoolean("setupDone", done) }
    }

    /** Whether switching profiles shows the Thor with that profile's shortcuts. */
    fun showMap(context: Context): Boolean = prefs(context).getBoolean("showMap", true)

    fun setShowMap(context: Context, show: Boolean) {
        prefs(context).edit { putBoolean("showMap", show) }
    }

    /**
     * Calls [listener] on the main thread whenever the list, a name or the
     * profile in use changes, wherever in the app it was changed. Android
     * only holds the listener weakly, so the caller has to keep it.
     */
    fun watch(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unwatch(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun switchTo(context: Context, id: Int): Profile {
        val target = if (has(context, id)) id else mainId(context)
        prefs(context).edit { putInt("active", target) }
        return Profile(target, name(context, target))
    }

    /** Moves to the next profile in the list, wrapping round to the first. */
    fun cycle(context: Context): Profile = switchTo(context, after(ids(context), activeId(context)))

    /** Turns [id] on, or goes back to the main profile when it is already on. */
    fun enable(context: Context, id: Int): Profile =
        switchTo(context, toggled(activeId(context), id, mainId(context)))

    /**
     * Adds a profile whose shortcuts all start at their defaults, and returns
     * it, or null when there is no room. [keepRunning] carries the
     * keep-running list over from the profile in use, which is the one
     * setting of Pathfinder's own worth having twice.
     */
    fun create(context: Context, name: String, keepRunning: Boolean): Profile? {
        val ids = ids(context)
        if (ids.size >= LIMIT) return null
        val id = nextId(ids, prefs(context).getInt("next", ORIGINAL + 1))
        // Read before the switch, so it is the list of the profile being left.
        val kept = if (keepRunning) Shortcuts(context).keepRunning else emptySet()
        prefs(context).edit(commit = true) {
            putString("ids", (ids + id).joinToString(","))
            putString("name.$id", name)
            putInt("next", id + 1)
        }
        // Its file starts empty, so every shortcut falls back to its default.
        if (kept.isNotEmpty()) {
            context.getSharedPreferences(fileName(id), Context.MODE_PRIVATE)
                .edit(commit = true) { putStringSet("keepRunning", kept) }
        }
        return Profile(id, name)
    }

    fun rename(context: Context, id: Int, name: String) {
        if (!has(context, id)) return
        prefs(context).edit { putString("name.$id", name) }
    }

    /** Whether [id] may be deleted: the main profile stays, and so does the last one. */
    fun deletable(context: Context, id: Int): Boolean =
        has(context, id) && id != mainId(context) && ids(context).size > 1

    /** Removes a profile and its shortcuts. */
    fun delete(context: Context, id: Int) {
        if (!deletable(context, id)) return
        val left = ids(context) - id
        val main = mainId(context)
        val wasActive = activeId(context) == id
        prefs(context).edit(commit = true) {
            putString("ids", left.joinToString(","))
            remove("name.$id")
            if (wasActive) putInt("active", main)
        }
        context.deleteSharedPreferences(fileName(id))
    }
}
