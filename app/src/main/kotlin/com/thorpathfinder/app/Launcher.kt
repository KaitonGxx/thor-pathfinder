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
        val display = display(context, screen) ?: return context.getString(R.string.msg_no_second_screen)
        val hint = if (screen == LaunchScreen.BOTTOM && ScreenSwap.otherDisplay(context)?.state == Display.STATE_OFF) {
            // It still opens there; this says why nothing shows up.
            context.getString(R.string.msg_other_screen_off)
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
            ?: return context.getString(R.string.msg_app_not_installed)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()
        if (runCatching { context.startActivity(intent, options) }.isSuccess) return null
        // Shizuku can start it as the shell instead, the way a swap moves one.
        val failed = context.getString(R.string.msg_couldnt_open_app)
        val component = intent.component?.flattenToShortString() ?: return failed
        if (!Shell.ready) return failed
        val started = Shell.run("am", "start", "--display", display.toString(), "-n", component)
        return if (started.ok) null else failed
    }

    /**
     * Sends the [target] screens home, by starting each screen's own home
     * screen there (see [homeIntent]).
     *
     * Pressing the real Home key would be the truer imitation of the button,
     * but it cannot be aimed reliably: AYN's "Enable Home and Back focus lock"
     * setting re-points Home and Back at whichever screen Focus Mode holds, so
     * an injected Home meant for the bottom screen sends the top one home
     * instead. A launch display is addressed directly and no key is routed, so
     * it obeys the target whatever that setting says. The key press is kept for
     * the one case the intent cannot cover: a screen with no home activity to
     * start.
     */
    fun goHome(context: Context, target: HomeTarget): String? {
        val other = ScreenSwap.otherDisplay(context)?.displayId
        val displays = when (target) {
            HomeTarget.TOP -> listOf(Display.DEFAULT_DISPLAY)
            HomeTarget.BOTTOM -> listOfNotNull(other)
            HomeTarget.BOTH -> listOfNotNull(Display.DEFAULT_DISPLAY, other)
        }
        val missing = if (target != HomeTarget.TOP && other == null) context.getString(R.string.msg_no_second_screen) else null
        if (displays.isEmpty()) return missing
        // Every screen is tried, so one bad screen cannot strand the other.
        val failed = displays.mapNotNull { home(context, it) }
        return failed.firstOrNull() ?: missing
    }

    /** Sends one screen home; null when it went. */
    private fun home(context: Context, display: Int): String? {
        val home = homeIntent(context, display)
        if (home != null) {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle()
            if (runCatching { context.startActivity(home, options) }.isSuccess) return null
        }
        // Injected keys carry scan code 0, so Pathfinder's own service ignores them.
        if (Shell.ready && Shell.run("input", "-d", display.toString(), "keyevent", "KEYCODE_HOME").ok) return null
        return context.getString(if (home == null) R.string.msg_no_home_found else R.string.msg_couldnt_go_home)
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
