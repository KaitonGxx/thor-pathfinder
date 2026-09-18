package com.thorpathfinder.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.ButtonAction
import com.thorpathfinder.app.ButtonKind
import com.thorpathfinder.app.Gesture
import com.thorpathfinder.app.MouseMode
import com.thorpathfinder.app.PhysicalButton
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.Shortcuts
import com.thorpathfinder.app.SystemState
import com.thorpathfinder.app.UpdateCheck
import com.thorpathfinder.app.normalLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(state: SystemState, onFix: (SetupStep) -> Unit) {
    val context = LocalContext.current
    val shortcuts = remember { ObservedShortcuts(Shortcuts(context)) }
    var editing by remember { mutableStateOf<Pair<PhysicalButton, Gesture>?>(null) }
    var choosingApp by remember { mutableStateOf<Pair<PhysicalButton, Gesture>?>(null) }
    var editingTiming by remember { mutableStateOf<Timing?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(context)
            if (!state.allGood) NeedsAttention(state, onFix)
            AboutCard(onRunSetup = { onFix(SetupStep.WELCOME) })

            Text(
                "Tap a gesture to change what it does. On Back, Home and the AYN button, Pathfinder " +
                    "handles the button once any gesture is changed, and Normal still does its usual job.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhysicalButton.entries.forEach { button ->
                ButtonCard(context, button, shortcuts) { gesture -> editing = button to gesture }
            }

            MouseCard(state)
            TimingCard(shortcuts, onEdit = { editingTiming = it })
        }
    }

    editing?.let { (button, gesture) ->
        ChoiceDialog(
            title = "${button.label}: ${gesture.label}",
            options = ButtonAction.choicesFor(button),
            selected = shortcuts.action(button, gesture),
            label = { if (it == ButtonAction.NORMAL) normalLabel(button, gesture) else it.label },
            detail = { if (it.needsShizuku && state.shizuku != Shell.Status.READY) "Needs Shizuku" else null },
            onPick = { action ->
                editing = null
                if (action == ButtonAction.LAUNCH_APP) {
                    choosingApp = button to gesture
                } else {
                    shortcuts.set(button, gesture, action)
                }
            },
            onDismiss = { editing = null },
        )
    }
    choosingApp?.let { (button, gesture) ->
        AppPickerDialog(
            onPick = { pkg ->
                shortcuts.set(button, gesture, ButtonAction.LAUNCH_APP, pkg)
                choosingApp = null
            },
            onDismiss = { choosingApp = null },
        )
    }
    editingTiming?.let { timing ->
        ChoiceDialog(
            title = timing.title,
            options = timing.choices,
            selected = timing.get(shortcuts),
            label = { "$it ms" },
            onPick = {
                timing.set(shortcuts, it)
                editingTiming = null
            },
            onDismiss = { editingTiming = null },
        )
    }
}

@Composable
private fun Header(context: Context) {
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<UpdateCheck.Outcome?>(null) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Thor Pathfinder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (version != null) {
            Spacer(Modifier.width(12.dp))
            VersionBadge(version)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        OutlinedButton(
            onClick = {
                if (checking) return@OutlinedButton
                checking = true
                scope.launch {
                    outcome = withContext(Dispatchers.IO) { UpdateCheck.check(version.orEmpty()) }
                    checking = false
                }
            },
            modifier = Modifier.focusOutline(PillShape),
        ) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking…")
            } else {
                Text("Check For Updates")
            }
        }
    }

    outcome?.let {
        UpdateDialog(
            outcome = it,
            installed = version.orEmpty(),
            onOpen = { page ->
                outcome = null
                runCatching { context.openUrl(page) }
            },
            onDismiss = { outcome = null },
        )
    }
}

@Composable
private fun VersionBadge(version: String) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            "v$version",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The answer to Check For Updates, with the way to the release page. */
@Composable
private fun UpdateDialog(
    outcome: UpdateCheck.Outcome,
    installed: String,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val open = remember { FocusRequester() }
    val close = remember { FocusRequester() }
    val title: String
    val message: String
    val page: String
    when (outcome) {
        is UpdateCheck.Outcome.Available -> {
            title = "Update available"
            message = "Version ${outcome.latest.version} is out. You have $installed."
            page = outcome.latest.page
        }
        is UpdateCheck.Outcome.UpToDate -> {
            title = "You're up to date"
            message = if (UpdateCheck.isNewer(installed, outcome.latest.version)) {
                "You have $installed, which is newer than the latest release (${outcome.latest.version})."
            } else {
                "$installed is the latest version."
            }
            page = outcome.latest.page
        }
        is UpdateCheck.Outcome.Failed -> {
            title = "Couldn't check for updates"
            message = outcome.message
            page = UpdateCheck.LATEST_PAGE
        }
    }
    val update = outcome is UpdateCheck.Outcome.Available
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = { onOpen(page) },
                modifier = Modifier.focusRequester(open).focusOutline(PillShape),
            ) { Text("Open release page") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(close).focusOutline(PillShape),
            ) { Text(if (update) "Not now" else "Close") }
        },
    )
    // With an update, the release page is what they came for.
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { (if (update) open else close).requestFocus() } }
}

