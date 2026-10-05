package com.thorpathfinder.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.R
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap

/**
 * The one-time "what's new" page for people updating from an earlier
 * version, now 1.1's. New installs skip it: setup already walks them through.
 */
object Welcome {

    /** Which welcome this build shows; a later big release can bump it to show a new one. */
    const val EDITION = 2

    const val NOTES = "https://github.com/KaitonGxx/thor-pathfinder/releases/tag/v1.1.0"

    private const val PREFS = "ui"
    private const val KEY = "welcomeSeen"

    /**
     * Due once, for someone who had Pathfinder set up before this edition and
     * then updated it. Never on a fresh install, where setup comes first, even
     * if Android restored an old "setup done" with the app's data.
     */
    fun due(context: Context, setupDone: Boolean): Boolean =
        setupDone && seen(context) < EDITION && updated(context)

    /** Whether this copy came as an update rather than a fresh install. */
    private fun updated(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    }.getOrDefault(false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY, EDITION) }
    }

    private fun seen(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
}

/** The version this welcome is for, as the title shows it. */
private const val VERSION = "1.1"

/** Where a card on the welcome page leads. */
enum class WelcomeLink { LEVELS, DISABLED, TILE, VOLUME_SWAP }

private class WelcomeItem(
    val link: WelcomeLink,
    @StringRes val title: Int,
    @StringRes val text: Int,
    @StringRes val button: Int,
    val symbol: @Composable () -> Unit,
)

private val ITEMS = listOf(
    WelcomeItem(WelcomeLink.LEVELS, R.string.w_levels, R.string.w_levels_text, R.string.w_levels_button) {
        ButtonSymbol(symbol("Sun", SUN))
    },
    WelcomeItem(WelcomeLink.DISABLED, R.string.w_disabled, R.string.w_disabled_text, R.string.w_disabled_button) {
        ButtonSymbol(symbol("Controller", CONTROLLER))
    },
    WelcomeItem(WelcomeLink.TILE, R.string.w_tile, R.string.w_tile_text, R.string.w_tile_button) {
        ButtonSymbol(symbol("Tile", TILE))
    },
    WelcomeItem(WelcomeLink.VOLUME_SWAP, R.string.w_volume, R.string.w_volume_text, R.string.w_volume_button) {
        ButtonSymbol(symbol("Speaker", SPEAKER))
    },
)

/** A 24 x 24 symbol from path data. */
private fun symbol(name: String, path: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(addPathNodes(path), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd).build()

// Material icons (Apache 2.0): light_mode and volume_up, as on the slider itself.
private const val SUN = "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5s5,-2.24 5,-5S14.76,7 12,7L12,7zM2,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1S1.45,13 2,13zM20,13l2,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1l-2,0c-0.55,0 -1,0.45 -1,1S19.45,13 20,13zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1S11,1.45 11,2zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1C11.45,19 11,19.45 11,20zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0c-0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0c0.39,-0.39 0.39,-1.03 0,-1.41L18.36,16.95zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L19.42,5.99zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41c-0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0L7.05,18.36z"
private const val SPEAKER = "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z"

// The Quick Settings tile's own controller (res/drawable/ic_tile_profile.xml).
private const val CONTROLLER = "M7,6H17C19.8,6 22,8.5 22,12V15C22,17 20.8,18 19.5,18C18.4,18 17.7,17.3 17,16.3L15.8,15H8.2L7,16.3C6.3,17.3 5.6,18 4.5,18C3.2,18 2,17 2,15V12C2,8.5 4.2,6 7,6Z M6,9.5h1.5v1.5h1.5v1.5h-1.5v1.5h-1.5v-1.5h-1.5v-1.5h1.5Z M15,10.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0Z M17,12.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0Z"

/** A Quick Settings tile: a rounded pill with a dot, as a switched-on tile looks. */
private const val TILE = "M7,7H17A5,5 0 0,1 17,17H7A5,5 0 0,1 7,7Z M7,9.5A2.5,2.5 0 1,0 7,14.5A2.5,2.5 0 1,0 7,9.5Z"

/**
 * What 1.1 brings, a card for each new feature with a button straight to it.
 * [onOpen] closes the page and goes there; Continue (or Back) goes to the
 * main screen. Either way the page has been seen.
 */
@Composable
fun WelcomeScreen(onOpen: (WelcomeLink) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onDone)
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }
    val icon = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(144, 144).asImageBitmap() }
            .getOrNull()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Continue stays below the scrolling part, so it is on screen however long the text runs.
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            ScrollingColumn(
                state = rememberScrollState(),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.width(16.dp))
                    }
                    Column {
                        val title = stringResource(R.string.w_title, VERSION)
                        val at = title.indexOf(VERSION)
                        Text(
                            buildAnnotatedString {
                                append(title)
                                if (at >= 0) addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary), at, at + VERSION.length)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.w_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ITEMS.forEachIndexed { index, item ->
                        Card(Modifier.weight(1f).fillMaxHeight()) {
                            Column(Modifier.fillMaxHeight().padding(16.dp)) {
                                Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) { item.symbol() }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(item.title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(item.text),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { onOpen(item.link) },
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                                        .focusOutline(PillShape),
                                ) { Text(stringResource(item.button)) }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { runCatching { context.openUrl(Welcome.NOTES) } },
                    modifier = Modifier.focusOutline(PillShape),
                ) { Text(stringResource(R.string.w_notes)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDone, modifier = Modifier.focusOutline(PillShape)) {
                    Text(stringResource(R.string.w_continue))
                }
            }
        }
    }
}
