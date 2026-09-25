package com.thorpathfinder.app

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backups and shared profiles: JSON files the user saves and opens through
 * Android's file picker, so Pathfinder needs no storage permission.
 *
 * A backup holds every profile (its shortcuts, combos, timings, vibration and
 * keep-running list), the list of profiles with their names, links to apps,
 * the main one and the chosen one, the shortcut list's layout, and whether
 * Pathfinder checks for updates when it opens. It leaves out what belongs to
 * one Thor rather than to the user's setup: setup being done, the app in
 * front, the watchdog and automatic updates (both depend on that Thor's
 * Shizuku), and caches. A shared profile is one profile's shortcuts, its name
 * and its linked apps, added on another Thor as a new profile.
 *
 * Each preference keeps its type in the file ([encode]), so every setting
 * reads back exactly as it was saved, including ones added in later versions.
 */
object Backup {

    const val APP = "Thor Pathfinder"

    /** The file format; a file from a newer format is refused rather than half read. */
    const val FORMAT = 1

    /** Bigger than any real backup by far; anything larger isn't one. */
    const val MAX_BYTES = 1 shl 20

    /** The shortcut list's layout, and the update settings (see ShortcutPicker and Updates). */
    private const val UI = "ui"
    private const val UPDATES = "updates"

    /** Of the update settings, the one that isn't about this Thor's Shizuku or a cache. */
    private val SHARED_UPDATE_KEYS = setOf("checkOnOpen")

    /** Kept out of a backup, and kept as they are on a restore: they describe this Thor. */
    private val DEVICE_KEYS = setOf("setupDone", "now.app", "now.held")

    private const val KIND_BACKUP = "backup"
    private const val KIND_PROFILE = "profile"

    /** What an opened file holds, read and checked but not yet applied. */
    sealed interface Opened {
        /** A whole backup: [profiles] by name, and how many combos and linked apps they have. */
        data class Whole(
            val json: JSONObject,
            val version: String,
            val date: String,
            val profiles: List<String>,
            val combos: Int,
            val apps: Int,
        ) : Opened

        /** One shared profile. */
        data class Shared(
            val json: JSONObject,
            val version: String,
            val name: String,
            val shortcuts: Int,
            val combos: Int,
            val apps: Int,
        ) : Opened

        /** Why not, as a resource, with [detail] for its one blank (a version, or an error). */
        data class Unreadable(@StringRes val reason: Int, val detail: String = "") : Opened
    }

    // The rules below are plain functions so they can be checked without a device.

    /** One preference file's values, each as [type, value], so it reads back with its type. */
    fun encode(values: Map<String, *>): JSONObject {
        val json = JSONObject()
        for ((key, value) in values) {
            val tagged = when (value) {
                is String -> JSONArray().put("s").put(value)
                is Boolean -> JSONArray().put("b").put(value)
                is Int -> JSONArray().put("i").put(value)
                is Long -> JSONArray().put("l").put(value)
                is Float -> JSONArray().put("f").put(value.toDouble())
                is Set<*> -> JSONArray().put("S").put(JSONArray(value.map { it.toString() }.sorted()))
                else -> continue
            }
            json.put(key, tagged)
        }
        return json
    }

    /** The values [encode] wrote. Anything it can't read is left out rather than guessed at. */
    fun decode(json: JSONObject): Map<String, Any> {
        val values = mutableMapOf<String, Any>()
        for (key in json.keys()) {
            val tagged = json.optJSONArray(key) ?: continue
            if (tagged.length() != 2 || tagged.isNull(1)) continue
            values[key] = when (tagged.optString(0)) {
                "s" -> tagged.optString(1)
                "b" -> tagged.optBoolean(1)
                "i" -> tagged.optInt(1)
                "l" -> tagged.optLong(1)
                "f" -> tagged.optDouble(1).toFloat()
                "S" -> tagged.optJSONArray(1)?.let { list -> (0 until list.length()).map { list.optString(it) }.toSet() }
                    ?: continue
                else -> continue
            }
        }
        return values
    }