@Composable
private fun NeedsAttention(state: SystemState, onFix: (SetupStep) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Something needs attention",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val problems = buildList {
                if (!state.device.ok) add("This Thor's firmware isn't supported" to SetupStep.DEVICE)
                if (state.wayfinderOn) add("Thor Wayfinder is also handling the Back button" to SetupStep.WAYFINDER)
                if (!state.serviceOn) add("Pathfinder's accessibility service is off" to SetupStep.ACCESSIBILITY)
                if (state.shizuku != Shell.Status.READY) add("Shizuku isn't connected" to SetupStep.SHIZUKU)
            }
            problems.forEach { (text, step) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onFix(step) }, modifier = Modifier.focusOutline(PillShape)) { Text("Fix") }
                }
            }
        }
    }
}

/**
 * The shortcuts as Compose sees them: reads register [version] and writes bump
 * it, so only the rows that changed recompose. Rebuilding whole cards instead
 * would destroy the focused row and throw the controller's focus elsewhere.
 */
@Stable
private class ObservedShortcuts(private val store: Shortcuts) {
    private val version = mutableIntStateOf(0)

    private fun <T> observe(read: () -> T): T {
        version.intValue
        return read()
    }

    private fun changed() {
        version.intValue++
    }

    fun action(button: PhysicalButton, gesture: Gesture) = observe { store.action(button, gesture) }

    fun app(button: PhysicalButton, gesture: Gesture) = observe { store.app(button, gesture) }

    fun set(button: PhysicalButton, gesture: Gesture, action: ButtonAction, app: String? = null) {
        store.set(button, gesture, action, app)
        changed()
    }

    var holdMs: Long
        get() = observe { store.holdMs }
        set(value) {
            store.holdMs = value
            changed()
        }

    var doubleMs: Long
        get() = observe { store.doubleMs }
        set(value) {
            store.doubleMs = value
            changed()
        }

    var vibrate: Boolean
        get() = observe { store.vibrate }
        set(value) {
            store.vibrate = value
            changed()
        }
}

@Composable
private fun ButtonCard(context: Context, button: PhysicalButton, shortcuts: ObservedShortcuts, onEdit: (Gesture) -> Unit) {
    SectionCard(
        button.label,
        if (button.kind == ButtonKind.GAMEPAD) "Games still get every press, so a plain press can't be changed." else null,
    ) {
        button.gestures.forEach { gesture ->
            val action = shortcuts.action(button, gesture)
            val value = when (action) {
                ButtonAction.NORMAL -> normalLabel(button, gesture)
                ButtonAction.LAUNCH_APP -> "Open ${appLabel(context, shortcuts.app(button, gesture))}"
                else -> action.label
            }
            ValueRow(gesture.label, value) { onEdit(gesture) }
        }
    }
}

private fun appLabel(context: Context, pkg: String?): String = pkg?.let {
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
    }.getOrNull()
} ?: "an app"

@Composable
private fun MouseCard(state: SystemState) {
    val scope = rememberCoroutineScope()
    val ready = state.shizuku == Shell.Status.READY
    // null until read, or when AYN's config can't be read
    var reversed by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var needsRestart by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ready) {
        reversed = if (ready) withContext(Dispatchers.IO) { MouseMode.isScrollReversed() } else null
    }

    SectionCard(
        "Mouse mode",
        "Cursor speed and scroll sensitivity stay in the Thor's own mouse mode settings.",
    ) {
        SwitchRow(
            title = "Reverse right-stick scrolling",
            detail = when {
                !ready -> "Needs Shizuku."
                reversed == null -> "Couldn't read the Thor's mouse mode settings."
                else -> "Push the stick up to scroll up the page. Takes effect after a restart."
            },
            checked = reversed == true,
            enabled = reversed != null && !busy,
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

private enum class Timing(val title: String, val choices: List<Long>) {
    HOLD("How long to hold", Shortcuts.HOLD_CHOICES),
    DOUBLE("Double-press gap", Shortcuts.DOUBLE_CHOICES);

    fun get(s: ObservedShortcuts) = if (this == HOLD) s.holdMs else s.doubleMs

    fun set(s: ObservedShortcuts, value: Long) {
        if (this == HOLD) s.holdMs = value else s.doubleMs = value
    }
}

@Composable
private fun TimingCard(shortcuts: ObservedShortcuts, onEdit: (Timing) -> Unit) {
    SectionCard(
        "Timing",
        "With a double-press shortcut on Back, Home or the AYN button, a single press waits for the " +
            "gap to pass before it acts.",
    ) {
        ValueRow("How long to hold", "${shortcuts.holdMs} ms") { onEdit(Timing.HOLD) }
        ValueRow("Double-press gap", "${shortcuts.doubleMs} ms") { onEdit(Timing.DOUBLE) }
        SwitchRow("Vibrate when a shortcut runs", null, shortcuts.vibrate) { shortcuts.vibrate = it }
    }
}

@Composable
private fun AboutCard(onRunSetup: () -> Unit) {
    var showLicenses by remember { mutableStateOf(false) }
    SectionCard("About") {
        Text(
            "Thor Pathfinder is free software under the GNU GPL v3, made in the spirit of Thor " +
                "Wayfinder, which found the way first. It's an independent project, not affiliated " +
                "with Wayfinder or AYN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValueRow("Open-source licenses", "View") { showLicenses = true }
        ValueRow("Setup", "Run again", onClick = onRunSetup)
    }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}
