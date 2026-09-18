package com.thorpathfinder.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.SystemState

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
        SetupStep.ACCESSIBILITY -> state.serviceOn
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
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(vertical = 16.dp),
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
                    OutlinedButton(onClick = { index-- }, modifier = Modifier.focusOutline(PillShape)) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (step == SetupStep.DONE) onFinish() else index++ },
                    enabled = passed,
                    modifier = Modifier.focusRequester(next).focusOutline(PillShape),
                ) {
                    Text(
                        when (step) {
                            SetupStep.WELCOME -> "Get started"
                            SetupStep.DONE -> "Finish"
                            else -> "Next"
                        }
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
            "Step ${current + 1} of $count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** ✓ or • with a short status line. */
@Composable
private fun Check(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ok) "✓" else "•",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButton(text: String, focus: FocusRequester?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = (if (focus != null) Modifier.focusRequester(focus) else Modifier).focusOutline(PillShape),
    ) { Text(text) }
}

@Composable
private fun Welcome() {
    Title("Welcome to Thor Pathfinder")
    Body(
        "Pathfinder moves apps between the Thor's two screens and puts shortcuts on its buttons. " +
            "Out of the box it works like this:"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Bullet("Hold Back: swap the apps on the two screens")
        Bullet("Double-press Back: recent apps")
        Bullet("Double-press Select: mouse mode on or off")
    }
    Body("You can change all of these later. Setup takes about two minutes.")
}

@Composable
private fun Bullet(text: String) = Text("•  $text", style = MaterialTheme.typography.bodyLarge)

@Composable
private fun DeviceStep(device: Device.Support, context: Context, focus: FocusRequester) {
    Title("Check your Thor")
    when (device) {
        is Device.Support.Supported -> {
            Check(true, "AYN Thor, firmware ${device.firmware}")
            if (device.firmware != Device.MIN_FIRMWARE_TEXT) {
                Body(
                    "That's newer than the firmware Pathfinder was tested on (${Device.MIN_FIRMWARE_TEXT}). " +
                        "It should work; if something doesn't, please report it."
                )
            }
        }
        is Device.Support.NotAThor -> {
            Check(false, "This is a ${device.model}, not an AYN Thor")
            Body("Pathfinder is made for the Thor's two screens and its buttons, so it won't run here.")
        }
        is Device.Support.OldFirmware -> {
            Check(false, "Firmware ${device.firmware} is older than ${Device.MIN_FIRMWARE_TEXT}")
            UpdateAdvice(context, focus)
        }
        Device.Support.UnknownFirmware -> {
            Check(false, "Couldn't tell which firmware this Thor runs")
            UpdateAdvice(context, focus)
        }
    }
}

@Composable
private fun UpdateAdvice(context: Context, focus: FocusRequester) {
    Body(
        "Pathfinder relies on parts of AYN's software as they are in firmware ${Device.MIN_FIRMWARE_TEXT} " +
            "and newer. Update your Thor, then open Pathfinder again."
    )
    // Not in the public SDK, but AYN's updater (com.odin.fota) answers it.
    ActionButton("Open system update", focus) {
        runCatching {
            context.startActivity(
                Intent("android.settings.SYSTEM_UPDATE_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun WayfinderStep(state: SystemState, context: Context, focus: FocusRequester) {
    Title("Thor Wayfinder is installed")
    Body(
        "Pathfinder does everything Wayfinder does, and both use the Back button, so with both on " +
            "every hold of Back would swap the screens twice. Uninstall Wayfinder, or at least turn " +
            "off its accessibility service."
    )
    Check(
        !state.wayfinderOn,
        when {
            !state.wayfinderInstalled -> "Wayfinder is uninstalled"
            !state.wayfinderOn -> "Wayfinder's service is off"
            else -> "Wayfinder's service is still on"
        },
    )
    if (state.wayfinderOn) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Uninstall Wayfinder", focus) { context.openAppInfo(SystemState.WAYFINDER_PACKAGE) }
            OutlinedButton(
                onClick = { context.openAccessibilitySettings() },
                modifier = Modifier.focusOutline(PillShape),
            ) { Text("Turn it off instead") }
        }
    }
}

@Composable
private fun AccessibilityStep(state: SystemState, context: Context, focus: FocusRequester) {
    Title("Let Pathfinder see the buttons")
    Body(
        "Android only shares button presses with accessibility services, so Pathfinder has one. " +
            "It receives button presses and nothing else: it cannot read the screen or anything you type."
    )
    Check(state.serviceOn, if (state.serviceOn) "Pathfinder's service is on" else "Pathfinder's service is off")
    if (!state.serviceOn) {
        Body("In the list, open Thor Pathfinder and turn it on.")
        ActionButton("Open accessibility settings", focus) { context.openAccessibilitySettings() }
        Text(
            "If Android says “Restricted setting”: open Pathfinder's app info, tap ⋮ in the corner, " +
                "choose “Allow restricted settings”, then try again. Android asks this of apps " +
                "installed from outside the Play Store.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { context.openAppInfo(context.packageName) },
            modifier = Modifier.focusOutline(PillShape),
        ) { Text("Open Pathfinder's app info") }
    }
}

@Composable
private fun ShizukuStep(state: SystemState, context: Context, focus: FocusRequester, onRequest: () -> Unit) {
    Title("Connect Shizuku")
    Body(
        "Swapping screens and switching mouse mode need a little more access than an app gets on " +
            "its own. Shizuku grants it without rooting the Thor, and sets itself up."
    )
    when (state.shizuku) {
        Shell.Status.NOT_INSTALLED -> {
            Check(false, "Shizuku isn't installed")
            Body(
                "Install Shizuku from Google Play or from its GitHub releases, open it and follow " +
                    "its steps to start it, then come back here."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Get it on Google Play", focus) {
                    context.openUrl("https://play.google.com/store/apps/details?id=${Shell.SHIZUKU_PACKAGE}")
                }
                OutlinedButton(
                    onClick = { context.openUrl(SHIZUKU_RELEASES) },
                    modifier = Modifier.focusOutline(PillShape),
                ) { Text("Download from GitHub") }
            }
        }
        Shell.Status.NOT_RUNNING -> {
            Check(false, "Shizuku isn't running")
            Body("Open Shizuku and start it (it walks you through it), then come back here.")
            ActionButton("Open Shizuku", focus) {
                context.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let(context::startActivity)
            }
        }
        Shell.Status.NO_PERMISSION -> {
            Check(false, "Shizuku is running, but Pathfinder isn't allowed yet")
            ActionButton("Allow Pathfinder", focus, onRequest)
        }
        Shell.Status.READY -> Check(true, "Shizuku is connected")
    }
}

@Composable
private fun Done() {
    Title("You're all set")
    Body("Hold Back to swap screens, double-press Back for recent apps, and double-press Select for mouse mode.")
    Body(
        "Next you'll see Pathfinder's settings, where you can put other actions on Back, Home, " +
            "the AYN button, Select, Start, L3 and R3, and choose which way the right stick scrolls in mouse mode."
    )
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
