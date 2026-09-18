package com.thorpathfinder.app

import android.content.Context
import android.util.Log

/** A rectangle in screen pixels, as `uiautomator dump` prints bounds. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun encode(): String = "$left,$top,$right,$bottom"

    companion object {
        fun decode(s: String?): Box? =
            s?.split(',')?.map { it.trim().toIntOrNull() ?: return null }?.takeIf { it.size == 4 }
                ?.let { Box(it[0], it[1], it[2], it[3]) }
    }
}

/** What matters in a dump of the expanded Quick Settings panel. */
data class QsPanel(
    /** Tile labels, in order, with where each one is drawn. */
    val tiles: List<Pair<String, Box>>,
    /** The swipeable pager holding the tiles, if the panel is open. */
    val pager: Box?,
    /** The page shown and how many there are; 1 of 1 when there is no indicator. */
    val page: Int,
    val pages: Int,
) {
    fun tile(label: String): Box? = tiles.firstOrNull { it.first.equals(label, ignoreCase = true) }?.second
}

/**
 * Opening System UI's screen recorder from a button.
 *
 * The recorder's options panel ("Screen Recorder": audio, show taps, and on
 * the Thor which screen) is a dialog inside System UI that only its Quick
 * Settings tile opens: the recording service is not exported, the dialog is
 * not an activity, and `cmd statusbar click-tile` reaches third-party tiles
 * only. So this does what a finger would, through Shizuku: expand Quick
 * Settings, read the panel with `uiautomator dump`, find the tile's label on
 * whichever page it is, and tap it. The label comes from System UI's own
 * resources, so it matches in every language. Pathfinder's accessibility
 * service is not involved. Blocking: run off the main thread.
 */
object ScreenRecord {

    private const val TAG = "PathfinderRecord"
    private const val SYSTEM_UI = "com.android.systemui"
    private const val MAX_PAGES = 6

    sealed interface Outcome {
        data object Opened : Outcome
        data object TileNotFound : Outcome
        data object NeedsShizuku : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun open(context: Context): Outcome {
        if (!Shell.ready) return Outcome.NeedsShizuku
        val label = tileLabel(context)
        Shell.run("cmd", "statusbar", "expand-settings")
        Thread.sleep(EXPAND_MS)

        // Each read of the panel costs about two seconds, so head for the page
        // the tile was on last time before the first read. Swiping past the
        // last page does nothing, so an out-of-date page number is harmless.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rememberedPage = prefs.getInt(KEY_PAGE, 1)
        val rememberedPager = Box.decode(prefs.getString(KEY_PAGER, null))
        if (rememberedPage > 1 && rememberedPager != null) {
            repeat(rememberedPage - 1) { swipe(rememberedPager, forward = true) }
        }

        var panel = dump() ?: return abort(Outcome.Failed("couldn't read Quick Settings"))
        var current = panel.page
        for (target in visitOrder(current, panel.pages)) {
            if (target != current) {
                val pager = panel.pager ?: break
                swipe(pager, forward = target > current)
                current = target
                panel = dump() ?: break
            }
            val box = panel.tile(label)
            if (box != null) {
                Log.i(TAG, "tile '$label' on page $current at ${box.centerX},${box.centerY}")
                Shell.run("input", "-d", "0", "tap", box.centerX.toString(), box.centerY.toString())
                prefs.edit().putInt(KEY_PAGE, current).putString(KEY_PAGER, panel.pager?.encode()).apply()
                return Outcome.Opened
            }
        }
        Log.i(TAG, "tile '$label' not found in ${panel.pages} page(s)")
        return abort(Outcome.TileNotFound)
    }

    /** The page shown first, then the ones after it, then the ones before it. */
    fun visitOrder(current: Int, pages: Int): List<Int> =
        listOf(current) + (current + 1..minOf(pages, MAX_PAGES)) + (current - 1 downTo 1)

    private fun abort(outcome: Outcome): Outcome {
        Shell.run("cmd", "statusbar", "collapse")
        return outcome
    }

    /** The tile's label in the Thor's language, from System UI's resources. */
    fun tileLabel(context: Context): String = runCatching {
        val res = context.packageManager.getResourcesForApplication(SYSTEM_UI)
        val id = res.getIdentifier("quick_settings_screen_record_label", "string", SYSTEM_UI)
        if (id != 0) res.getString(id) else null
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Screen record"

    private fun dump(): QsPanel? {
        // uiautomator waits for the UI to settle, then writes the tree as XML.
        val result = Shell.sh(
            "uiautomator dump \"\$1\" >/dev/null 2>&1; cat \"\$1\"; rm -f \"\$1\"",
            "/data/local/tmp/thor-pathfinder-qs.xml",
        )
        return if (result.ok && result.out.contains("<hierarchy")) parse(result.out) else null
    }

    /** One page over: a quick swipe across the pager, as a finger would. */
    private fun swipe(pager: Box, forward: Boolean) {
        val width = pager.right - pager.left
        val from = if (forward) pager.right - width / 5 else pager.left + width / 5
        val to = if (forward) pager.left + width / 5 else pager.right - width / 5
        val y = pager.centerY
        Shell.run("input", "-d", "0", "swipe", "$from", "$y", "$to", "$y", "150")
        Thread.sleep(SWIPE_MS)
    }

    private val node = Regex("""<node\b[^>]*>""")
    private val bounds = Regex("""\bbounds="\[(\d+),(\d+)]\[(\d+),(\d+)]"""")
    private val pageNumbers = Regex("""(\d+)\D+(\d+)""")

    /** Reads a `uiautomator dump` of the expanded panel. */
    fun parse(xml: String): QsPanel {
        val tiles = mutableListOf<Pair<String, Box>>()
        var pager: Box? = null
        var page = 1
        var pages = 1
        for (m in node.findAll(xml)) {
            val n = m.value
            val id = attr(n, "resource-id")
            val box = bounds.find(n)?.let { b ->
                Box(b.groupValues[1].toInt(), b.groupValues[2].toInt(), b.groupValues[3].toInt(), b.groupValues[4].toInt())
            } ?: continue
            when {
                id.endsWith(":id/tile_label") -> attr(n, "text").takeIf { it.isNotBlank() }?.let { tiles += it to box }
                id.endsWith(":id/qs_pager") -> pager = box
                id.endsWith(":id/footer_page_indicator") -> pageNumbers.find(attr(n, "content-desc"))?.let {
                    page = it.groupValues[1].toInt()
                    pages = it.groupValues[2].toInt()
                }
            }
        }
        return QsPanel(tiles, pager, page, pages)
    }

    private fun attr(node: String, name: String): String =
        Regex("""\b$name="([^"]*)"""").find(node)?.groupValues?.get(1)?.let(::unescape) ?: ""

    private fun unescape(s: String) = s
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private const val EXPAND_MS = 600L
    private const val SWIPE_MS = 350L
    private const val PREFS = "screenrecord"
    private const val KEY_PAGE = "page"
    private const val KEY_PAGER = "pager"
}

/** A message for the user, or null when the panel simply opened. */
fun screenRecordOutcomeMessage(outcome: ScreenRecord.Outcome): String? = when (outcome) {
    ScreenRecord.Outcome.Opened -> null
    ScreenRecord.Outcome.TileNotFound -> "Add the Screen record tile to Quick Settings"
    ScreenRecord.Outcome.NeedsShizuku -> "Screen record needs Shizuku"
    is ScreenRecord.Outcome.Failed -> "Couldn't open screen record: ${outcome.message}"
}
