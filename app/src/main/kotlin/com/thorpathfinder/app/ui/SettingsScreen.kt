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
import com.thorpathfinder.app.ComboKey
import com.thorpathfinder.app.Combos
import com.thorpathfinder.app.ButtonKind
import com.thorpathfinder.app.CloseTarget
import com.thorpathfinder.app.FocusSwitch
import com.thorpathfinder.app.Gesture
import com.thorpathfinder.app.HomeTarget
import com.thorpathfinder.app.LaunchScreen
import com.thorpathfinder.app.PhysicalButton
import com.thorpathfinder.app.R
import androidx.annotation.StringRes
import com.thorpathfinder.app.words
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.ProfileSwitch
import com.thorpathfinder.app.Profiles
import com.thorpathfinder.app.RecentTasks
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.Shortcut
import com.thorpathfinder.app.Shortcuts
import com.thorpathfinder.app.SystemState
import com.thorpathfinder.app.UpdateCheck
import com.thorpathfinder.app.normalLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    state: SystemState,
    onFix: (SetupStep) -> Unit,
    onOpenSettings: () -> Unit,
    /** A button whose card starts open, as the welcome page's "Add a combo" asks. */
    openCard: PhysicalButton? = null,
    /** Opens the welcome page again, from About. */
    onWhatsNew: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shortcuts = remember { ObservedShortcuts(Shortcuts(context)) }
    // A different profile means different shortcuts, so every card is redrawn.
    val profiles = rememberProfileUi(onSwitched = shortcuts::reloaded)
    val updates = remember { UpdateUi(context, scope) }
    // Every time the screen comes back, not only the first time it is built:
    // leaving Pathfinder and returning is exactly when a release may be out.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { updates.checkOnOpen() }
    var editing by remember { mutableStateOf<Pair<PhysicalButton, Gesture>?>(null) }
    // A combo: the button whose card is adding one, the one tapped, the one being set.
    var addingCombo by remember { mutableStateOf<ComboKey?>(null) }
    var comboActing by remember { mutableStateOf<Set<ComboKey>?>(null) }
    var comboEditing by remember { mutableStateOf<Set<ComboKey>?>(null) }

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
                stringResource(R.string.main_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhysicalButton.entries.forEach { button ->
                ButtonCard(
                    context,
                    button,
                    shortcuts,
                    startOpen = button == openCard,
                    onEditCombo = { comboActing = it },
                    onAddCombo = { addingCombo = ComboKey.of(button) },
                ) { gesture -> editing = button to gesture }
            }
            AboutCard(onWhatsNew = onWhatsNew)
        }
    }

    val words = remember(context) { context.words() }
    editing?.let { (button, gesture) ->
        ShortcutPicker(
            title = stringResource(R.string.edit_title, stringResource(button.text), stringResource(gesture.text)),
            choices = ButtonAction.choicesFor(button),
            current = shortcuts.shortcut(button, gesture),
            shizukuReady = state.shizuku == Shell.Status.READY,
            label = { if (it == ButtonAction.NORMAL) normalLabel(words, button, gesture) else words.text(it.text) },
            onChosen = { chosen ->
                shortcuts.set(button, gesture, chosen)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    addingCombo?.let { held ->
        ComboKeysDialog(
            held = held,
            onNext = { keys ->
                addingCombo = null
                comboEditing = keys
            },
            onDismiss = { addingCombo = null },
        )
    }

    comboActing?.let { keys ->
        PickDialog(
            title = Combos.label(words, keys),
            choices = listOf(
                stringResource(R.string.combo_change) to shortcuts.combo(keys)?.let { shortcutText(context, it) },
                stringResource(R.string.combo_remove) to stringResource(R.string.combo_remove_detail),
            ),
            onPick = { index ->
                comboActing = null
                if (index == 0) comboEditing = keys else shortcuts.removeCombo(keys)
            },
            onDismiss = { comboActing = null },
        )
    }

    comboEditing?.let { keys ->
        ShortcutPicker(
            title = Combos.label(words, keys),
            choices = ButtonAction.comboChoices,
            current = shortcuts.combo(keys),
            shizukuReady = state.shizuku == Shell.Status.READY,
            onChosen = { chosen ->
                shortcuts.setCombo(keys, chosen)
                comboEditing = null
            },
            onDismiss = { comboEditing = null },
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
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
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
            title = stringResource(R.string.update_available_title)
            message = stringResource(R.string.update_available_msg, outcome.latest.version, installed)
            page = outcome.latest.page
        }
        is UpdateCheck.Outcome.UpToDate -> {
            title = stringResource(R.string.up_to_date_title)
            message = if (UpdateCheck.isNewer(installed, outcome.latest.version)) {
                stringResource(R.string.up_to_date_newer, installed, outcome.latest.version)
            } else {
                stringResource(R.string.up_to_date_latest, installed)
            }
            page = outcome.latest.page
        }
        is UpdateCheck.Outcome.Failed -> {
            title = stringResource(R.string.check_failed_title)
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
            ) { Text(stringResource(if (update) R.string.whats_new else R.string.open_release_page)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(close).focusOutline(PillShape),
            ) { Text(stringResource(if (update) R.string.not_now else R.string.close)) }
        },
    )
    // With an update, the release page is what they came for.
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { (if (update) open else close).requestFocus() } }
}

/** Something setup checks, with a line on why it happened when that isn't obvious. */
private class Problem(@StringRes val text: Int, @StringRes val detail: Int?, val step: SetupStep)

@Composable
private fun NeedsAttention(state: SystemState, onFix: (SetupStep) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(R.string.attention_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val problems = buildList {
                if (!state.device.ok) {
                    add(Problem(R.string.problem_firmware, null, SetupStep.DEVICE))
                }
                if (state.wayfinderOn) {
                    add(Problem(R.string.problem_wayfinder, null, SetupStep.WAYFINDER))
                }
                if (state.autoLaunchBlocked) {
                    // Named first: it is the cause of the stuck service, not another problem.
                    add(Problem(R.string.problem_autolaunch, R.string.problem_autolaunch_detail, SetupStep.ACCESSIBILITY))
                } else if (state.serviceStuck) {
                    add(Problem(R.string.problem_stuck, R.string.problem_stuck_detail, SetupStep.ACCESSIBILITY))
                } else if (!state.serviceOn) {
                    // The first thing anyone sees after updating the app.
                    add(Problem(R.string.problem_off, R.string.problem_off_detail, SetupStep.ACCESSIBILITY))
                }
                if (state.shizuku != Shell.Status.READY) {
                    add(
                        Problem(
                            R.string.problem_shizuku,
                            when (state.shizuku) {
                                Shell.Status.NOT_INSTALLED -> R.string.shizuku_not_installed_detail
                                Shell.Status.NOT_RUNNING -> R.string.shizuku_not_running_detail
                                else -> R.string.shizuku_no_permission_detail
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
                            stringResource(problem.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        problem.detail?.let {
                            Text(
                                stringResource(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { onFix(problem.step) },
                        modifier = Modifier.focusOutline(PillShape),
                    ) { Text(stringResource(R.string.fix)) }
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

    fun focus(button: PhysicalButton, gesture: Gesture) = observe { store.focus(button, gesture) }

    fun shortcut(button: PhysicalButton, gesture: Gesture) = observe { store.shortcut(button, gesture) }

    fun combos() = observe { store.combos }

    fun combo(keys: Set<ComboKey>) = observe { store.combo(keys) }

    fun setCombo(keys: Set<ComboKey>, shortcut: Shortcut) {
        store.setCombo(keys, shortcut)
        changed()
    }

    fun removeCombo(keys: Set<ComboKey>) {
        store.removeCombo(keys)
        changed()
    }

    /** Another profile is in use, so every shortcut on the screen may have changed. */
    fun reloaded() = changed()

    fun set(button: PhysicalButton, gesture: Gesture, shortcut: Shortcut) {
        store.set(button, gesture, shortcut)
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
 * closed when the screen opens. Back, Home and the AYN button also list the
 * combos they are in, with a way to add one.
 */
@Composable
private fun ButtonCard(
    context: Context,
    button: PhysicalButton,
    shortcuts: ObservedShortcuts,
    startOpen: Boolean,
    onEditCombo: (Set<ComboKey>) -> Unit,
    onAddCombo: () -> Unit,
    onEdit: (Gesture) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(startOpen) }
    val words = remember(context) { context.words() }
    val values = button.gestures.associateWith { valueText(context, button, it, shortcuts) }
    val changed = button.gestures.filter { shortcuts.action(button, it) != ButtonAction.NORMAL }
    val key = ComboKey.of(button)
    val holdsCombos = key in ComboKey.ANCHORS
    val combos = if (holdsCombos) Combos.including(shortcuts.combos(), key) else emptyList()
    val summary = buildList {
        changed.forEach { add(words.text(R.string.map_line, words.text(it.text), values.getValue(it))) }
        if (combos.isNotEmpty()) add(pluralStringResource(R.plurals.combos_count, combos.size, combos.size))
    }
    CollapsibleCard(
        title = stringResource(button.text),
        summary = if (summary.isEmpty()) stringResource(R.string.works_as_usual) else summary.joinToString("  \u00b7  "),
        subtitle = if (button.kind == ButtonKind.GAMEPAD) stringResource(R.string.gamepad_subtitle) else null,
        expanded = open,
        onToggle = { open = !open },
        symbol = buttonSymbol(button),
        badge = if (holdsCombos) {
            { ComboBadge() }
        } else {
            null
        },
    ) {
        button.gestures.forEach { gesture ->
            ValueRow(stringResource(gesture.text), values.getValue(gesture)) { onEdit(gesture) }
        }
        if (holdsCombos) {
            ListHeading(
                stringResource(R.string.combos_heading),
                stringResource(R.string.combos_heading_detail, stringResource(button.text)),
            )
            combos.forEach { keys ->
                ValueRow(Combos.label(words, keys), shortcuts.combo(keys)?.let { shortcutText(context, it) } ?: "") {
                    onEditCombo(keys)
                }
            }
            NavRow(stringResource(R.string.add_combo), null, onClick = onAddCombo)
        }
    }
}

/** What a gesture does, in words: its action, and for some actions where or what. */
private fun valueText(context: Context, button: PhysicalButton, gesture: Gesture, shortcuts: ObservedShortcuts): String {
    val shortcut = shortcuts.shortcut(button, gesture)
    return if (shortcut.action == ButtonAction.NORMAL) {
        normalLabel(context.words(), button, gesture)
    } else {
        shortcutText(context, shortcut)
    }
}

/** What a shortcut does, in words: its action, and for some actions where or what. */
internal fun shortcutText(context: Context, shortcut: Shortcut): String {
    val words = context.words()
    return when (shortcut.action) {
        ButtonAction.LAUNCH_APP -> {
            val app = appLabel(context, shortcut.app)
            val second = shortcut.second
            if (second != null) {
                words.text(R.string.text_open_pair, app, appLabel(context, second))
            } else {
                words.text(R.string.map_open_one, app, words.text(shortcut.screen.short))
            }
        }
        ButtonAction.HOME ->
            shortcut.home?.let { words.text(R.string.map_home_to, words.text(it.short)) } ?: words.text(shortcut.action.text)
        ButtonAction.PROFILE -> when (shortcut.profile) {
            ProfileSwitch.CYCLE -> words.text(ProfileSwitch.CYCLE.text)
            ProfileSwitch.ASK -> words.text(R.string.map_profile_ask)
            ProfileSwitch.ENABLE -> words.text(R.string.map_profile_enable, Profiles.name(context, shortcut.profileId))
        }
        ButtonAction.CLOSE_ALL -> when (shortcut.close) {
            CloseTarget.SPECIFIC -> words.text(
                R.string.text_close_specific,
                RecentTasks.names(words, shortcut.closeApps.map { appLabel(context, it) }.sorted()),
            )
            else -> words.text(shortcut.close.text)
        }
        ButtonAction.FOCUS_MODE -> words.text(shortcut.focus.text)
        else -> words.text(shortcut.action.text)
    }
}

internal fun appLabel(context: Context, pkg: String?): String = pkg?.let {
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
    }.getOrNull()
} ?: context.getString(R.string.an_app)

@Composable
private fun AboutCard(onWhatsNew: () -> Unit) {
    var showLicenses by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.about_title)) {
        Text(
            stringResource(R.string.about_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValueRow(stringResource(R.string.whats_new_row), stringResource(R.string.show)) { onWhatsNew() }
        ValueRow(stringResource(R.string.licenses_row), stringResource(R.string.view)) { showLicenses = true }
    }
    if (showLicenses) LicensesDialog(onDismiss = { showLicenses = false })
}
