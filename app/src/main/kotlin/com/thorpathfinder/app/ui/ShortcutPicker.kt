package com.thorpathfinder.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.thorpathfinder.app.ButtonAction
import com.thorpathfinder.app.CloseTarget
import com.thorpathfinder.app.FocusSwitch
import com.thorpathfinder.app.HomeTarget
import com.thorpathfinder.app.LaunchScreen
import com.thorpathfinder.app.ProfileSwitch
import com.thorpathfinder.app.Profiles
import com.thorpathfinder.app.Shortcut

/**
 * How the shortcut list is laid out, wide or as a list: the user's pick, kept
 * for the whole device rather than per profile, since it is about the screen.
 * The settings screen and the Shortcut menu share it.
 */
private object ShortcutListLayout {
    private const val PREFS = "ui"
    private const val KEY = "shortcutListColumns"

    fun columns(context: Context): Int = ChoiceLayout.of(prefs(context).getInt(KEY, ChoiceLayout.WIDE))

    fun set(context: Context, columns: Int) {
        prefs(context).edit { putInt(KEY, columns) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Where choosing a shortcut has got to. "Open an app", "Home" and the others
 * with an arrow ask further questions after the action; nothing is handed
 * back until the last one, so Cancel at any step changes nothing.
 */
private sealed interface Step {
    data object Action : Step

    /** One app, or one on each screen? */
    data object HowMany : Step
    data object OneApp : Step
    data class OneScreen(val pkg: String) : Step
    data object TopApp : Step
    data class BottomApp(val top: String) : Step

    /** Which screens a Home shortcut sends home. */
    data object HomeWhere : Step

    /** Which apps a Close app(s) shortcut closes. */
    data object CloseWhich : Step

    /** The apps a "Close specific apps" shortcut closes. */
    data object CloseApps : Step

    /** Which profile a Profile switcher shortcut turns on, or whether it cycles or asks. */
    data object ProfileWhich : Step

    /** Which way a Focus Mode shortcut moves focus. */
    data object FocusWhich : Step
}

/** What choosing [action] asks next; null when choosing it is enough. */
private fun nextStep(action: ButtonAction): Step? = when (action) {
    ButtonAction.LAUNCH_APP -> Step.HowMany
    ButtonAction.HOME -> Step.HomeWhere
    ButtonAction.CLOSE_ALL -> Step.CloseWhich
    ButtonAction.PROFILE -> Step.ProfileWhich
    ButtonAction.FOCUS_MODE -> Step.FocusWhich
    else -> null
}

/**
 * Choosing a shortcut: the list of [choices], then whatever that action needs
 * to know, handed back whole through [onChosen]. The settings screen saves it
 * to a button's gesture; the Shortcut menu runs it once. [current] is what is
 * there now, for the lists to start on, or null for nothing.
 */
@Composable
internal fun ShortcutPicker(
    title: String,
    choices: List<ButtonAction>,
    current: Shortcut?,
    shizukuReady: Boolean,
    label: (ButtonAction) -> String = { it.label },
    onChosen: (Shortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf<Step>(Step.Action) }
    when (val at = step) {
        Step.Action -> {
            var columns by remember { mutableIntStateOf(ShortcutListLayout.columns(context)) }
            ChoiceDialog(
                title = title,
                options = choices,
                selected = current?.action,
                label = label,
                detail = { if (it.needsShizuku && !shizukuReady) "Needs Shizuku" else null },
                leadsOn = { nextStep(it) != null },
                columns = columns,
                onColumnsChange = {
                    columns = it
                    ShortcutListLayout.set(context, it)
                },
                onPick = { action ->
                    val next = nextStep(action)
                    if (next != null) step = next else onChosen(Shortcut(action))
                },
                onDismiss = onDismiss,
            )
        }
        Step.HowMany -> PickDialog(
            title = "Open an app",
            choices = listOf(
                "Open 1 App" to "On the top screen, the bottom one, or ask each time",
                "Open 2 Apps" to "One on each screen, both at once",
            ),
            onPick = { choice -> step = if (choice == 0) Step.OneApp else Step.TopApp },
            onDismiss = onDismiss,
        )
        Step.OneApp -> AppPickerDialog(
            onPick = { pkg -> step = Step.OneScreen(pkg) },
            onDismiss = onDismiss,
        )
        is Step.OneScreen -> ChoiceDialog(
            title = "Open ${appLabel(context, at.pkg)} on",
            options = LaunchScreen.entries,
            selected = current?.screen ?: LaunchScreen.TOP,
            label = { it.label },
            detail = { if (it == LaunchScreen.ASK) "Choose top or bottom each time the shortcut runs" else null },
            onPick = { screen -> onChosen(Shortcut(ButtonAction.LAUNCH_APP, app = at.pkg, screen = screen)) },
            onDismiss = onDismiss,
        )
        Step.TopApp -> AppPickerDialog(
            title = "App for the top screen",
            onPick = { pkg -> step = Step.BottomApp(pkg) },
            onDismiss = onDismiss,
        )
        is Step.BottomApp -> AppPickerDialog(
            title = "App for the bottom screen",
            exclude = at.top,
            onPick = { pkg ->
                onChosen(Shortcut(ButtonAction.LAUNCH_APP, app = at.top, screen = LaunchScreen.TOP, second = pkg))
            },
            onDismiss = onDismiss,
        )
        Step.CloseWhich -> PickDialog(
            title = ButtonAction.CLOSE_ALL.label,
            choices = CloseTarget.entries.map { it.label to null },
            onPick = { choice ->
                val target = CloseTarget.entries[choice]
                if (target == CloseTarget.SPECIFIC) {
                    step = Step.CloseApps
                } else {
                    onChosen(Shortcut(ButtonAction.CLOSE_ALL, close = target))
                }
            },
            onDismiss = onDismiss,
        )
        Step.CloseApps -> AppMultiPickerDialog(
            title = "Apps to close",
            initial = current?.closeApps ?: emptySet(),
            onDone = { apps ->
                onChosen(Shortcut(ButtonAction.CLOSE_ALL, close = CloseTarget.SPECIFIC, closeApps = apps))
            },
            onDismiss = onDismiss,
        )
        Step.ProfileWhich -> {
            // Cycle and Ask, then one row per profile, so the whole choice is one dialog.
            val profiles = remember { Profiles.all(context) }
            PickDialog(
                title = ButtonAction.PROFILE.label,
                choices = listOf(
                    ProfileSwitch.CYCLE.label to "Move to the next profile each press",
                    ProfileSwitch.ASK.label to "Choose from a list when the shortcut runs",
                ) + profiles.map { "Enable ${it.name}" to "Press again to go back to the main profile" },
                onPick = { choice ->
                    onChosen(
                        when (choice) {
                            0 -> Shortcut(ButtonAction.PROFILE, profile = ProfileSwitch.CYCLE)
                            1 -> Shortcut(ButtonAction.PROFILE, profile = ProfileSwitch.ASK)
                            else -> Shortcut(
                                ButtonAction.PROFILE,
                                profile = ProfileSwitch.ENABLE,
                                profileId = profiles[choice - 2].id,
                            )
                        },
                    )
                },
                onDismiss = onDismiss,
            )
        }
        Step.HomeWhere -> ChoiceDialog(
            title = "Send home on",
            options = HomeTarget.entries,
            selected = current?.home,
            label = { it.label },
            onPick = { target -> onChosen(Shortcut(ButtonAction.HOME, home = target)) },
            onDismiss = onDismiss,
        )
        Step.FocusWhich -> ChoiceDialog(
            title = ButtonAction.FOCUS_MODE.label,
            options = FocusSwitch.entries,
            // Only a gesture already on Focus Mode has a choice to start from.
            selected = current?.focus?.takeIf { current.action == ButtonAction.FOCUS_MODE },
            label = { it.label },
            detail = {
                when (it) {
                    FocusSwitch.TOP -> "Lock the controller to the top screen"
                    FocusSwitch.BOTTOM -> "Lock the controller to the bottom screen"
                    FocusSwitch.CYCLE -> "Auto-lock, Top screen, Bottom screen, one each press"
                    FocusSwitch.SWAP -> "Top to bottom and back; from Auto-lock, the top first"
                }
            },
            onPick = { switch -> onChosen(Shortcut(ButtonAction.FOCUS_MODE, focus = switch)) },
            onDismiss = onDismiss,
        )
    }
}
