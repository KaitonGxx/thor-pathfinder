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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.R
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap

/**
 * The one-time "Welcome to 1.0" page for people updating from an earlier
 * version. New installs skip it: setup already walks them through.
 */
object Welcome {

    /** Which welcome this build shows; a later big release can bump it to show a new one. */
    const val EDITION = 1

    const val NOTES = "https://github.com/KaitonGxx/thor-pathfinder/releases/tag/v1.0.0"

    private const val PREFS = "ui"
    private const val KEY = "welcomeSeen"

    /** Due once, for someone who had Pathfinder set up before this edition. */
    fun due(context: Context, setupDone: Boolean): Boolean = setupDone && seen(context) < EDITION

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY, EDITION) }
    }

    private fun seen(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
}

/** The version this welcome is for, as the title shows it. */
private const val VERSION = "1.0"

/** Where a card on the welcome page leads. */
enum class WelcomeLink { PROFILES, COMBOS, BACKUP, LANGUAGE }

private class WelcomeItem(
    val link: WelcomeLink,
    @StringRes val title: Int,
    @StringRes val text: Int,
    @StringRes val button: Int,
    val symbol: @Composable () -> Unit,
)

private val ITEMS = listOf(
    WelcomeItem(WelcomeLink.PROFILES, R.string.w_profiles, R.string.w_profiles_text, R.string.w_profiles_button) {
        ButtonSymbol(Icons.Filled.Person)
    },
    WelcomeItem(WelcomeLink.COMBOS, R.string.w_combos, R.string.w_combos_text, R.string.w_combos_button) {
        ComboBadge()
    },
    WelcomeItem(WelcomeLink.BACKUP, R.string.w_backup, R.string.w_backup_text, R.string.w_backup_button) {
        ButtonSymbol(Icons.Filled.Share)
    },
    WelcomeItem(WelcomeLink.LANGUAGE, R.string.w_languages, R.string.w_languages_text, R.string.w_languages_button) {
        ButtonSymbol(LanguageSymbol)
    },
)

/** A speech bubble with lines in it, for the language card. */
private val LanguageSymbol: ImageVector = ImageVector.Builder(
    name = "LanguageSymbol",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // Bubble: a rounded rectangle with a tail at the bottom left.
        moveTo(4f, 3f)
        horizontalLineTo(20f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 22f, y1 = 5f)
        verticalLineTo(15f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 17f)
        horizontalLineTo(8f)
        lineTo(4f, 21f)
        verticalLineTo(17f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 2f, y1 = 15f)
        verticalLineTo(5f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 3f)
        close()
    }
}.build()

/**
 * What 1.0 brings, a card for each new feature with a button straight to it.
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
