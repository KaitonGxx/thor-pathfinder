package com.thorpathfinder.app

import android.content.ComponentName
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.system.exitProcess

/**
 * One root task as [TaskWatcher] reports it: the screen it is on, whether it
 * shows, what kind of task it is (one of Android's activity types), and its
 * top activity as "package/class".
 */
data class TaskEntry(val display: Int, val visible: Boolean, val type: Int, val component: String?) {
    val pkg: String? get() = component?.substringBefore('/')
}

/** The helper's report format: one line per task, "display|visible|type|component". */
object TaskList {

    /** Android's activity types (WindowConfiguration.ACTIVITY_TYPE_*) that app profiles tell apart. */
    const val TYPE_STANDARD = 1
    const val TYPE_HOME = 2
    const val TYPE_RECENTS = 3

    fun line(display: Int, visible: Boolean, type: Int, component: String?): String =
        "$display|$visible|$type|${component.orEmpty()}"

    fun parse(line: String?): TaskEntry? {
        val parts = line?.split('|') ?: return null
        if (parts.size != 4) return null
        val display = parts[0].toIntOrNull() ?: return null
        val type = parts[2].toIntOrNull() ?: return null
        return TaskEntry(display, parts[1] == "true", type, parts[3].takeIf { '/' in it })
    }
}

/**
 * The helper that tells app profiles which app is where. Shizuku runs it in
 * a process of its own as the shell user (a "user service"), started and
 * stopped by [AppWatcher].
 *
 * It asks Android's task manager to call back whenever tasks change: an app
 * opening, closing, moving to the other screen, or taking focus, including
 * focus moving by a touch alone. Each callback only means "look again": after
 * a short settle it reads the focused root task and every root task, and
 * passes their screens, kinds and top activities on to Pathfinder when they
 * differ from the last report. Nothing inside any app is read, and the
 * accessibility service still receives nothing but key events.
 *
 * The task manager's interfaces are not part of the SDK. Shizuku starts this
 * process with app_process, where Android does not restrict them, so they are
 * reached by reflection and nothing of them is compiled in. The listener is a
 * plain Binder, since any call on it means the same thing.
 */
class TaskWatcher : ITaskWatcher.Stub() {

    private val thread = HandlerThread("pathfinder-tasks").apply { start() }
    private val handler = Handler(thread.looper)

    // Touched only on [thread].
    private var listener: ITaskListener? = null
    private var registered: Any? = null
    private var last: List<String>? = null

    private val taskManager: Any by lazy {
        Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
    }

    private val report = Runnable { send() }

