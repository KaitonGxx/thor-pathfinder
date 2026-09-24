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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.thorpathfinder.app.ButtonAction
import com.thorpathfinder.app.ButtonKind
import com.thorpathfinder.app.CloseTarget
import com.thorpathfinder.app.Gesture
import com.thorpathfinder.app.HomeTarget
import com.thorpathfinder.app.LaunchScreen
import com.thorpathfinder.app.PhysicalButton
import com.thorpathfinder.app.ProfileSwitch
import com.thorpathfinder.app.Profiles
import com.thorpathfinder.app.RecentTasks
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
    // A different profile means different shortcuts, so every card is redrawn.
    val profiles = rememberProfileUi(onSwitched = shortcuts::reloaded)
    val updates = remember { UpdateUi(context, scope) }
    // Every time the screen comes back, not only the first time it is built:
    // leaving Pathfinder and returning is exactly when a release may be out.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updates.checkOnOpen() }
    var flow by remember { mutableStateOf<EditFlow?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(context, updates, onOpenSettings)
            ProfileBar(profiles)
            if (!state.allGood) NeedsAttention(state, onFix)
            updates.notice?.let { release ->
                UpdateCard(updates, release, onOpenPage = { runCatching { context.openUrl(it) } })
            }
            Text(
                "Tap a gesture to change what it does. On Back, Home and the AYN button, Pathfinder " +
                    "handles the button once any gesture is changed, and Normal still does its usual job.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhysicalButton.entries.forEach { button ->
                ButtonCard(context, button, shortcuts) { gesture -> flow = EditFlow.Action(button, gesture) }
            }
            AboutCard()
        }
    }

    flow?.let { current -> EditDialogs(current, context, state, shortcuts, onFlow = { flow = it }) }
}

/**
 * Where the edit of one gesture has got to. "Open an app" and "Home" ask
 * further questions after the action; nothing is saved until the last one,
 * so Cancel at any step leaves the gesture as it was.
 */
private sealed interface EditFlow {
    val button: PhysicalButton
    val gesture: Gesture

