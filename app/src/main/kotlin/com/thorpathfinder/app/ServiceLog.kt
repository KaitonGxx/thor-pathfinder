package com.thorpathfinder.app

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings
import androidx.core.content.edit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * When Android's accessibility switch changed, and when the service started
 * and stopped.
 *
 * A report of what is true right now cannot answer "it keeps turning itself
 * off", because that is a change rather than a state. This keeps the last few
 * changes with the time of each, so a report can show what happened and in
 * what order, and the diagnostics page prints them.
 *
 * Written with commit so an entry survives the process being killed straight
 * afterwards, which is exactly when the interesting ones happen.
 */
object ServiceLog {

    private const val PREFS = "servicelog"
    private const val EVENTS = "events"
    private const val LAST_LISTED = "lastListed"
    private const val PENDING = "pending"
    private const val CAPTURE = "capture"
    private const val LIMIT = 40

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(context: Context, event: String) {
        val stamp = runCatching {
            Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
        }.getOrDefault("")
        val kept = events(context).takeLast(LIMIT - 1) + "$stamp  $event"
        prefs(context).edit(commit = true) { putString(EVENTS, kept.joinToString("\n")) }
    }

    fun events(context: Context): List<String> =
        prefs(context).getString(EVENTS, null).orEmpty().lines().filter { it.isNotBlank() }

    /** Whether Android's switch currently names Pathfinder. */
    fun listed(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == context.packageName }

    /**
     * Watches the switch and records every time it changes. The setting is
     * readable by any app, so this works whether or not the service is
     * running. Unregister with [stop].
     */
    fun watch(context: Context, handler: Handler, onChange: () -> Unit = {}): ContentObserver {
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                record(context)
                onChange()
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                observer,
            )
        }
        // The setting may have changed while nothing was watching.
        record(context)
        return observer
    }

    fun stop(context: Context, observer: ContentObserver?) {
        observer ?: return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    /**
     * What the system said around an unexplained stop.
     *
     * Nothing reveals who wrote the accessibility setting: the change arrives
     * with no caller attached. What does show up is a force-stop, which
     * ActivityManager logs together with the pid that asked for it, along with
     * kills, low-memory reaps and package replacements. Reading the log needs
     * Shizuku, since it runs as the shell user.
     *
     * Taken at the next start rather than at the moment itself: the process is
     * usually being killed just then, and the log buffer keeps the history
     * anyway. Blocking, so keep it off the main thread.
     */
    fun captureIfPending(context: Context) {
        val prefs = prefs(context)
        val why = prefs.getString(PENDING, null) ?: return
        if (!Shell.ready) return
        val result = Shell.run("logcat", "-d", "-t", "800")
        if (!result.ok) return
        val lines = result.out.lineSequence()
            .filter { line ->
                line.contains(context.packageName) ||
                    line.contains("Force stopping") ||
                    line.contains("AccessibilityManagerService") ||
                    line.contains("lowmemorykiller") ||
                    line.contains("am_kill")
            }
            .toList()
            .takeLast(40)
        prefs.edit(commit = true) {
            remove(PENDING)
            putString(CAPTURE, (listOf("after: $why") + lines).joinToString("\n"))
        }
    }

    /**
     * Records the service starting, and notices when the run before it never
     * recorded a stop: that means it was killed rather than switched off, so
     * the log is worth a look.
     */
    fun noteStart(context: Context) {
        val last = events(context).lastOrNull { it.contains("service ") }
        if (last != null && last.contains("service started")) {
            markPending(context, "the service stopped without saying so, so something ended it")
        }
        add(context, "service started")
    }

    /** The last capture, for the diagnostics report. */
    fun capture(context: Context): String? = prefs(context).getString(CAPTURE, null)

    /** Something worth explaining happened; look at the log at the next start. */
    private fun markPending(context: Context, why: String) {
        prefs(context).edit(commit = true) { putString(PENDING, why) }
    }

    /** Only writes when the answer changed, since the setting notifies freely. */
    private fun record(context: Context) {
        val listed = listed(context)
        val prefs = prefs(context)
        if (prefs.contains(LAST_LISTED) && prefs.getBoolean(LAST_LISTED, false) == listed) return
        val first = !prefs.contains(LAST_LISTED)
        prefs.edit(commit = true) { putBoolean(LAST_LISTED, listed) }
        if (first) {
            add(context, "watching: Android's switch is " + if (listed) "on" else "off")
        } else {
            add(context, "Android's switch turned " + if (listed) "ON" else "OFF")
            if (!listed) markPending(context, "Android's switch turned off")
        }
    }
}
