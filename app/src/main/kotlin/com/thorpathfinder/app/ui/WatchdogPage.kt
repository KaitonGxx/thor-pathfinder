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
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.R
import com.thorpathfinder.app.words
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
    var holding by remember { mutableStateOf(false) }
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
        holding = withContext(Dispatchers.IO) { Watchdog.holding() }
        on = isOn
        alive = isAlive
        log = lines
    }

    LaunchedEffect(ready) { if (ready) reload() else on = false }
    LaunchedEffect(inputMode, on) { runCatching { first.requestFocus() } }

    PageScaffold(
        stringResource(R.string.wd_title),
        stringResource(R.string.wd_subtitle),
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.wd_switch),
                detail = stringResource(
                    when {
                        !ready -> R.string.needs_shizuku
                        on == true && !alive -> R.string.wd_on_not_running
                        on == true && holding -> R.string.wd_holding
                        on == true -> R.string.on
                        else -> R.string.off
                    },
                ),
                checked = on == true,
                enabled = ready && !busy,
                modifier = Modifier.focusRequester(first),
                onChange = { want ->
                    busy = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            if (want) Watchdog.start(context) else Watchdog.stop()
                        }
                        said = watchdogMessage(context.words(), outcome)
                        reload()
                        busy = false
                    }
                },
            )
            ValueRow(
                stringResource(R.string.wd_how_often),
                stringResource(R.string.wd_every_short, every),
                enabled = ready && !busy,
            ) { choosing = true }
            said?.let { Para(it) }

            ListHeading(stringResource(R.string.wd_what_it_does))
            Para(stringResource(R.string.wd_p1))
            Para(stringResource(R.string.wd_p2))
            Para(stringResource(R.string.wd_p3))
            Para(stringResource(R.string.wd_p4))

            if (log.isNotEmpty()) {
                ListHeading(stringResource(R.string.wd_log))
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
        val words = context.words()
        ChoiceDialog(
            title = stringResource(R.string.wd_how_often),
            options = Watchdog.CHOICES,
            selected = every,
            label = { words.text(R.string.wd_every, it) },
            detail = { if (it == Watchdog.DEFAULT_SECONDS) words.text(R.string.wd_default_detail) else null },
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
