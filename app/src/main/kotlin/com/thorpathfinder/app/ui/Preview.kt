package com.thorpathfinder.app.ui

import android.app.Activity
import android.content.pm.ApplicationInfo
import com.thorpathfinder.app.Device
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.SystemState

/**
 * Debug builds only: pretend parts of the system are in another state, to look
 * at screens a set-up Thor never shows. Release builds ignore it. For example:
 *
 *     adb shell am start -n com.thorpathfinder.app/.ui.MainActivity -f 0x10008000 \
 *         --es step SHIZUKU --es shizuku NOT_INSTALLED
 *
 * Extras: `step` (a [SetupStep] to open setup at), `shizuku` (a [Shell.Status]),
 * `service` (boolean), `wayfinder` (boolean: installed and on) and `firmware`
 * (a version such as 1.0.0.300).
 */
class Preview private constructor(
    val step: SetupStep?,
    private val shizuku: Shell.Status?,
    private val service: Boolean?,
    private val wayfinder: Boolean?,
    private val firmware: String?,
) {
    fun apply(state: SystemState): SystemState = state.copy(
        shizuku = shizuku ?: state.shizuku,
        serviceOn = service ?: state.serviceOn,
        wayfinderInstalled = wayfinder ?: state.wayfinderInstalled,
        wayfinderOn = wayfinder ?: state.wayfinderOn,
        device = firmware?.let { Device.check("AYN", "AYN Thor", "Thor_V${it}_preview", 0) } ?: state.device,
    )

    companion object {
        fun from(activity: Activity): Preview? {
            if (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
            val extras = activity.intent?.extras ?: return null
            fun flag(key: String) = if (extras.containsKey(key)) extras.getBoolean(key) else null
            val preview = Preview(
                step = extras.getString("step")?.let { name -> SetupStep.entries.firstOrNull { it.name == name } },
                shizuku = extras.getString("shizuku")?.let { name -> Shell.Status.entries.firstOrNull { it.name == name } },
                service = flag("service"),
                wayfinder = flag("wayfinder"),
                firmware = extras.getString("firmware"),
            )
            return preview.takeIf {
                listOf(it.step, it.shizuku, it.service, it.wayfinder, it.firmware).any { value -> value != null }
            }
        }
    }
}
