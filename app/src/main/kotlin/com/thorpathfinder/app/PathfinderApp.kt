package com.thorpathfinder.app

import android.app.Application
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/**
 * Pathfinder's process, whatever started it.
 *
 * The watchdog is resumed from here rather than from the accessibility
 * service, because the service is exactly what may not be running. On AYN's
 * auto launch list ([AutoLaunchList]) Android never starts it after a
 * restart, but Shizuku still starts this process through its provider once
 * it is up, and the watchdog is what then takes Pathfinder off that list.
 */
class PathfinderApp : Application() {

    private val worker = Executors.newSingleThreadExecutor()

    /**
     * Shizuku arriving, at boot, when started by hand later, or in the first
     * process after an update, is the moment a watchdog left switched on can
     * run again, or swap an older script for this version's. Sticky, so a
     * Shizuku that was already up when the process started counts too.
     */
    private val shizukuUp = Shizuku.OnBinderReceivedListener {
        worker.execute {
            if (Watchdog.resume(this)) ServiceLog.add(this, "watchdog started again")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Shizuku.addBinderReceivedListenerSticky(shizukuUp)
    }
}
