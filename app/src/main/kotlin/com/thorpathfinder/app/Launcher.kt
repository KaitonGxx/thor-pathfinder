package com.thorpathfinder.app

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Display

/**
 * Opening apps, and home screens, on a chosen screen of the Thor.
 *
 * Both screens are ordinary public displays, so any app may start an activity
 * on either with `setLaunchDisplayId`; no permission is needed. Calls block
 * (the Shizuku fallbacks run shell commands), so run them off the main thread.
 * Each returns a message for the user, or null when all went to plan.
 */
object Launcher {

    /** The display a [LaunchScreen] means, or null when there is no second screen. */
    fun display(context: Context, screen: LaunchScreen): Int? = when (screen) {
        LaunchScreen.BOTTOM -> ScreenSwap.otherDisplay(context)?.displayId
        else -> Display.DEFAULT_DISPLAY
    }

    /** Opens [pkg] on [screen] (top or bottom; "ask" is the caller's to resolve). */
    fun open(context: Context, pkg: String, screen: LaunchScreen): String? {
        val display = display(context, screen) ?: return "No second screen found"
        val hint = if (screen == LaunchScreen.BOTTOM && ScreenSwap.otherDisplay(context)?.state == Display.STATE_OFF) {
            // It still opens there; this says why nothing shows up.
            "The other screen is off"
        } else {
            null
        }
        return open(context, pkg, display) ?: hint
    }

    /** Opens [top] on the top screen and [bottom] on the bottom one. */
    fun openPair(context: Context, top: String, bottom: String): String? {
        val first = open(context, top, LaunchScreen.TOP)
        val second = open(context, bottom, LaunchScreen.BOTTOM)
        return first ?: second
    }

    private fun open(context: Context, pkg: String, display: Int): String? {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "That app isn't installed"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()
        if (runCatching { context.startActivity(intent, options) }.isSuccess) return null
        // Shizuku can start it as the shell instead, the way a swap moves one.
        val component = intent.component?.flattenToShortString() ?: return "Couldn't open that app"
        if (!Shell.ready) return "Couldn't open that app"
        val started = Shell.run("am", "start", "--display", display.toString(), "-n", component)
        return if (started.ok) null else "Couldn't open that app"
    }

    /**
     * Sends the [target] screens home. With Shizuku this presses Home on each
     * screen, exactly as the button would. Without it, Pathfinder starts the
     * home screen there itself (see [homeIntent]).
     */
    fun goHome(context: Context, target: HomeTarget): String? {
        val other = ScreenSwap.otherDisplay(context)?.displayId
        val displays = when (target) {
            HomeTarget.TOP -> listOf(Display.DEFAULT_DISPLAY)
            HomeTarget.BOTTOM -> listOfNotNull(other)
            HomeTarget.BOTH -> listOfNotNull(Display.DEFAULT_DISPLAY, other)
        }
        val missing = if (target != HomeTarget.TOP && other == null) "No second screen found" else null
        if (displays.isEmpty()) return missing
        if (Shell.ready) {
            // Injected keys carry scan code 0, so Pathfinder's own service ignores them.
            val pressed = Shell.sh(displays.joinToString("; ") { "input -d $it keyevent KEYCODE_HOME" })
            if (pressed.ok) return missing
        }
        for (display in displays) {
            val home = homeIntent(context, display) ?: return "Couldn't find a home screen for that screen"
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()
            if (runCatching { context.startActivity(home, options) }.isFailure) return "Couldn't go home"
        }
        return missing
    }

    /**
     * The home screen to start on [display]. The top screen takes the usual
     * home intent. The second screen's "secondary home" is claimed by several
     * apps on a Thor (Cocoon, Launcher3 and others), and asking for the
     * category opens Android's app chooser, so pick one component: the one from
     * the default launcher's own package, else Launcher3's, else the first.
     */
    fun homeIntent(context: Context, display: Int): Intent? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (display == Display.DEFAULT_DISPLAY) return home
        val pm = context.packageManager
        val default = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val secondary = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_SECONDARY_HOME)
        val candidates = pm.queryIntentActivities(secondary, 0).map { it.activityInfo }
        val pick = candidates.firstOrNull { it.packageName == default }
            ?: candidates.firstOrNull { it.packageName == LAUNCHER3 }
            ?: candidates.firstOrNull()
            ?: return null
        return secondary.setClassName(pick.packageName, pick.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val LAUNCHER3 = "com.android.launcher3"
}
