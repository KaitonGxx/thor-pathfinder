package com.thorpathfinder.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.R
import com.thorpathfinder.app.words
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.Device
import com.thorpathfinder.app.ServiceSwitch
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.SystemState
import com.thorpathfinder.app.serviceSwitchMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SetupStep { WELCOME, DEVICE, WAYFINDER, ACCESSIBILITY, SHIZUKU, DONE }

private const val SHIZUKU_RELEASES = "https://github.com/RikkaApps/Shizuku/releases"

/**
 * First-run setup, one requirement per screen. Each step checks its
 * requirement live (the activity re-reads [state] whenever it resumes or
 * Shizuku changes), and Next unlocks once it passes.
 */
@Composable
fun SetupWizard(
    state: SystemState,
    startAt: SetupStep,
    onRequestShizuku: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    // Fixed when setup opens, so steps don't vanish mid-way (e.g. once Wayfinder is uninstalled).
    val steps = remember {
        SetupStep.entries.filter { it != SetupStep.WAYFINDER || state.wayfinderInstalled || startAt == it }
    }
    var index by rememberSaveable { mutableIntStateOf(steps.indexOf(startAt).coerceAtLeast(0)) }
    val step = steps[index]
    val passed = when (step) {
        SetupStep.WELCOME, SetupStep.DONE -> true
        SetupStep.DEVICE -> state.device.ok
        SetupStep.WAYFINDER -> !state.wayfinderOn
        // On AYN's auto launch list it may run now, but not after a restart.
        SetupStep.ACCESSIBILITY -> state.serviceOn && !state.autoLaunchBlocked
        SetupStep.SHIZUKU -> state.shizuku == Shell.Status.READY
    }

    BackHandler(enabled = index > 0) { index-- }

    val next = remember { FocusRequester() }
    val action = remember { FocusRequester() }
    // Controller users land on whatever they need to press next. Buttons only
    // take focus once the D-pad is in use, so try again when it starts.
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(step, passed, inputMode) {
        runCatching { if (passed) next.requestFocus() else action.requestFocus() }
    }

    // Each page gets its own scroll position, so every page opens at the top.
    val scroll = remember(step) { ScrollState(0) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            StepDots(count = steps.size, current = index)
            ScrollingColumn(
                state = scroll,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    SetupStep.WELCOME -> Welcome()
                    SetupStep.DEVICE -> DeviceStep(state.device, context, action)
                    SetupStep.WAYFINDER -> WayfinderStep(state, context, action)
                    SetupStep.ACCESSIBILITY -> AccessibilityStep(state, context, action)
                    SetupStep.SHIZUKU -> ShizukuStep(state, context, action, onRequestShizuku)
                    SetupStep.DONE -> Done()
                }
            }
            // Outside the scrolling area, so focusing Next never scrolls the page.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index > 0) {
                    OutlinedButton(onClick = { index-- }, modifier = Modifier.focusOutline(PillShape)) {
                        Text(stringResource(R.string.nav_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (step == SetupStep.DONE) onFinish() else index++ },
                    enabled = passed,
                    modifier = Modifier.focusRequester(next).focusOutline(PillShape),
                ) {
                    Text(
                        stringResource(
                            when (step) {
                                SetupStep.WELCOME -> R.string.setup_get_started
                                SetupStep.DONE -> R.string.setup_finish
                                else -> R.string.next
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 10.dp else 8.dp)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
        Text(
            stringResource(R.string.setup_step, current + 1, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Title(@StringRes text: Int, vararg args: Any) =
    Text(stringResource(text, *args), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(@StringRes text: Int, vararg args: Any) =
    Text(
        stringResource(text, *args),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/** ✓ or • with a short status line. */
@Composable
private fun Check(ok: Boolean, @StringRes text: Int, vararg args: Any) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "•",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(stringResource(text, *args), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButton(@StringRes text: Int, focus: FocusRequester?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = (if (focus != null) Modifier.focusRequester(focus) else Modifier).focusOutline(PillShape),
    ) { Text(stringResource(text)) }
}

@Composable
private fun Welcome() {
    Title(R.string.welcome_title)
    Body(R.string.welcome_body)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Bullet(R.string.welcome_b1)
        Bullet(R.string.welcome_b2)
        Bullet(R.string.welcome_b3)
    }
    Body(R.string.welcome_later)
}

@Composable
private fun Bullet(@StringRes text: Int) = Text("•  " + stringResource(text), style = MaterialTheme.typography.bodyLarge)

@Composable
private fun DeviceStep(device: Device.Support, context: Context, focus: FocusRequester) {
    Title(R.string.device_title)
    when (device) {
        is Device.Support.Supported -> {
            Check(true, R.string.device_ok, device.model, device.firmware)
            if (!Device.tested(device.model)) {
                Body(R.string.device_untested, device.model)
            } else if (device.model == Device.THOR && device.firmware != Device.MIN_FIRMWARE_TEXT) {
                Body(R.string.device_newer, Device.MIN_FIRMWARE_TEXT)
            }
        }
        is Device.Support.NotAThor -> {
            Check(false, R.string.device_not_thor, device.model)
            Body(R.string.device_not_thor_body)
        }
        is Device.Support.OldFirmware -> {
            Check(false, R.string.device_old, device.firmware, Device.MIN_FIRMWARE_TEXT)
            UpdateAdvice(context, focus)
        }
        Device.Support.UnknownFirmware -> {
            Check(false, R.string.device_unknown)
            UpdateAdvice(context, focus)
        }
    }
}

@Composable
private fun UpdateAdvice(context: Context, focus: FocusRequester) {
    Body(R.string.device_update_body, Device.MIN_FIRMWARE_TEXT)
    // Not in the public SDK, but AYN's updater (com.odin.fota) answers it.
    ActionButton(R.string.device_open_update, focus) {
        runCatching {
            context.startActivity(
                Intent("android.settings.SYSTEM_UPDATE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun WayfinderStep(state: SystemState, context: Context, focus: FocusRequester) {
    Title(R.string.wayfinder_title)
    Body(R.string.wayfinder_body)
    Check(
        !state.wayfinderOn,
        when {
            !state.wayfinderInstalled -> R.string.wayfinder_uninstalled
            !state.wayfinderOn -> R.string.wayfinder_off
            else -> R.string.wayfinder_on
        },
    )
    if (state.wayfinderOn) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(R.string.wayfinder_uninstall, focus) { context.openAppInfo(SystemState.WAYFINDER_PACKAGE) }
            OutlinedButton(
                onClick = { context.openAccessibilitySettings() },
                modifier = Modifier.focusOutline(PillShape),
            ) { Text(stringResource(R.string.wayfinder_turn_off)) }
        }
    }
}

@Composable
private fun AccessibilityStep(state: SystemState, context: Context, focus: FocusRequester) {
    Title(R.string.a11y_title)
    Body(R.string.a11y_body)
    Check(
        state.serviceOn && !state.autoLaunchBlocked,
        when {
            state.autoLaunchBlocked -> R.string.problem_autolaunch
            state.serviceOn -> R.string.a11y_on
            state.serviceStuck -> R.string.a11y_stuck
            else -> R.string.a11y_off
        },
    )
    if (!state.serviceOn || state.autoLaunchBlocked) {
        if (state.autoLaunchBlocked) {
            Body(if (state.shizuku == Shell.Status.READY) R.string.a11y_blocked_body_fix else R.string.a11y_blocked_body)
        } else if (state.serviceStuck) {
            Body(R.string.a11y_stuck_body)
        } else {
            Body(R.string.a11y_off_body)
        }
        ActionButton(R.string.a11y_open, focus) { context.openAccessibilitySettings() }
        // Shizuku is the shell user, which is the only one allowed to write this
        // setting, so with it connected the off-and-on can happen right here.
        if (state.shizuku == Shell.Status.READY) {
            var busy by remember { mutableStateOf(false) }
            var said by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { ServiceSwitch.turnOn(context) }
                        said = serviceSwitchMessage(context.words(), outcome)
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.focusOutline(PillShape),
            ) {
                Text(
                    stringResource(
                        when {
                            state.autoLaunchBlocked -> R.string.a11y_fix_it
                            state.serviceStuck -> R.string.a11y_off_on
                            else -> R.string.a11y_turn_on
                        },
                    ),
                )
            }
            said?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            stringResource(R.string.a11y_restricted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { context.openAppInfo(context.packageName) },
            modifier = Modifier.focusOutline(PillShape),
        ) { Text(stringResource(R.string.a11y_app_info)) }
    }
}

@Composable
private fun ShizukuStep(state: SystemState, context: Context, focus: FocusRequester, onRequest: () -> Unit) {
    Title(R.string.shizuku_title)
    Body(R.string.shizuku_body)
    when (state.shizuku) {
        Shell.Status.NOT_INSTALLED -> {
            Check(false, R.string.shizuku_not_installed)
            Body(R.string.shizuku_install_body)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(R.string.shizuku_play, focus) {
                    context.openUrl("https://play.google.com/store/apps/details?id=${Shell.SHIZUKU_PACKAGE}")
                }
                OutlinedButton(
                    onClick = { context.openUrl(SHIZUKU_RELEASES) },
                    modifier = Modifier.focusOutline(PillShape),
                ) { Text(stringResource(R.string.shizuku_github)) }
            }
        }
        Shell.Status.NOT_RUNNING -> {
            Check(false, R.string.shizuku_not_running)
            Body(R.string.shizuku_start_body)
            ActionButton(R.string.shizuku_open, focus) {
                context.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let(context::startActivity)
            }
        }
        Shell.Status.NO_PERMISSION -> {
            Check(false, R.string.shizuku_no_permission)
            ActionButton(R.string.shizuku_allow, focus, onRequest)
        }
        Shell.Status.READY -> Check(true, R.string.shizuku_connected)
    }
}

@Composable
private fun Done() {
    Title(R.string.done_title)
    Body(R.string.done_body1)
    Body(R.string.done_body2)
    Spacer(Modifier.height(4.dp))
}

fun Context.openAccessibilitySettings() =
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

fun Context.openAppInfo(pkg: String) = startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

fun Context.openUrl(url: String) =
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