    /** What the task manager calls: any call at all sends a fresh report once things settle. */
    private val poke = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) return super.onTransact(code, data, reply, flags)
            handler.removeCallbacks(report)
            handler.postDelayed(report, SETTLE_MS)
            return true
        }
    }.apply { attachInterface(null, LISTENER) }

    override fun watch(listener: ITaskListener) {
        handler.post {
            this.listener = listener
            last = null
            if (registered == null) {
                registered = runCatching { register() }
                    .onFailure { Log.w(TAG, "couldn't listen for task changes", it) }
                    .getOrNull()
            }
            send()
        }
    }

    override fun swap(moves: IntArray, topLevel: Int, bottomLevel: Int): Boolean {
        // Equal volumes need no change, and so can never be heard to change.
        val volumes = topLevel >= 0 && bottomLevel >= 0 && topLevel != bottomLevel
        val began = System.nanoTime()
        val steps = StringBuilder()
        fun mark(what: String) {
            synchronized(steps) { steps.append(" $what@").append((System.nanoTime() - began) / 1_000_000).append("ms") }
        }
        var topDone = false
        var bottomDone = false
        // A screen takes the other's volume just before the app headed there arrives, so that
        // app never plays at the wrong one. The app it is replacing, which is still there
        // until its own move, is the one that does; the swap moves a playing app first.
        fun volumeFor(display: Int) {
            if (!volumes) return
            if (display == 0) {
                topDone = true
                runCatching { setMediaVolume(bottomLevel) }.onFailure { Log.w(TAG, "couldn't set the media volume", it) }
                mark("media")
            } else {
                bottomDone = true
                runCatching { putSystemSetting(SECONDARY_VOLUME, topLevel) }
                    .onFailure { Log.w(TAG, "couldn't set the bottom screen's volume", it) }
                mark("bottom")
            }
        }
        val method = taskManager.javaClass.getMethod("moveRootTaskToDisplay", Int::class.java, Int::class.java)
        fun move(i: Int): Boolean {
            val ok = runCatching { method.invoke(taskManager, moves[2 * i], moves[2 * i + 1]) }
                .onFailure { Log.w(TAG, "couldn't move root task ${moves[2 * i]}", it) }
                .isSuccess
            mark("move${moves[2 * i]}")
            return ok
        }

        var ok = true
        if (moves.size == 4) {
            // A swap: both apps move at once, so the second spends the least time at the wrong
            // volume. The first's destination takes its volume before anything moves; the screen
            // it leaves takes the other's as soon as it has gone.
            volumeFor(moves[1])
            var secondOk = true
            val second = Thread { secondOk = move(1) }.apply { start() }
            ok = move(0)
            volumeFor(moves[3])
            second.join()
            ok = ok && secondOk
        } else {
            for (i in 0 until moves.size / 2) {
                volumeFor(moves[2 * i + 1])
                ok = move(i) && ok
            }
            // One app crossed: the screen it left takes the other's volume too.
            if (volumes && !topDone) volumeFor(0)
            if (volumes && !bottomDone) volumeFor(4)
        }
        Log.i(TAG, "swap steps done at:$steps")
        return ok
    }

    private fun service(name: String): Any? =
        Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, name)

    /** STREAM_MUSIC, which AudioService shows as the top screen's volume, without the volume bar. */
    private fun setMediaVolume(level: Int) {
        val audio = Class.forName("android.media.IAudioService\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, service("audio") as IBinder)!!
        audio.javaClass.getMethod(
            "setStreamVolumeWithAttribution",
            Int::class.java, Int::class.java, Int::class.java, String::class.java, String::class.java,
        ).invoke(audio, STREAM_MUSIC, level, 0, SHELL_PACKAGE, null)
    }

    /**
     * `settings put system`, without its process: the same call the settings
     * command makes, to the settings provider, as the shell user.
     */
    private fun putSystemSetting(name: String, value: Int) {
        val activity = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        val token = Binder()
        val holder = activity.javaClass.getMethod(
            "getContentProviderExternal", String::class.java, Int::class.java, IBinder::class.java, String::class.java,
        ).invoke(activity, "settings", 0, token, "*pathfinder*")!!
        try {
            val provider = holder.javaClass.getField("provider").get(holder)!!
            val extras = Bundle().apply {
                putString("value", value.toString())
                putInt("_user", 0)
            }
            val source = Class.forName("android.content.AttributionSource")
                .getConstructor(Int::class.java, String::class.java, String::class.java)
                .newInstance(Process.myUid(), SHELL_PACKAGE, null)
            val call = provider.javaClass.methods.first { it.name == "call" && it.parameterCount == 5 }
            call.invoke(provider, source, "settings", "PUT_system", name, extras)
        } finally {
            runCatching {
                activity.javaClass.getMethod("removeContentProviderExternalAsUser", String::class.java, IBinder::class.java, Int::class.java)
                    .invoke(activity, "settings", token, 0)
            }
        }
    }

    override fun destroy() {
        unregister()
        exitProcess(0)
    }

    private fun register(): Any {
        val type = Class.forName(LISTENER)
        val stand = Proxy.newProxyInstance(type.classLoader, arrayOf(type), InvocationHandler { self, method, args ->
            when (method.name) {
                "asBinder" -> poke
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.firstOrNull()
                "toString" -> "PathfinderTaskListener"
                else -> null
            }
        })
        taskManager.javaClass.getMethod("registerTaskStackListener", type).invoke(taskManager, stand)
        return stand
    }

    private fun unregister() {
        val stand = registered ?: return
        registered = null
        runCatching {
            val type = Class.forName(LISTENER)
            taskManager.javaClass.getMethod("unregisterTaskStackListener", type).invoke(taskManager, stand)
        }
    }

    private fun send() {
        val target = listener ?: return
        val now = runCatching { snapshot() }
            .onFailure { Log.w(TAG, "couldn't read the tasks", it) }
            .getOrNull() ?: return
        if (now == last) return
        last = now
        try {
            target.onTasks(now.first(), now.drop(1).toTypedArray())
        } catch (e: RemoteException) {
            // Pathfinder has gone, and this is only there for it.
            unregister()
            exitProcess(0)
        }
    }

    /** The focused root task, then every root task in the task manager's order. */
    private fun snapshot(): List<String> {
        val focused = taskManager.javaClass.getMethod("getFocusedRootTaskInfo").invoke(taskManager)
        val all = taskManager.javaClass.getMethod("getAllRootTaskInfos").invoke(taskManager) as List<*>
        return listOf(line(focused)) + all.map(::line)
    }

    private fun line(info: Any?): String {
        if (info == null) return ""
        return TaskList.line(
            display = field(info, "displayId") as? Int ?: -1,
            visible = field(info, "visible") as? Boolean ?: false,
            type = field(info, "topActivityType") as? Int ?: 0,
            component = (field(info, "topActivity") as? ComponentName)?.flattenToString(),
        )
    }

    /** A field of [target] or of a class above it (RootTaskInfo keeps most of them in TaskInfo). */
    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val found = runCatching { type!!.getDeclaredField(name) }.getOrNull()
            if (found != null) return runCatching { found.isAccessible = true; found.get(target) }.getOrNull()
            type = type.superclass
        }
        return null
    }

    private companion object {
        const val TAG = "PathfinderTasks"
        const val LISTENER = "android.app.ITaskStackListener"
        const val SECONDARY_VOLUME = "secondary_screen_volume_level"
        const val STREAM_MUSIC = 3
        const val SHELL_PACKAGE = "com.android.shell"

        /** A change arrives as a burst of callbacks (ten or more within 100 ms); report once it is over. */
        const val SETTLE_MS = 120L
    }
}
