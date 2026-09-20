package com.thorpathfinder.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.thorpathfinder.app.ButtonAction
import com.thorpathfinder.app.ButtonKind
import com.thorpathfinder.app.Gesture
import com.thorpathfinder.app.LaunchScreen
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
fun SettingsScreen(state: SystemState, onFix: (SetupStep) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shortcuts = remember { ObservedShortcuts(Shortcuts(context)) }
    val updates = remember { UpdateUi(context, scope) }
    // Every time the screen comes back, not only the first time it is built:
    // leaving Pathfinder and returning is exactly when a release may be out.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updates.checkOnOpen() }
    var editing by remember { mutableStateOf<Pair<PhysicalButton, Gesture>?>(null) }
    var choosingApp by remember { mutableStateOf<Pair<PhysicalButton, Gesture>?>(null) }
    var choosingScreen by remember { mutableStateOf<Triple<PhysicalButton, Gesture, String>?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(context, updates, onOpenSettings)
            if (!state.allGood) NeedsAttention(state, onFix)
            updates.notice?.let { release ->
                UpdateCard(updates, release, onOpenPage = { runCatching { context.openUrl(it) } })
            }
            AboutCard()

            Text(
                "Tap a gesture to change what it does. On Back, Home and the AYN button, Pathfinder " +
                    "handles the button once any gesture is changed, and Normal still does its usual job.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhysicalButton.entries.forEach { button ->
                ButtonCard(context, button, shortcuts) { gesture -> editing = button to gesture }
            }
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
                choosingApp = null
                choosingScreen = Triple(button, gesture, pkg)
            },
            onDismiss = { choosingApp = null },
        )
    }
    // Nothing is saved until the screen is picked, so Cancel leaves the gesture as it was.
    choosingScreen?.let { (button, gesture, pkg) ->
        ChoiceDialog(
            title = "Open ${appLabel(context, pkg)} on",
            options = LaunchScreen.entries,
            selected = shortcuts.screen(button, gesture),
            label = { it.label },
            onPick = { screen ->
                shortcuts.set(button, gesture, ButtonAction.LAUNCH_APP, pkg, screen)
                choosingScreen = null
            },
            onDismiss = { choosingScreen = null },
        )
    }
}

@Composable
private fun Header(context: Context, updates: UpdateUi, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Thor Pathfinder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (updates.installed.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            VersionBadge(updates.installed)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        UpdateButton(
            label = updates.buttonLabel,
            state = updates.state,
            onClick = { updates.check(answerInDialog = true) },
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onOpenSettings, modifier = Modifier.focusOutline(PillShape)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }

    updates.answer?.let {
        UpdateDialog(
            outcome = it,
            installed = updates.installed,
            onOpen = { page ->
                updates.answer = null
                runCatching { context.openUrl(page) }
            },
            onDismiss = { updates.answer = null },
        )
    }
}

/** Says where updates stand, with a mark to match, and checks again when pressed. */
@Composable
private fun UpdateButton(label: String, state: UpdateUi.State, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        when (state) {
            is UpdateUi.State.Checking -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            is UpdateUi.State.Available -> WarningTriangle()
            is UpdateUi.State.UpToDate -> OkDot()
            else -> Unit
        }
        if (state is UpdateUi.State.Checking || state is UpdateUi.State.Available ||
            state is UpdateUi.State.UpToDate
        ) {
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
    if (state is UpdateUi.State.Available) {
        Button(onClick = onClick, modifier = Modifier.focusOutline(PillShape), content = content)
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.focusOutline(PillShape), content = content)
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

/** Something setup checks, with a line on why it happened when that isn't obvious. */
private class Problem(val text: String, val detail: String?, val step: SetupStep)

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
                if (!state.device.ok) {
                    add(Problem("This Thor's firmware isn't supported", null, SetupStep.DEVICE))
                }
                if (state.wayfinderOn) {
                    add(Problem("Thor Wayfinder is also handling the Back button", null, SetupStep.WAYFINDER))
                }
                if (!state.serviceOn) {
                    add(
                        Problem(
                            "Pathfinder's accessibility service is off",
                            // The first thing anyone sees after updating the app.
                            "Android switches accessibility services off whenever their app is " +
                                "updated. Turn it back on and your shortcuts work again.",
                            SetupStep.ACCESSIBILITY,
                        )
                    )
                }
                if (state.shizuku != Shell.Status.READY) {
                    add(
                        Problem(
                            "Shizuku isn't connected",
                            when (state.shizuku) {
                                Shell.Status.NOT_INSTALLED ->
                                    "Swapping screens, mouse mode and closing apps go through " +
                                        "Shizuku, which grants that access without rooting the Thor."
                                Shell.Status.NOT_RUNNING ->
                                    "Shizuku stops whenever the Thor restarts, so it needs " +
                                        "starting again after a reboot."
                                else -> "Shizuku is running, but hasn't let Pathfinder in yet."
                            },
                            SetupStep.SHIZUKU,
                        )
                    )
                }
            }
            problems.forEach { problem ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            problem.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        problem.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { onFix(problem.step) },
                        modifier = Modifier.focusOutline(PillShape),
                    ) { Text("Fix") }
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
internal class ObservedShortcuts(private val store: Shortcuts) {
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

    fun screen(button: PhysicalButton, gesture: Gesture) = observe { store.screen(button, gesture) }

    fun set(
        button: PhysicalButton,
        gesture: Gesture,
        action: ButtonAction,
        app: String? = null,
        screen: LaunchScreen = LaunchScreen.TOP,
    ) {
        store.set(button, gesture, action, app, screen)
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
                ButtonAction.LAUNCH_APP ->
                    "Open ${appLabel(context, shortcuts.app(button, gesture))} " +
                        "(${shortcuts.screen(button, gesture).short})"
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
private fun AboutCard() {
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
    }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}
