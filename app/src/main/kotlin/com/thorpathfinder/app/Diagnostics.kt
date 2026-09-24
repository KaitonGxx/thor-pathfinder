package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.hardware.display.DisplayManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityManager
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything worth knowing when something isn't working, in one block of text
 * the user can paste into an issue.
 *
 * It answers, without Shizuku and without adb, the questions that otherwise
 * take a round of messages each: what the device reports itself as, what
 * Pathfinder made of that, whether Android has the service installed as well
 * as enabled, whether the second screen is there, which AYN settings that are
 * known to change behaviour are on, and what every profile actually maps.
 *
 * Blocking in places (it asks Shizuku for one command), so build it off the
 * main thread.
 */
object Diagnostics {

    /** AYN's own settings that are known to change how Pathfinder behaves. */
    private const val FOCUS_LOCK = "screen_focus_lock"
    private const val SYSTEM_KEY_FOCUS_LOCK = "enable_system_key_focus_lock"
    private const val MOUSE_MODE = "global_gamepad_to_mouse_mode"

    /** The key every release is signed with, so a report says whose build it is. */
    private const val RELEASE_CERT = "2706e85ba69b3f77e37227b4c0a0f99311fc73434d7264216f7bf32cbe439897"

    /** Pathfinder's own log tags, for the tail at the end of the report. */
    private val LOG_TAGS = listOf(
        "PathfinderSwap", "PathfinderClose", "PathfinderShell",
        "PathfinderMouse", "PathfinderRecord", "PathfinderUpdate",
    )

    fun report(context: Context, state: SystemState): String = buildString {
        val time = runCatching {
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrDefault("unknown")

        appendLine("Thor Pathfinder diagnostics — $time")
        appendLine()
        appendLine(
            "This lists your Thor's model and firmware, Pathfinder's settings, and the package " +
                "names of apps your shortcuts use. Have a read before posting it anywhere public.",
        )
        appendLine()

        pathfinder(context, state)
        device(context, state)
        accessibility(context)
        shizuku(context)
        screens(context)
        aynSettings(context)
        updates(context)
        watchdog(context)
        history(context)
        profiles(context)
        recentLogs()
    }

    /**
     * The answer to "it keeps turning itself off": when the switch changed and
     * when the service ran, rather than only what is true at this moment.
     */
    private fun StringBuilder.history(context: Context) {
        section("Service history")
        val events = ServiceLog.events(context)
        if (events.isEmpty()) {
            appendLine("  (nothing recorded yet)")
        } else {
            events.forEach { appendLine("  $it") }
        }
        appendLine()
        ServiceLog.capture(context)?.let { capture ->
            section("What the system said around the last stop")
            capture.lines().forEach { appendLine("  $it") }
            appendLine()
        }
    }

    private fun StringBuilder.pathfinder(context: Context, state: SystemState) {
        section("Pathfinder")
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("unknown")
        row("Version", version)
        // Two different things: Android says it is on, and the service actually got going.
        row("Switched on in Android", yesNo(state.serviceListed))
        row("Started by Android", yesNo(state.serviceOn))
        row("Service running", yesNo(PathfinderService.running))
        if (state.serviceStuck) {
            row("", "SWITCHED ON BUT NEVER STARTED — switch it off and on again")
        }
        row("Device check", describe(state.device))
        row("Tested model", yesNo(Device.tested(Build.MODEL)))
        row("Build type", buildType(context))
        row("Signed by", signer(context))
        row("Installed by", installer(context))
        row("Installed", packageTimes(context))
        row("Can draw overlays", yesNo(Settings.canDrawOverlays(context)))
        row("Battery optimised", batteryOptimised(context))
        appendLine()
    }

    private fun buildType(context: Context) =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "DEBUGGABLE — not a release build"
        } else {
            "release"
        }

    /**
     * A build signed with another key is someone's own, which matters before
     * anyone spends time on a bug in "the release".
     */
    private fun signer(context: Context): String = runCatching {
        val info = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners.orEmpty()
        if (signers.isEmpty()) return "unknown"
        val digest = MessageDigest.getInstance("SHA-256").digest(signers.first().toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        if (hex == RELEASE_CERT) "the official release key" else "a DIFFERENT key ($hex)"
    }.getOrDefault("unknown")

    private fun installer(context: Context): String = runCatching {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            ?: "sideloaded (no installer recorded)"
    }.getOrDefault("unknown")

    private fun packageTimes(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val fresh = info.firstInstallTime == info.lastUpdateTime
        (if (fresh) "first install" else "updated") + ", " + stamp(info.lastUpdateTime)
    }.getOrDefault("unknown")

    private fun batteryOptimised(context: Context): String = runCatching {
        val power = context.getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(context.packageName)) "no (exempt)" else "yes"
    }.getOrDefault("unknown")

