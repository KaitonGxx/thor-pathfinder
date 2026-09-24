package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** What setup checks: everything Pathfinder needs from the rest of the system. */
data class SystemState(
    val device: Device.Support,
    /** Android has started the service: the only state in which shortcuts work. */
    val serviceOn: Boolean,
    /** Android's switch is on, whether or not it ever started the service. */
    val serviceListed: Boolean,
    val shizuku: Shell.Status,
    val wayfinderInstalled: Boolean,
    val wayfinderOn: Boolean,
    /**
     * Pathfinder is switched on in AYN's APP Auto Launch Manage, which keeps
     * its service from starting ([AutoLaunchList]). Worth fixing even while
     * the service runs, since the next restart stops it.
     */
    val autoLaunchBlocked: Boolean,
) {
    val allGood: Boolean
        get() = device.ok && serviceOn && !autoLaunchBlocked && shizuku == Shell.Status.READY && !wayfinderOn

    /**
     * Switched on but never started. Android refuses to bind a service whose
     * app has been force-stopped, or that is on AYN's auto launch list, and
     * says nothing about it; the switch stays on, so everything looks right
     * while no shortcut works. Turning the switch off and on is what makes
     * Android try again.
     */
    val serviceStuck: Boolean get() = serviceListed && !serviceOn

    companion object {
        const val WAYFINDER_PACKAGE = "com.thorwayfinder.app"

        fun read(context: Context): SystemState {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty().split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            val wayfinderInstalled = runCatching {
                context.packageManager.getPackageInfo(WAYFINDER_PACKAGE, 0)
            }.isSuccess
            // Whether the service is really going comes from the accessibility
            // manager, which lists what Android has actually bound. The setting
            // only says the switch is on, and the two do come apart.
            val running = runCatching {
                context.getSystemService(AccessibilityManager::class.java)
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.id.orEmpty().startsWith(context.packageName + "/") }
            }.getOrDefault(false)
            return SystemState(
                device = Device.current,
                serviceOn = running,
                serviceListed = enabled.any { it.packageName == context.packageName },
                shizuku = Shell.status(context),
                wayfinderInstalled = wayfinderInstalled,
                wayfinderOn = wayfinderInstalled && enabled.any { it.packageName == WAYFINDER_PACKAGE },
                autoLaunchBlocked = AutoLaunchList.blocksUs(context),
            )
        }
    }
}
