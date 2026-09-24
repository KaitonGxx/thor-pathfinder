package com.thorpathfinder.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.SystemState
import com.thorpathfinder.app.Watchdog
import com.thorpathfinder.app.watchdogMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Body text, matching the other pages. */
@Composable
private fun Para(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/**
 * The watchdog: off by default, and plain about what it does, because an app
 * that switches its own accessibility service back on is exactly what a bad
 * one would do. Everything it runs is in `watchdog.sh` in the repository.
 */
@Composable
fun WatchdogPage(state: SystemState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = state.shizuku == Shell.Status.READY
    var on by remember { mutableStateOf<Boolean?>(null) }
    var alive by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var every by remember { mutableStateOf(Watchdog.seconds(context)) }
    var choosing by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode

    suspend fun reload() {
        val (isOn, isAlive, lines) = withContext(Dispatchers.IO) {
            Triple(Watchdog.on(context), Watchdog.alive(), Watchdog.log())
        }
        on = isOn
        alive = isAlive
        log = lines
    }

    LaunchedEffect(ready) { if (ready) reload() else on = false }
    LaunchedEffect(inputMode, on) { runCatching { first.requestFocus() } }

    PageScaffold(
        "Watchdog",
        "Keeps Pathfinder's accessibility service switched on, even when something stops " +
            "Pathfinder itself.",
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = "Keep the service switched on",
                detail = when {
                    !ready -> "Needs Shizuku"
                    on == true && !alive -> "On, but not running — turn it off and on again"
                    on == true -> "On"
                    else -> "Off"
                },
                checked = on == true,
                enabled = ready && !busy,
                modifier = Modifier.focusRequester(first),
                onChange = { want ->
                    busy = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            if (want) Watchdog.start(context) else Watchdog.stop()
                        }
                        said = watchdogMessage(outcome)
                        reload()
                        busy = false
                    }
                },
            )
            ValueRow("How often it looks", "every ${every}s", enabled = ready && !busy) { choosing = true }
            said?.let { Para(it) }

            ListHeading("What it does")
            Para(
                "Shizuku starts a small script that belongs to Shizuku rather than to Pathfinder, " +
                    "so force-stopping or killing Pathfinder doesn't stop it. Every few seconds it " +
                    "checks whether Pathfinder is still in Android's accessibility list, and puts it " +
                    "back if it has gone. A check costs about 22 milliseconds, so even the quickest " +
                    "setting is under half a percent of one processor core.",
            )
            Para(
                "It only ever adds Pathfinder's own service. It never removes anyone else's, and it " +
                    "leaves the switch alone while Android's settings are open, so switching the " +
                    "service off yourself still works.",
            )
            Para(
                "It also takes Pathfinder, and nothing else, off the list in the Thor's APP Auto " +
                    "Launch Manage page. Despite the name, the apps switched on there can't have " +
                    "anything started in the background, so on that list Pathfinder's service never " +
                    "starts after a restart. It leaves that list alone while the Thor's settings are " +
                    "open.",
            )
            Para(
                "A restart stops it along with Shizuku, and it starts again by itself once Shizuku " +
                    "is running. It stops the moment you turn " +
                    "this off. The script is watchdog.sh in Pathfinder's source, and it is short.",
            )

            if (log.isNotEmpty()) {
                ListHeading("What it has done")
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    log.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (choosing) {
        ChoiceDialog(
            title = "How often it looks",
            options = Watchdog.CHOICES,
            selected = every,
            label = { "Every $it seconds" },
            detail = { if (it == Watchdog.DEFAULT_SECONDS) "Quick, and still barely any work" else null },
            onPick = { seconds ->
                choosing = false
                every = seconds
                busy = true
                scope.launch {
                    withContext(Dispatchers.IO) { Watchdog.setSeconds(context, seconds) }
                    reload()
                    busy = false
                }
            },
            onDismiss = { choosing = false },
        )
    }
}