    data class Action(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    /** One app, or one on each screen? */
    data class HowMany(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    data class OneApp(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    data class OneScreen(override val button: PhysicalButton, override val gesture: Gesture, val pkg: String) : EditFlow

    data class TopApp(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    data class BottomApp(override val button: PhysicalButton, override val gesture: Gesture, val top: String) : EditFlow

    /** Which screens a Home shortcut sends home. */
    data class HomeWhere(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    /** Which apps a Close app(s) shortcut closes. */
    data class CloseWhich(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    /** The apps a "Close specific apps" shortcut closes. */
    data class CloseApps(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow

    /** Which profile a Profile switcher shortcut turns on, or whether it cycles or asks. */
    data class ProfileWhich(override val button: PhysicalButton, override val gesture: Gesture) : EditFlow
}

/**
 * How the shortcut list is laid out, wide or as a list: the user's pick, kept
 * for the whole device rather than per profile, since it is about the screen.
 */
private object ShortcutListLayout {
    private const val PREFS = "ui"
    private const val KEY = "shortcutListColumns"

    fun columns(context: Context): Int =
        if (prefs(context).getInt(KEY, ChoiceLayout.WIDE) == ChoiceLayout.LIST) ChoiceLayout.LIST else ChoiceLayout.WIDE

    fun set(context: Context, columns: Int) {
        prefs(context).edit { putInt(KEY, columns) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** What choosing [action] asks next, before anything is saved; null when choosing it is enough. */
private fun nextStep(action: ButtonAction, button: PhysicalButton, gesture: Gesture): EditFlow? =
    when (action) {
        ButtonAction.LAUNCH_APP -> EditFlow.HowMany(button, gesture)
        ButtonAction.HOME -> EditFlow.HomeWhere(button, gesture)
        ButtonAction.CLOSE_ALL -> EditFlow.CloseWhich(button, gesture)
        ButtonAction.PROFILE -> EditFlow.ProfileWhich(button, gesture)
        else -> null
    }

@Composable
private fun EditDialogs(
    flow: EditFlow,
    context: Context,
    state: SystemState,
    shortcuts: ObservedShortcuts,
    onFlow: (EditFlow?) -> Unit,
) {
    val button = flow.button
    val gesture = flow.gesture
    val done = { onFlow(null) }
    when (flow) {
        is EditFlow.Action -> {
            var columns by remember { mutableIntStateOf(ShortcutListLayout.columns(context)) }
            ChoiceDialog(
                title = "${button.label}: ${gesture.label}",
                options = ButtonAction.choicesFor(button),
                selected = shortcuts.action(button, gesture),
                label = { if (it == ButtonAction.NORMAL) normalLabel(button, gesture) else it.label },
                detail = { if (it.needsShizuku && state.shizuku != Shell.Status.READY) "Needs Shizuku" else null },
                leadsOn = { nextStep(it, button, gesture) != null },
                columns = columns,
                onColumnsChange = {
                    columns = it
                    ShortcutListLayout.set(context, it)
                },
                onPick = { action ->
                    val next = nextStep(action, button, gesture)
                    if (next != null) {
                        onFlow(next)
                    } else {
                        shortcuts.set(button, gesture, action)
                        done()
                    }
                },
                onDismiss = done,
            )
        }
        is EditFlow.HowMany -> PickDialog(
            title = "Open an app",
            choices = listOf(
                "Open 1 App" to "On the top screen, the bottom one, or ask each time",
                "Open 2 Apps" to "One on each screen, both at once",
            ),
            onPick = { choice ->
                onFlow(if (choice == 0) EditFlow.OneApp(button, gesture) else EditFlow.TopApp(button, gesture))
            },
            onDismiss = done,
        )
        is EditFlow.OneApp -> AppPickerDialog(
            onPick = { pkg -> onFlow(EditFlow.OneScreen(button, gesture, pkg)) },
            onDismiss = done,
        )
        is EditFlow.OneScreen -> ChoiceDialog(
            title = "Open ${appLabel(context, flow.pkg)} on",
            options = LaunchScreen.entries,
            selected = shortcuts.screen(button, gesture),
            label = { it.label },
            detail = { if (it == LaunchScreen.ASK) "Choose top or bottom each time the shortcut runs" else null },
            onPick = { screen ->
                shortcuts.set(button, gesture, ButtonAction.LAUNCH_APP, flow.pkg, screen)
                done()
            },
            onDismiss = done,
        )
        is EditFlow.TopApp -> AppPickerDialog(
            title = "App for the top screen",
            onPick = { pkg -> onFlow(EditFlow.BottomApp(button, gesture, pkg)) },
            onDismiss = done,
        )
        is EditFlow.BottomApp -> AppPickerDialog(
            title = "App for the bottom screen",
            exclude = flow.top,
            onPick = { pkg ->
                shortcuts.set(button, gesture, ButtonAction.LAUNCH_APP, flow.top, LaunchScreen.TOP, second = pkg)
                done()
            },
            onDismiss = done,
        )
        is EditFlow.CloseWhich -> PickDialog(
            title = ButtonAction.CLOSE_ALL.label,
            choices = CloseTarget.entries.map { it.label to null },
            onPick = { choice ->
                val target = CloseTarget.entries[choice]
                if (target == CloseTarget.SPECIFIC) {
                    onFlow(EditFlow.CloseApps(button, gesture))
                } else {
                    shortcuts.set(button, gesture, ButtonAction.CLOSE_ALL, close = target)
                    done()
                }
            },
            onDismiss = done,
        )
        is EditFlow.CloseApps -> AppMultiPickerDialog(
            title = "Apps to close",
            initial = shortcuts.closeApps(button, gesture),
            onDone = { apps ->
                shortcuts.set(button, gesture, ButtonAction.CLOSE_ALL, close = CloseTarget.SPECIFIC, closeApps = apps)
                done()
            },
            onDismiss = done,
        )
        is EditFlow.ProfileWhich -> {
            // Cycle and Ask, then one row per profile, so the whole choice is one dialog.
            val profiles = remember { Profiles.all(context) }
            PickDialog(
                title = ButtonAction.PROFILE.label,
                choices = listOf(
                    ProfileSwitch.CYCLE.label to "Move to the next profile each press",
                    ProfileSwitch.ASK.label to "Choose from a list when the shortcut runs",
                ) + profiles.map { "Enable ${it.name}" to "Press again to go back to the main profile" },
                onPick = { choice ->
                    when (choice) {
                        0 -> shortcuts.set(button, gesture, ButtonAction.PROFILE, profile = ProfileSwitch.CYCLE)
                        1 -> shortcuts.set(button, gesture, ButtonAction.PROFILE, profile = ProfileSwitch.ASK)
                        else -> shortcuts.set(
                            button,
                            gesture,
                            ButtonAction.PROFILE,
                            profile = ProfileSwitch.ENABLE,
                            profileId = profiles[choice - 2].id,
                        )
                    }
                    done()
                },
                onDismiss = done,
            )
        }
        is EditFlow.HomeWhere -> ChoiceDialog(
            title = "Send home on",
            options = HomeTarget.entries,
            selected = shortcuts.home(button, gesture),
            label = { it.label },
            onPick = { target ->
                shortcuts.set(button, gesture, ButtonAction.HOME, home = target)
                done()
            },
            onDismiss = done,
        )
    }
}

@Composable
private fun Header(context: Context, updates: UpdateUi, onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppTitle()
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

/** The name, with "Pathfinder" in a gentle sweep of the theme's own accent colours. */
@Composable
private fun AppTitle() {
    val sweep = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    )
    Text(
        buildAnnotatedString {
            append("Thor ")
            withStyle(SpanStyle(brush = sweep, fontWeight = FontWeight.Bold)) { append("Pathfinder") }
        },
        style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.4.sp),
        fontWeight = FontWeight.SemiBold,
    )
}

/** Says where updates stand, with a mark to match, and checks again when pressed. */
@Composable
private fun UpdateButton(label: String, state: UpdateUi.State, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        when (state) {
            is UpdateUi.State.Checking -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            is UpdateUi.State.Available -> WarningTriangle()
            is UpdateUi.State.UpToDate -> OkDot()
            // Nothing checked yet, or checking on open is off: no answer to give.
            UpdateUi.State.Idle -> NeutralDot()
            else -> Unit
        }
        if (state is UpdateUi.State.Checking || state is UpdateUi.State.Available ||
            state is UpdateUi.State.UpToDate || state == UpdateUi.State.Idle
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
            ) { Text(if (update) "What's New" else "Open release page") }
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
                if (state.autoLaunchBlocked) {
                    // Named first: it is the cause of the stuck service, not another problem.
                    add(
                        Problem(
                            "AYN's APP Auto Launch Manage is blocking Pathfinder",
                            "Thor Pathfinder is switched on in that page (Settings → Thor " +
                                "settings → Advanced Settings), which stops Android from starting " +
                                "its service, so after a restart no shortcut works. Switch it off " +
                                "there, or let Pathfinder fix it.",
                            SetupStep.ACCESSIBILITY,
                        )
                    )
                } else if (state.serviceStuck) {
                    add(
                        Problem(
                            "Pathfinder is switched on but isn't running",
                            "Android has the switch on but hasn't started the service, so no " +
                                "shortcut works. It can happen after a crash, an unexpected " +
                                "restart, or an app that stops others in the background. Switch " +
                                "it off and on again to fix it.",
                            SetupStep.ACCESSIBILITY,
                        )
                    )
                } else if (!state.serviceOn) {
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

    fun second(button: PhysicalButton, gesture: Gesture) = observe { store.second(button, gesture) }

    fun home(button: PhysicalButton, gesture: Gesture) = observe { store.home(button, gesture) }

    fun close(button: PhysicalButton, gesture: Gesture) = observe { store.close(button, gesture) }

    fun closeApps(button: PhysicalButton, gesture: Gesture) = observe { store.closeApps(button, gesture) }

    fun profile(button: PhysicalButton, gesture: Gesture) = observe { store.profile(button, gesture) }

    fun profileId(button: PhysicalButton, gesture: Gesture) = observe { store.profileId(button, gesture) }

    /** Another profile is in use, so every shortcut on the screen may have changed. */
    fun reloaded() = changed()

    fun set(
        button: PhysicalButton,
        gesture: Gesture,
        action: ButtonAction,
        app: String? = null,
        screen: LaunchScreen = LaunchScreen.TOP,
        second: String? = null,
        home: HomeTarget? = null,
        close: CloseTarget = CloseTarget.ALL,
        closeApps: Set<String> = emptySet(),
        profile: ProfileSwitch = ProfileSwitch.CYCLE,
        profileId: Int = Profiles.ORIGINAL,
    ) {
        store.set(button, gesture, action, app, screen, second, home, close, closeApps, profile, profileId)
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

/**
 * One button's gestures, folded away until opened. Closed, it lists whatever
 * isn't left on Normal, so the page still reads at a glance. Every card starts
 * closed when the screen opens.
 */
@Composable
private fun ButtonCard(context: Context, button: PhysicalButton, shortcuts: ObservedShortcuts, onEdit: (Gesture) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val values = button.gestures.associateWith { valueText(context, button, it, shortcuts) }
    val changed = button.gestures.filter { shortcuts.action(button, it) != ButtonAction.NORMAL }
    CollapsibleCard(
        title = button.label,
        summary = if (changed.isEmpty()) "Works as usual" else changed.joinToString("  \u00b7  ") { "${it.label}: ${values.getValue(it)}" },
        subtitle = if (button.kind == ButtonKind.GAMEPAD) "Games still get every press, so a plain press can't be changed." else null,
        expanded = open,
        onToggle = { open = !open },
    ) {
        button.gestures.forEach { gesture ->
            ValueRow(gesture.label, values.getValue(gesture)) { onEdit(gesture) }
        }
    }
}

/** What a gesture does, in words: its action, and for some actions where or what. */
private fun valueText(context: Context, button: PhysicalButton, gesture: Gesture, shortcuts: ObservedShortcuts): String {
    val action = shortcuts.action(button, gesture)
    return when (action) {
                ButtonAction.NORMAL -> normalLabel(button, gesture)
                ButtonAction.LAUNCH_APP -> {
                    val app = appLabel(context, shortcuts.app(button, gesture))
                    val second = shortcuts.second(button, gesture)
                    if (second != null) {
                        "Open $app top, ${appLabel(context, second)} bottom"
                    } else {
                        "Open $app (${shortcuts.screen(button, gesture).short})"
                    }
                }
                ButtonAction.HOME -> shortcuts.home(button, gesture)?.let { "Home (${it.short})" } ?: action.label
                ButtonAction.PROFILE -> when (shortcuts.profile(button, gesture)) {
                    ProfileSwitch.CYCLE -> ProfileSwitch.CYCLE.label
                    ProfileSwitch.ASK -> "Switch profile (ask)"
                    ProfileSwitch.ENABLE -> "Enable " + Profiles.name(context, shortcuts.profileId(button, gesture))
                }
                ButtonAction.CLOSE_ALL -> when (val target = shortcuts.close(button, gesture)) {
                    CloseTarget.SPECIFIC ->
                        "Close " + RecentTasks.names(shortcuts.closeApps(button, gesture).map { appLabel(context, it) }.sorted())
                    else -> target.label
                }
                else -> action.label
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
