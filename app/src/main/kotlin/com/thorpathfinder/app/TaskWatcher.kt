package com.thorpathfinder.app

import android.content.ComponentName
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
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

        /** A change arrives as a burst of callbacks (ten or more within 100 ms); report once it is over. */
        const val SETTLE_MS = 120L
    }
}
