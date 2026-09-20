package com.thorpathfinder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.MouseMode
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.Shortcuts
import com.thorpathfinder.app.SystemState
import com.thorpathfinder.app.UpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pages behind the cog, each opened from the settings menu. */
private enum class SettingsPage(val title: String, val detail: String) {
    CLOSE_ALL("Close all apps", "Which apps keep running when tasks are closed"),
    MOUSE("Mouse mode", "Which way the right stick scrolls"),
    TIMING("Timing", "How long a hold takes, and the double-press gap"),
    UPDATES("Update settings", "Checking for updates, and installing them by itself"),
    // Run setup again stays last, whatever else is added above it.
    SETUP("Run setup again", "Walk through the checks from the start"),
}

/** The cog's menu: a page per group of settings. */
@Composable
fun MoreSettingsScreen(state: SystemState, onBack: () -> Unit, onRunSetup: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // Where the menu puts focus when a page closes: the row it came from.
    var last by rememberSaveable { mutableStateOf(SettingsPage.entries.first()) }
    fun close() {
        last = page ?: last
        page = null
    }
    when (page) {
        null -> SettingsMenu(focusOn = last, onOpen = { page = it }, onBack = onBack)
        SettingsPage.CLOSE_ALL -> KeepRunningPage(onBack = ::close)
        SettingsPage.MOUSE -> MouseModePage(state, onBack = ::close)
        SettingsPage.TIMING -> TimingPage(onBack = ::close)
        SettingsPage.UPDATES -> UpdateSettingsPage(state, onBack = ::close)
        SettingsPage.SETUP -> RunSetupPage(onRunSetup = onRunSetup, onBack = ::close)
    }
}

@Composable
private fun SettingsMenu(focusOn: SettingsPage, onOpen: (SettingsPage) -> Unit, onBack: () -> Unit) {
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold("Settings", onBack = onBack) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsPage.entries.forEach { entry ->
                NavRow(
                    title = entry.title,
                    detail = entry.detail,
                    modifier = if (entry == focusOn) Modifier.focusRequester(first) else Modifier,
                    onClick = { onOpen(entry) },
                )
            }
        }
    }
}

/** Reversing the right stick in the Thor's mouse mode. */
@Composable
private fun MouseModePage(state: SystemState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ready = state.shizuku == Shell.Status.READY
    // null until read, or when AYN's config can't be read
    var reversed by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var needsRestart by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode

    LaunchedEffect(ready) {
        reversed = if (ready) withContext(Dispatchers.IO) { MouseMode.isScrollReversed() } else null
    }
    // A disabled row can't take focus, so try again once the setting is in.
    LaunchedEffect(inputMode, reversed != null) { runCatching { first.requestFocus() } }

    PageScaffold(
        "Mouse mode",
        "Cursor speed and scroll sensitivity stay in the Thor's own mouse mode settings.",
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = "Reverse right-stick scrolling",
                detail = when {
                    !ready -> "Needs Shizuku."
                    reversed == null -> "Couldn't read the Thor's mouse mode settings."
                    else -> "Pushing up scrolls up, pushing down scrolls down. Takes effect after a restart."
                },
                checked = reversed == true,
                enabled = reversed != null && !busy,
                modifier = Modifier.focusRequester(first),
                onChange = { want ->
                    busy = true
                    error = null
                    scope.launch {
                        if (withContext(Dispatchers.IO) { MouseMode.setScrollReversed(want) }) {
                            reversed = want
                            needsRestart = true
                        } else {
                            error = "Couldn't change the Thor's mouse mode settings."
                        }
                        busy = false
                    }
                },
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (needsRestart) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Restart the Thor to apply the new direction.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { MouseMode.restart() } },
                        modifier = Modifier.focusOutline(PillShape),
                    ) { Text("Restart now") }
                }
            }
        }
    }
}

/** How Pathfinder checks for updates, and whether it may install one. */
@Composable
private fun UpdateSettingsPage(state: SystemState, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { UpdateSettings(context) }
    var checkOnOpen by remember { mutableStateOf(settings.checkOnOpen) }
    var autoInstall by remember { mutableStateOf(settings.autoInstall) }
    val ready = state.shizuku == Shell.Status.READY
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold(
        "Update settings",
        "Pathfinder asks GitHub for the newest release. Nothing about you or your Thor is sent.",
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SwitchRow(
                title = "Check when Pathfinder opens",
                detail = "A quick look for a newer release each time you open the app.",
                checked = checkOnOpen,
                modifier = Modifier.focusRequester(first),
                onChange = {
                    checkOnOpen = it
                    settings.checkOnOpen = it
                },
            )
            SwitchRow(
                title = "Install updates automatically",
                detail = if (ready) {
                    "Downloads the new APK from Pathfinder's releases and installs it through " +
                        "Shizuku, with nothing to confirm. Off unless you turn it on."
                } else {
                    "Needs Shizuku: without it there is no way to install without prompts."
                },
                checked = autoInstall && ready,
                enabled = ready,
                onChange = {
                    autoInstall = it
                    settings.autoInstall = it
                },
            )
            val known = settings.known
            Text(
                buildString {
                    append(if (known == null) "No release seen yet." else "Newest release seen: ${known.version}.")
                    append(" Updates install only over the same signing key, so a copy from ")
                    append("anywhere else is refused.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

/** Setup again, but only after saying so on a screen of its own. */
@Composable
private fun RunSetupPage(onRunSetup: () -> Unit, onBack: () -> Unit) {
    // The cautious button holds focus: it takes a deliberate move to start over.
    val notNow = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { notNow.requestFocus() } }

    PageScaffold("Run setup again?", onBack = onBack) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Setup goes through the Thor's firmware, Thor Wayfinder, Pathfinder's " +
                    "accessibility service and Shizuku, one screen at a time, and checks each one " +
                    "before it moves on.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Your shortcuts, timings and the apps you keep running all stay as they are.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRunSetup, modifier = Modifier.focusOutline(PillShape)) {
                    Text("Run setup")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(notNow).focusOutline(PillShape),
                ) { Text("Not now") }
            }
        }
    }
}

private enum class Timing(val title: String, val choices: List<Long>) {
    HOLD("How long to hold", Shortcuts.HOLD_CHOICES),
    DOUBLE("Double-press gap", Shortcuts.DOUBLE_CHOICES);

    fun get(s: ObservedShortcuts) = if (this == HOLD) s.holdMs else s.doubleMs

    fun set(s: ObservedShortcuts, value: Long) {
        if (this == HOLD) s.holdMs = value else s.doubleMs = value
    }
}

/** How long a hold takes, the double-press gap, and the buzz. */
@Composable
private fun TimingPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val shortcuts = remember { ObservedShortcuts(Shortcuts(context)) }
    var editing by remember { mutableStateOf<Timing?>(null) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold(
        "Timing",
        "With a double-press shortcut on Back, Home or the AYN button, a single press waits for " +
            "the gap to pass before it acts.",
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ValueRow(
                "How long to hold",
                "${shortcuts.holdMs} ms",
                modifier = Modifier.focusRequester(first),
            ) { editing = Timing.HOLD }
            ValueRow("Double-press gap", "${shortcuts.doubleMs} ms") { editing = Timing.DOUBLE }
            SwitchRow("Vibrate when a shortcut runs", null, shortcuts.vibrate) { shortcuts.vibrate = it }
        }
    }

    editing?.let { timing ->
        ChoiceDialog(
            title = timing.title,
            options = timing.choices,
            selected = timing.get(shortcuts),
            label = { "$it ms" },
            onPick = {
                timing.set(shortcuts, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
