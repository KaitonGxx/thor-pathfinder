package com.thorpathfinder.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Display
import com.thorpathfinder.app.ui.ProfileChoiceActivity
import com.thorpathfinder.app.ui.ScreenChoiceActivity
import com.thorpathfinder.app.ui.ShortcutMenuActivity
import rikka.shizuku.Shizuku

/** The rules of app profiles, kept apart from Android so they can be checked without a device. */
object AppProfiles {

    /** Pathfinder's own see-through questions, which open over an app without leaving it. */
    val QUESTIONS = setOf(
        ScreenChoiceActivity::class.java.name,
        ProfileChoiceActivity::class.java.name,
        ShortcutMenuActivity::class.java.name,
    )

    /**
     * The app the controller drives, as Focus Mode decides it: the top
     * screen's app when focus is locked to the top, the bottom screen's when
     * locked to the bottom, and in Auto-lock the focused one, which is the
     * screen last touched or the one an app last opened on.
     */
    fun controllerApp(focused: TaskEntry?, tasks: List<TaskEntry>, focusMode: Int, bottomDisplay: Int?): TaskEntry? =
        when (focusMode) {
            FocusMode.TOP -> shownOn(tasks, Display.DEFAULT_DISPLAY)
            FocusMode.BOTTOM -> bottomDisplay?.let { shownOn(tasks, it) }
            else -> focused
        }

    /** What shows on [display]: its topmost visible task that has an activity. */
    fun shownOn(tasks: List<TaskEntry>, display: Int): TaskEntry? =
        tasks.firstOrNull { it.display == display && it.visible && it.component != null }

    /**
     * Whether [task] only passes over the app underneath, which then still
     * counts as the one in front: Recents, and Pathfinder's own questions.
     */
    fun passing(task: TaskEntry, ownPackage: String): Boolean =
        task.component == null ||
            task.type == TaskList.TYPE_RECENTS ||
            (task.pkg == ownPackage && task.component.substringAfter('/') in QUESTIONS)

    /** What a switch made for an app says, on its own or as the title of the Thor picture. */
    fun switchMessage(words: Words, profile: String, app: String?): String =
        if (app == null) words.text(R.string.msg_profile, profile) else words.text(R.string.msg_profile_for, profile, app)
}

/**
 * Keeps [Profiles] told which app the controller drives, while any app is
 * linked to a profile and Shizuku is running, and reports each time that
 * changes the profile in use.
 *
 * The facts come from [TaskWatcher], which Shizuku runs as a user service;
 * this binds it when it is needed and lets it go when it isn't (no links, no
 * Shizuku, or the service stopping). Focus Mode changing is watched here,
 * since it changes which screen counts without any task moving.
 *
 * Main thread only; the listeners hop onto it.
 */
