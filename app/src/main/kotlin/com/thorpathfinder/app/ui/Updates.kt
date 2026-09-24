package com.thorpathfinder.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.UpdateCheck
import com.thorpathfinder.app.UpdateSettings
import com.thorpathfinder.app.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What Pathfinder knows about updates while the settings are open: the button's
 * wording, the notice card, and the download the card can start.
 *
 * A check runs when the screen opens (unless it ran a few minutes ago), so the
 * button already says where things stand. Tapping it checks again and answers
 * in a dialog.
 */
@Stable
class UpdateUi(private val context: Context, private val scope: CoroutineScope) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(val release: UpdateCheck.Release) : State
        data class UpToDate(val release: UpdateCheck.Release) : State
        data class Failed(val message: String) : State
        data class Downloading(val percent: Int) : State
        data object Installing : State
        data class InstallFailed(val message: String) : State
    }

    private val settings = UpdateSettings(context)

    val installed: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    var state: State by mutableStateOf(remembered())
        private set

    /** The answer to a tap on the button, as a dialog. */
    var answer: UpdateCheck.Outcome? by mutableStateOf(null)

    private var dismissed by mutableStateOf<String?>(null)

    /** The update to shout about, or null when there's nothing to say. */
    val notice: UpdateCheck.Release?
        get() = when (val s = state) {
            is State.Available -> s.release.takeIf { it.version != dismissed && it.version != settings.hiddenVersion }
            is State.Downloading, State.Installing, is State.InstallFailed -> settings.known
            else -> null
        }

    /**
     * What the last check found, so the button says something at once. A
     * check runs whenever the screen comes to the front, so this is never
     * more than [QUIET_MS] old by the time anyone reads it.
     *
     * With checking on open turned off nothing keeps it fresh, and "Up to
     * date" could be long out of date, so the button offers a check instead.
     * A release newer than this one stays true however old the answer is.
     */
    private fun remembered(): State {
        val known = settings.known ?: return State.Idle
        return when {
            UpdateCheck.isNewer(known.version, installed) -> State.Available(known)
            !settings.checkOnOpen -> State.Idle
            else -> State.UpToDate(known)
        }
    }

    /** Called every time the settings screen comes to the front. */
    fun checkOnOpen() {
        if (!settings.checkOnOpen) return
        if (System.currentTimeMillis() - settings.lastCheckMs < QUIET_MS) return
        check(answerInDialog = false)
    }

    fun check(answerInDialog: Boolean) {
        if (state is State.Checking || state is State.Downloading || state is State.Installing) return
        state = State.Checking
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { UpdateCheck.check(installed) }
            settings.lastCheckMs = System.currentTimeMillis()
            state = when (outcome) {
                is UpdateCheck.Outcome.Available -> {
                    settings.known = outcome.latest
                    State.Available(outcome.latest)
                }
                is UpdateCheck.Outcome.UpToDate -> {
                    settings.known = outcome.latest
                    State.UpToDate(outcome.latest)
                }
                is UpdateCheck.Outcome.Failed -> State.Failed(outcome.message)
            }
            if (answerInDialog) answer = outcome
            val release = (state as? State.Available)?.release
            if (release != null && settings.autoInstall && Updater.canInstall(release)) install(release)
        }
    }

    /** Downloads and installs [release]; the install replaces this app. */
    fun install(release: UpdateCheck.Release) {
        if (state is State.Downloading || state is State.Installing) return
        state = State.Downloading(0)
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                Updater.install(context, release) { percent ->
                    scope.launch { if (state is State.Downloading) state = State.Downloading(percent) }
                }
            }
            state = when (outcome) {
                Updater.Outcome.Installing -> State.Installing
                is Updater.Outcome.Failed -> State.InstallFailed(outcome.message)
            }
        }
    }

    /** Hide the card until Pathfinder is opened again. */
    fun dismiss() {
        dismissed = (state as? State.Available)?.release?.version
    }

    /** Hide the card until a release newer than this one turns up. */
    fun dismissForever() {
        val version = (state as? State.Available)?.release?.version ?: return
        settings.hiddenVersion = version
        dismissed = version
    }

    /** What the button by the version badge says. */
    val buttonLabel: String
        get() = when (state) {
            State.Checking -> "Checking…"
            is State.Available -> "Update Available"
            is State.UpToDate -> "Up to date"
            else -> "Check For Updates"
        }

    private companion object {
        /** Long enough that flicking in and out doesn't ask GitHub each time. */
        const val QUIET_MS = 15 * 60 * 1000L
    }
}

/** The yellow notice: an update is out, with the ways to deal with it. */
@Composable
fun UpdateCard(ui: UpdateUi, release: UpdateCheck.Release, onOpenPage: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = WarningContainer,
            contentColor = OnWarningContainer,
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "Update available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Text(
                "Version ${release.version} is out. You have ${ui.installed}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            when (val state = ui.state) {
                is UpdateUi.State.Downloading -> {
                    Spacer(Modifier.height(8.dp))
                    Text("Downloading ${state.percent}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                UpdateUi.State.Installing -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Installing… Pathfinder closes for a moment while Android swaps it over.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is UpdateUi.State.InstallFailed -> {
                    Spacer(Modifier.height(8.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
                else -> Unit
            }
            val busy = ui.state is UpdateUi.State.Downloading || ui.state is UpdateUi.State.Installing
            if (!busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (Updater.canInstall(release)) {
                        TextButton(
                            onClick = { ui.install(release) },
                            modifier = Modifier.focusOutline(PillShape),
                        ) { Text("Update now") }
                        // The release page holds that version's notes. Without an
                        // install from here, the button below opens it anyway.
                        TextButton(
                            onClick = { onOpenPage(release.page) },
                            modifier = Modifier.focusOutline(PillShape),
                        ) { Text("What's New") }
                    } else {
                        TextButton(
                            onClick = { onOpenPage(release.page) },
                            modifier = Modifier.focusOutline(PillShape),
                        ) { Text("Open release page") }
                    }
                    TextButton(
                        onClick = { ui.dismiss() },
                        modifier = Modifier.focusOutline(PillShape),
                    ) { Text("Dismiss") }
                    TextButton(
                        onClick = { ui.dismissForever() },
                        modifier = Modifier.focusOutline(PillShape),
                    ) { Text("Don't show again") }
                }
            }
        }
    }
}
