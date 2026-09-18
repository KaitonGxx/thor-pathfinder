package com.thorpathfinder.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** What setup checks: everything Pathfinder needs from the rest of the system. */
data class SystemState(
    val device: Device.Support,
    val serviceOn: Boolean,
    val shizuku: Shell.Status,
    val wayfinderInstalled: Boolean,
    val wayfinderOn: Boolean,
) {
    val allGood: Boolean
        get() = device.ok && serviceOn && shizuku == Shell.Status.READY && !wayfinderOn

    companion object {
        const val WAYFINDER_PACKAGE = "com.thorwayfinder.app"

        fun read(context: Context): SystemState {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty().split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            val wayfinderInstalled = runCatching {
                context.packageManager.getPackageInfo(WAYFINDER_PACKAGE, 0)
            }.isSuccess
            return SystemState(
                device = Device.current,
                serviceOn = enabled.any { it.packageName == context.packageName },
                shizuku = Shell.status(context),
                wayfinderInstalled = wayfinderInstalled,
                wayfinderOn = wayfinderInstalled && enabled.any { it.packageName == WAYFINDER_PACKAGE },
            )
        }
    }
}