    /**
     * A shared profile's "Enable a profile" shortcuts name profiles by id,
     * which mean nothing on another Thor. One that enables the shared profile
     * itself ([from]) follows it to its new id ([to]); any other cycles
     * through the profiles instead.
     */
    fun retarget(values: Map<String, Any>, from: Int, to: Int): Map<String, Any> {
        val out = values.toMutableMap()
        for ((key, value) in values) {
            if (!key.endsWith(PROFILE_ID)) continue
            val slot = key.removeSuffix(PROFILE_ID)
            if (value == from) {
                out[key] = to
            } else {
                out.remove(key)
                out["$slot.profile"] = ProfileSwitch.CYCLE.name
            }
        }
        return out
    }

    /** [wanted], or "wanted (2)" and so on when a profile already has that name. */
    fun uniqueName(existing: List<String>, wanted: String): String {
        if (existing.none { it.equals(wanted, ignoreCase = true) }) return wanted
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val name = wanted.take(Profiles.MAX_NAME - suffix.length).trimEnd() + suffix
            if (existing.none { it.equals(name, ignoreCase = true) }) return name
            n++
        }
    }

    /** Shortcuts changed from Normal in one profile's values: its gestures, then its combos. */
    fun counts(values: Map<String, Any>): Pair<Int, Int> {
        val set = values.filterValues { it is String && it != ButtonAction.NORMAL.name }.keys
        return set.count { GESTURE.matches(it) } to set.count { COMBO.matches(it) }
    }

    /** Reads a file and says what it holds, without changing anything. */
    fun open(text: String): Opened {
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json == null || json.optString("app") != APP) return Opened.Unreadable(R.string.not_ours)
        val format = json.optInt("format", 0)
        val version = json.optString("version", "?")
        if (format > FORMAT) {
            return Opened.Unreadable(R.string.from_newer, version)
        }
        if (format < 1) return Opened.Unreadable(R.string.not_ours)
        return runCatching {
            when (json.optString("kind")) {
                KIND_BACKUP -> {
                    val list = decode(json.getJSONObject("list"))
                    val files = json.getJSONObject("profiles")
                    val ids = Profiles.parseIds(list["ids"] as? String)
                    require(ids.all { files.has(it.toString()) })
                    val profiles = ids.map { decode(files.getJSONObject(it.toString())) }
                    Opened.Whole(
                        json = json,
                        version = version,
                        date = json.optString("created").take(10),
                        profiles = ids.map { id -> list["name.$id"] as? String ?: defaultName(id) },
                        combos = profiles.sumOf { counts(it).second },
                        apps = ids.sumOf { id -> (list["apps.$id"] as? Set<*>)?.size ?: 0 },
                    )
                }
                KIND_PROFILE -> {
                    val (shortcuts, combos) = counts(decode(json.getJSONObject("shortcuts")))
                    Opened.Shared(
                        json = json,
                        version = version,
                        name = Profiles.cleanName(json.optString("name"), SHARED_NAME),
                        shortcuts = shortcuts,
                        combos = combos,
                        apps = json.optJSONArray("apps")?.length() ?: 0,
                    )
                }
                else -> Opened.Unreadable(R.string.not_ours)
            }
        }.getOrElse { Opened.Unreadable(R.string.damaged) }
    }

    // Saving and applying.

    /** Everything a backup holds, as the file's text. */
    fun backup(context: Context, version: String, created: String): String {
        val json = header(KIND_BACKUP, version, created)
        json.put("list", encode(prefs(context, Profiles.PREFS).all - DEVICE_KEYS))
        val files = JSONObject()
        for (id in Profiles.ids(context)) files.put(id.toString(), encode(prefs(context, Profiles.fileName(id)).all - DEVICE_KEYS))
        json.put("profiles", files)
        json.put("ui", encode(prefs(context, UI).all))
        json.put("updates", encode(prefs(context, UPDATES).all.filterKeys { it in SHARED_UPDATE_KEYS }))
        return json.toString(2)
    }

    /** Replaces every profile and shared setting with [whole]'s. */
    fun restore(context: Context, whole: Opened.Whole) {
        val json = whole.json
        val list = decode(json.getJSONObject("list"))
        val files = json.getJSONObject("profiles")
        val ids = Profiles.parseIds(list["ids"] as? String)
        // Profiles that aren't in the backup go, emptied first so no cached copy keeps them.
        for (id in Profiles.ids(context) - ids.toSet()) {
            val gone = prefs(context, Profiles.fileName(id))
            gone.edit(commit = true) { clear() }
            context.deleteSharedPreferences(Profiles.fileName(id))
        }
        for (id in ids) replace(prefs(context, Profiles.fileName(id)), decode(files.getJSONObject(id.toString())))
        replace(prefs(context, Profiles.PREFS), list)
        json.optJSONObject("ui")?.let { replace(prefs(context, UI), decode(it)) }
        json.optJSONObject("updates")?.let { updates ->
            val values = decode(updates).filterKeys { it in SHARED_UPDATE_KEYS }
            prefs(context, UPDATES).edit(commit = true) { values.forEach { (key, value) -> put(key, value) } }
        }
    }

    /** One profile as a file to share. */
    fun share(context: Context, id: Int, version: String, created: String): String {
        val json = header(KIND_PROFILE, version, created)
        json.put("id", id)
        json.put("name", Profiles.name(context, id))
        json.put("shortcuts", encode(prefs(context, Profiles.fileName(id)).all - DEVICE_KEYS))
        json.put("apps", JSONArray(Profiles.linkedApps(context, id).sorted()))
        return json.toString(2)
    }

    /**
     * Adds [shared] as a new profile, or null when there's no room. Its
     * linked apps come along when they are installed ([installed]) and not
     * linked to a profile here already.
     */
    fun addShared(context: Context, shared: Opened.Shared, installed: (String) -> Boolean): Profiles.Profile? {
        val json = shared.json
        val name = uniqueName(Profiles.all(context).map { it.name }, shared.name)
        val made = Profiles.create(context, name, keepRunning = false) ?: return null
        val values = retarget(decode(json.getJSONObject("shortcuts")), from = json.optInt("id", -1), to = made.id)
        replace(prefs(context, Profiles.fileName(made.id)), values)
        val apps = json.optJSONArray("apps")
            ?.let { list -> (0 until list.length()).map { list.optString(it) } }
            .orEmpty()
            .filter { it.isNotBlank() && Profiles.profileFor(context, it) == null && installed(it) }
            .toSet()
        if (apps.isNotEmpty()) Profiles.setLinkedApps(context, made.id, apps)
        return made
    }

    /** A file name for a shared profile: its name, with anything a file name can't hold swapped for "-". */
    fun profileFileName(name: String): String =
        "Thor-Pathfinder-profile-" + name.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "profile" } + ".json"

    private fun header(kind: String, version: String, created: String) = JSONObject()
        .put("app", APP)
        .put("kind", kind)
        .put("format", FORMAT)
        .put("version", version)
        .put("created", created)

    /** Makes [prefs] hold [values], keeping only what describes this Thor ([DEVICE_KEYS]). */
    private fun replace(prefs: SharedPreferences, values: Map<String, Any>) {
        prefs.edit(commit = true) {
            for (key in prefs.all.keys) if (key !in DEVICE_KEYS) remove(key)
            for ((key, value) in values) if (key !in DEVICE_KEYS) put(key, value)
        }
    }

    private fun SharedPreferences.Editor.put(key: String, value: Any) {
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value.map { it.toString() }.toSet())
        }
    }

    private fun prefs(context: Context, name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun defaultName(id: Int) = if (id == Profiles.ORIGINAL) Profiles.DEFAULT_MAIN_NAME else "Profile ${id + 1}"

    private const val PROFILE_ID = ".profileId"
    private const val SHARED_NAME = "Shared profile"
    private val GESTURE = Regex("""^map\.[A-Z0-9_]+\.[A-Z]+$""")
    private val COMBO = Regex("""^combo\.[A-Z0-9_+]+$""")
}
