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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.Language
import com.thorpathfinder.app.MouseMode
import com.thorpathfinder.app.R
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.Shortcuts
import com.thorpathfinder.app.SystemState
import com.thorpathfinder.app.UpdateSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The pages behind the cog, each opened from the settings menu. */
private enum class SettingsPage(@StringRes val title: Int, @StringRes val detail: Int?) {
    PROFILES(R.string.page_profiles, R.string.page_profiles_detail),
    CLOSE_ALL(R.string.page_close, R.string.page_close_detail),
    MOUSE(R.string.page_mouse, R.string.page_mouse_detail),
    TIMING(R.string.page_timing, R.string.page_timing_detail),
    UPDATES(R.string.page_updates, R.string.page_updates_detail),
    WATCHDOG(R.string.wd_title, R.string.page_watchdog_detail),
    // Its detail is the language in use, in its own words.
    LANGUAGE(R.string.page_language, null),
    DIAGNOSTICS(R.string.page_diagnostics, R.string.page_diagnostics_detail),
    // Run setup again stays last, whatever else is added above it.
    SETUP(R.string.page_setup, R.string.page_setup_detail),
}

/**
 * The cog's menu: a page per group of settings. [openAt] opens Manage
 * profiles at one of its pages straight away, as the welcome page does.
 */
@Composable
fun MoreSettingsScreen(
    state: SystemState,
    onBack: () -> Unit,
    onRunSetup: () -> Unit,
    openAt: ManagePage? = null,
    openLanguage: Boolean = false,
) {
    var page by rememberSaveable {
        mutableStateOf(
            when {
                openLanguage -> SettingsPage.LANGUAGE
                openAt != null -> SettingsPage.PROFILES
                else -> null
            },
        )
    }
    // Where the menu puts focus when a page closes: the row it came from.
    var last by rememberSaveable { mutableStateOf(SettingsPage.entries.first()) }
    fun close() {
        last = page ?: last
        page = null
    }
    when (page) {
        null -> SettingsMenu(focusOn = last, onOpen = { page = it }, onBack = onBack)
        SettingsPage.PROFILES -> ManageProfilesPage(onBack = ::close, openAt = openAt)
        SettingsPage.CLOSE_ALL -> KeepRunningPage(onBack = ::close)
        SettingsPage.MOUSE -> MouseModePage(state, onBack = ::close)
        SettingsPage.TIMING -> TimingPage(onBack = ::close)
        SettingsPage.UPDATES -> UpdateSettingsPage(state, onBack = ::close)
        SettingsPage.WATCHDOG -> WatchdogPage(state, onBack = ::close)
        SettingsPage.LANGUAGE -> LanguagePage(onBack = ::close)
        SettingsPage.DIAGNOSTICS -> DiagnosticsPage(state, onBack = ::close)
        SettingsPage.SETUP -> RunSetupPage(onRunSetup = onRunSetup, onBack = ::close)
    }
}

@Composable
private fun SettingsMenu(focusOn: SettingsPage, onOpen: (SettingsPage) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val language = remember { Language.chosenName(context) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold(stringResource(R.string.settings), onBack = onBack) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsPage.entries.forEach { entry ->
                NavRow(
                    title = stringResource(entry.title),
                    detail = entry.detail?.let { stringResource(it) }
                        ?: language
                        ?: stringResource(R.string.language_system),
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
    val context = LocalContext.current
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
        stringResource(R.string.page_mouse),
        stringResource(R.string.mouse_subtitle),
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.mouse_reverse),
                detail = stringResource(
                    when {
                        !ready -> R.string.mouse_needs_shizuku
                        reversed == null -> R.string.mouse_unreadable
                        else -> R.string.mouse_reverse_detail
                    },
                ),
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
                            error = context.getString(R.string.mouse_couldnt_change)
                        }
                        busy = false
                    }
                },
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (needsRestart) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.mouse_restart),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { MouseMode.restart() } },
                        modifier = Modifier.focusOutline(PillShape),
                    ) { Text(stringResource(R.string.mouse_restart_now)) }
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
        stringResource(R.string.page_updates),
        stringResource(R.string.updates_subtitle),
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.updates_check_on_open),
                detail = stringResource(R.string.updates_check_on_open_detail),
                checked = checkOnOpen,
                modifier = Modifier.focusRequester(first),
                onChange = {
                    checkOnOpen = it
                    settings.checkOnOpen = it
                },
            )
            SwitchRow(
                title = stringResource(R.string.updates_auto),
                detail = stringResource(if (ready) R.string.updates_auto_detail else R.string.updates_auto_needs_shizuku),
                checked = autoInstall && ready,
                enabled = ready,
                onChange = {
                    autoInstall = it
                    settings.autoInstall = it
                },
            )
            val known = settings.known
            Text(
                (if (known == null) stringResource(R.string.updates_none_seen) else stringResource(R.string.updates_seen, known.version)) +
                    " " + stringResource(R.string.updates_same_key),
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

    PageScaffold(stringResource(R.string.setup_again_title), onBack = onBack) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.setup_again_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.setup_again_keeps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRunSetup, modifier = Modifier.focusOutline(PillShape)) {
                    Text(stringResource(R.string.setup_again_run))
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(notNow).focusOutline(PillShape),
                ) { Text(stringResource(R.string.not_now)) }
            }
        }
    }
}

private enum class Timing(@StringRes val title: Int, val choices: List<Long>) {
    HOLD(R.string.timing_hold, Shortcuts.HOLD_CHOICES),
    DOUBLE(R.string.timing_double, Shortcuts.DOUBLE_CHOICES);

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
        stringResource(R.string.page_timing),
        stringResource(R.string.timing_subtitle),
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ValueRow(
                stringResource(R.string.timing_hold),
                stringResource(R.string.timing_ms, shortcuts.holdMs),
                modifier = Modifier.focusRequester(first),
            ) { editing = Timing.HOLD }
            ValueRow(
                stringResource(R.string.timing_double),
                stringResource(R.string.timing_ms, shortcuts.doubleMs),
            ) { editing = Timing.DOUBLE }
            SwitchRow(stringResource(R.string.timing_vibrate), null, shortcuts.vibrate) { shortcuts.vibrate = it }
        }
    }

    editing?.let { timing ->
        ChoiceDialog(
            title = stringResource(timing.title),
            options = timing.choices,
            selected = timing.get(shortcuts),
            label = { context.getString(R.string.timing_ms, it) },
            onPick = {
                timing.set(shortcuts, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