    private fun stamp(millis: Long): String = runCatching {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault(millis.toString())

    private fun describe(support: Device.Support): String = when (support) {
        is Device.Support.Supported -> "supported (${support.model}, firmware ${support.firmware})"
        is Device.Support.NotAThor -> "NOT A THOR: reported model \"${support.model}\" — shortcuts stay off"
        is Device.Support.OldFirmware -> "firmware ${support.firmware} is older than Pathfinder requires"
        Device.Support.UnknownFirmware -> "firmware could not be read — shortcuts stay off"
    }

    private fun StringBuilder.device(context: Context, state: SystemState) {
        section("Device")
        row("Manufacturer", Build.MANUFACTURER)
        row("Model", Build.MODEL)
        row("Device", Build.DEVICE)
        row("Build", Build.DISPLAY)
        row("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        row("Thor Wayfinder", if (state.wayfinderInstalled) "installed, ${onOff(state.wayfinderOn)}" else "not installed")
        appendLine()
    }

    /**
     * Installed matters as much as enabled: a service Android never installed
     * is never bound either, and that looks exactly like nothing happening.
     */
    private fun StringBuilder.accessibility(context: Context) {
        section("Accessibility")
        val resolver = context.contentResolver
        row("accessibility_enabled", Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, -1).toString())
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val installed = runCatching { manager.installedAccessibilityServiceList }.getOrDefault(emptyList())
        val enabled = runCatching {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        }.getOrDefault(emptyList())
        row("Pathfinder installed", yesNo(installed.any { it.isOurs(context) }))
        row("Pathfinder enabled", yesNo(enabled.any { it.isOurs(context) }))
        list("Installed services (${installed.size})", installed.map { it.describe() })
        list("Enabled services (${enabled.size})", enabled.map { it.describe() })
        val setting = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        list("enabled_accessibility_services", setting.split(':').filter { it.isNotBlank() })
        appendLine()
    }

    private fun AccessibilityServiceInfo.isOurs(context: Context) =
        id.orEmpty().startsWith(context.packageName + "/")

    /**
     * Another service that filters key events sees the Thor's buttons too, so
     * it is worth naming when a button appears to do nothing.
     */
    private fun AccessibilityServiceInfo.describe(): String {
        val filtersKeys = capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0
        return id.orEmpty() + if (filtersKeys) "   [filters key events]" else ""
    }

    private fun StringBuilder.shizuku(context: Context) {
        section("Shizuku")
        val status = Shell.status(context)
        row("Status", status.name)
        // A status of READY only says the binder answered; run something to be sure.
        if (status == Shell.Status.READY) {
            val check = Shell.run("id")
            row("Shell check", if (check.ok) check.out.trim().ifBlank { "ok" } else "FAILED: ${check.err.trim()}")
        }
        appendLine()
    }

    private fun StringBuilder.screens(context: Context) {
        section("Screens")
        val displays = context.getSystemService(DisplayManager::class.java).displays
        for (display in displays) {
            val mode = display.mode
            row(
                "Display ${display.displayId}",
                "${mode.physicalWidth}x${mode.physicalHeight}, ${stateName(display.state)}" +
                    if (display.displayId == Display.DEFAULT_DISPLAY) " (top)" else "",
            )
        }
        val other = ScreenSwap.otherDisplay(context)
        row("Second screen", other?.let { "display ${it.displayId}" } ?: "NOT FOUND")
        appendLine()
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_OFF -> "off"
        Display.STATE_ON -> "on"
        Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "dozing"
        else -> "state $state"
    }

    /**
     * "Enable Home and Back focus lock" re-aims an injected Home or Back at
     * whichever screen Focus Mode holds, which is worth seeing in a report
     * about either of those buttons.
     */
    private fun StringBuilder.aynSettings(context: Context) {
        section("AYN settings")
        val focus = Settings.System.getInt(context.contentResolver, FOCUS_LOCK, -1)
        row("Focus Mode", "$focus ${focusName(focus)}")
        val keys = Settings.System.getInt(context.contentResolver, SYSTEM_KEY_FOCUS_LOCK, -1)
        row("Home/Back focus lock", "$keys ${onOffUnknown(keys)}")
        val mouse = Settings.System.getInt(context.contentResolver, MOUSE_MODE, -1)
        row("Mouse mode", "$mouse ${onOffUnknown2(mouse)}")
        // Pathfinder on this list is enough on its own to keep the service from starting.
        row("Auto launch list", AutoLaunchList.stored(context) ?: "(not set)")
        if (AutoLaunchList.blocksUs(context)) {
            row(
                "",
                "PATHFINDER IS ON IT — its service can't start; switch it off in " +
                    "Settings → Thor settings → Advanced Settings → APP Auto Launch Manage",
            )
        }
        appendLine()
    }

    private fun onOffUnknown2(value: Int) = when (value) {
        0 -> "(off)"
        1 -> "(on)"
        else -> "(not set)"
    }

    private fun StringBuilder.watchdog(context: Context) {
        section("Watchdog")
        row("Turned on", yesNo(Watchdog.on(context)))
        row("Running", yesNo(Watchdog.alive()))
        val log = Watchdog.log()
        if (log.isEmpty()) list("What it has done", emptyList()) else list("What it has done", log)
        appendLine()
    }

    private fun StringBuilder.updates(context: Context) {
        val settings = UpdateSettings(context)
        section("Updates")
        row("Check on open", onOff(settings.checkOnOpen))
        row("Install by itself", onOff(settings.autoInstall))
        row("Last checked", if (settings.lastCheckMs == 0L) "never" else stamp(settings.lastCheckMs))
        row("Newest seen", settings.known?.version ?: "(none)")
        appendLine()
    }

    /**
     * The last of Pathfinder's own log lines, which is where a swap or a close
     * says what it saw. Needs Shizuku, since an app cannot read logcat itself.
     */
    private fun StringBuilder.recentLogs() {
        section("Recent Pathfinder logs")
        if (!Shell.ready) {
            appendLine("  (needs Shizuku)")
            return
        }
        val result = Shell.run("logcat", "-d", "-t", "80", "-s", *LOG_TAGS.toTypedArray())
        val lines = result.out.lines().filter { it.isNotBlank() && !it.startsWith("--------") }
        if (!result.ok || lines.isEmpty()) {
            appendLine("  (nothing logged yet)")
            return
        }
        lines.takeLast(40).forEach { appendLine("  $it") }
    }

    private fun focusName(value: Int) = when (value) {
        0 -> "(auto-lock)"
        1 -> "(top screen)"
        2 -> "(bottom screen)"
        else -> "(not set)"
    }

    private fun onOffUnknown(value: Int) = when (value) {
        0 -> "(off)"
        1 -> "(ON — re-aims Home and Back at the focused screen)"
        else -> "(not set)"
    }

    private fun StringBuilder.profiles(context: Context) {
        val all = Profiles.all(context)
        val active = Profiles.activeId(context)
        val main = Profiles.mainId(context)
        section("Profiles")
        row("Count", all.size.toString())
        row("Active", "${Profiles.name(context, active)} (id $active)")
        row("Main", "${Profiles.name(context, main)} (id $main)")
        row("Map on switching", onOff(Profiles.showMap(context)))
        appendLine()

        for (profile in all) {
            val shortcuts = Shortcuts(context, profile.id)
            val marks = listOfNotNull(
                "active".takeIf { profile.id == active },
                "main".takeIf { profile.id == main },
            )
            section("Profile: ${profile.name} (id ${profile.id})" + if (marks.isEmpty()) "" else " [${marks.joinToString(", ")}]")
            var any = false
            for (button in PhysicalButton.entries) {
                for (gesture in button.gestures) {
                    val action = shortcuts.action(button, gesture)
                    if (action == ButtonAction.NORMAL) continue
                    any = true
                    row("${button.label} / ${gesture.label}", ButtonMap.describe(context, shortcuts, button, gesture, action))
                }
            }
            if (!any) appendLine("  (everything left on Normal)")
            row("Timings", "hold ${shortcuts.holdMs} ms, double-press ${shortcuts.doubleMs} ms")
            row("Vibrate", onOff(shortcuts.vibrate))
            list("Keep running", shortcuts.keepRunning.sorted())
            appendLine()
        }
    }

    private fun StringBuilder.section(title: String) = appendLine("== $title")

    private fun StringBuilder.row(name: String, value: String) = appendLine("  ${name.padEnd(26)} $value")

    private fun StringBuilder.list(name: String, values: List<String>) {
        if (values.isEmpty()) {
            row(name, "(none)")
            return
        }
        appendLine("  $name:")
        values.forEach { appendLine("    $it") }
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "NO"

    private fun onOff(value: Boolean) = if (value) "on" else "off"
}