class AppWatcher(
    private val context: Context,
    /** The profile now in use, and the app it is for (null when it is the chosen one). */
    private val onSwitch: (Profiles.Profile, String?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var bound = false
    private var started = false
    private var retries = 0

    /** Uptime before which no new helper is started, after one has stopped by itself. */
    private var pauseUntil = 0L
    private var focused: TaskEntry? = null
    private var tasks: List<TaskEntry> = emptyList()

    private val args by lazy {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(1)
        Shizuku.UserServiceArgs(ComponentName(context.packageName, TaskWatcher::class.java.name))
            .processNameSuffix("tasks")
            .tag("tasks")
            .debuggable(false)
            // Tied to Pathfinder's process: it goes when Pathfinder does.
            .daemon(false)
            // A new version restarts it, so an update never talks to an old helper.
            .version(version)
    }

    private val listener = object : ITaskListener.Stub() {
        override fun onTasks(focused: String?, tasks: Array<out String>?) {
            val top = TaskList.parse(focused)
            val all = tasks.orEmpty().mapNotNull { TaskList.parse(it) }
            handler.post {
                retries = 0
                this@AppWatcher.focused = top
                this@AppWatcher.tasks = all
                decide()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val watcher = binder?.takeIf { it.pingBinder() }?.let { ITaskWatcher.Stub.asInterface(it) } ?: return
            runCatching { watcher.watch(listener) }
                .onFailure { ServiceLog.add(context, "app profiles couldn't start watching: ${it.message}") }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handler.post {
                if (!bound) return@post
                // It stopped by itself: start another after a pause, a few times
                // at most. The pause is set first, since forgetting the app in
                // front changes a preference, which calls refresh().
                retries++
                if (retries > RETRIES) {
                    pauseUntil = Long.MAX_VALUE
                    ServiceLog.add(context, "app profiles stopped: their helper kept stopping")
                } else {
                    Log.w(TAG, "the helper stopped; starting it again ($retries)")
                    pauseUntil = SystemClock.uptimeMillis() + RETRY_MS
                    handler.postDelayed({ refresh() }, RETRY_MS)
                }
                // Shizuku keeps a record of it, and a new bind would wait on that
                // record, so it is removed first.
                unbind()
                forget()
            }
        }
    }

    private val shizukuUp = Shizuku.OnBinderReceivedListener {
        retries = 0
        pauseUntil = 0L
        refresh()
    }

    private val shizukuGone = Shizuku.OnBinderDeadListener {
        bound = false
        forget()
    }

    // Kept in a field, since Android only holds a preference listener weakly.
    private val profilesChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    private val focusModeChanged = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = decide()
    }

    fun start() {
        if (started) return
        started = true
        Shizuku.addBinderReceivedListenerSticky(shizukuUp, handler)
        Shizuku.addBinderDeadListener(shizukuGone, handler)
        Profiles.watch(context, profilesChanged)
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(FocusMode.KEY),
            false,
            focusModeChanged,
        )
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        Shizuku.removeBinderReceivedListener(shizukuUp)
        Shizuku.removeBinderDeadListener(shizukuGone)
        Profiles.unwatch(context, profilesChanged)
        context.contentResolver.unregisterContentObserver(focusModeChanged)
        unbind()
        forget()
    }

    /** Whether the helper is bound, for the diagnostics report. */
    val watching: Boolean get() = bound

    /** Binds the helper when there is something to watch for, and lets it go when not. */
    private fun refresh() {
        if (!started) return
        val wanted = Profiles.hasLinks(context) && Shell.ready
        if (wanted && !bound && SystemClock.uptimeMillis() >= pauseUntil) {
            bound = runCatching { Shizuku.bindUserService(args, connection) }
                .onFailure { ServiceLog.add(context, "app profiles couldn't start their helper: ${it.message}") }
                .isSuccess
        } else if (!wanted && bound) {
            unbind()
            forget()
        }
    }

    private fun unbind() {
        if (!bound) return
        bound = false
        runCatching { Shizuku.unbindUserService(args, connection, true) }
    }

    /** Nothing is watching any more: the chosen profile applies, without a word. */
    private fun forget() {
        focused = null
        tasks = emptyList()
        Profiles.setCurrentApp(context, null)
    }

    private fun decide() {
        if (!bound) return
        val app = AppProfiles.controllerApp(
            focused,
            tasks,
            FocusMode.current(context),
            ScreenSwap.otherDisplay(context)?.displayId,
        ) ?: return
        if (AppProfiles.passing(app, context.packageName)) return
        val pkg = app.pkg ?: return
        if (pkg == Profiles.currentApp(context)) return
        val before = Profiles.activeId(context)
        Profiles.setCurrentApp(context, pkg)
        val after = Profiles.activeId(context)
        Log.i(TAG, "in front: $pkg; profile $after" + if (after != before) " (was $before)" else "")
        if (after != before) onSwitch(Profiles.active(context), Profiles.appInUse(context))
    }

    private companion object {
        const val TAG = "PathfinderApps"
        const val RETRY_MS = 2000L
        const val RETRIES = 3
    }
}
