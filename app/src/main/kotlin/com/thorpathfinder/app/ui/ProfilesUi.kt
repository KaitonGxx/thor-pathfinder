package com.thorpathfinder.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.PathfinderService
import com.thorpathfinder.app.Profiles

/**
 * The profiles as Compose sees them.
 *
 * Every screen that shows profiles builds its own, and each one is thrown
 * away when its screen leaves, so coming back always reads what is stored.
 * [onSwitched] lets the settings screen redraw its shortcut cards, since a
 * different profile means different shortcuts.
 */
@Stable
internal class ProfileUi(private val context: Context, private val onSwitched: () -> Unit = {}) {

    var profiles by mutableStateOf(Profiles.all(context))
        private set

    var activeId by mutableIntStateOf(Profiles.activeId(context))
        private set

    private var showMapState by mutableStateOf(Profiles.showMap(context))

    /** The profile an "Enable" shortcut returns to; the user says which. */
    var mainId by mutableIntStateOf(Profiles.mainId(context))
        private set

    val active: Profiles.Profile
        get() = profiles.firstOrNull { it.id == activeId } ?: main

    val main: Profiles.Profile
        get() = profiles.firstOrNull { it.id == mainId }
            ?: profiles.firstOrNull()
            ?: Profiles.Profile(Profiles.ORIGINAL, Profiles.DEFAULT_MAIN_NAME)

    val full: Boolean get() = profiles.size >= Profiles.LIMIT

    fun deletable(id: Int): Boolean = id != mainId && profiles.size > 1

    private fun refresh() {
        profiles = Profiles.all(context)
        activeId = Profiles.activeId(context)
        mainId = Profiles.mainId(context)
        onSwitched()
    }

    fun switchTo(id: Int) {
        Profiles.switchTo(context, id)
        refresh()
        PathfinderService.profileSwitched(context)
    }

    /** Turns one on, or goes back to the main profile when it already is. */
    fun enable(id: Int) {
        Profiles.enable(context, id)
        refresh()
        PathfinderService.profileSwitched(context)
    }

    /** Adds a profile and moves to it, so its defaults are what the user sees. */
    fun create(name: String, keepRunning: Boolean) {
        Profiles.create(context, name, keepRunning)?.let { Profiles.switchTo(context, it.id) }
        refresh()
    }

    var showMap: Boolean
        get() = showMapState
        set(value) {
            Profiles.setShowMap(context, value)
            showMapState = value
        }

    fun rename(id: Int, name: String) {
        Profiles.rename(context, id, name)
        refresh()
    }

    fun delete(id: Int) {
        Profiles.delete(context, id)
        refresh()
    }

    /** Makes one profile the main one, which every "Enable" shortcut then returns to. */
    fun makeMain(id: Int) {
        Profiles.setMain(context, id)
        refresh()
    }
}

/** What a row on the Manage profiles page offers for one profile. */
private enum class ProfileRowAction { ENABLE, BACK_TO_MAIN, MAKE_MAIN, RENAME, DELETE }

private const val CREATE = "Create profile"
private const val NO_ROOM = "No room for another one"
private const val FRESH = "A set of shortcuts of its own, all at their defaults"

/** The oval under the title: the profile in use, and a tap to change it. */
@Composable
internal fun ProfileBar(ui: ProfileUi) {
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Profiles",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, end = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = PillShape,
            modifier = Modifier
                .focusOutline(PillShape)
                .clip(PillShape)
                .clickable { picking = true },
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ui.active.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose a profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }

    if (picking) {
        PickDialog(
            title = "Profiles",
            choices = ui.profiles.map { it.name to profileTag(it, ui) } +
                listOf(CREATE to if (ui.full) NO_ROOM else FRESH),
            onPick = { index ->
                picking = false
                when {
                    index < ui.profiles.size -> ui.switchTo(ui.profiles[index].id)
                    !ui.full -> creating = true
                }
            },
            onDismiss = { picking = false },
        )
    }
    if (creating) CreateProfileDialog(ui) { creating = false }
}

/**
 * Names the new profile and asks what to carry over. Only the keep-running
 * list is offered: mouse mode is AYN's own setting, shared by the device, so
 * a profile has nothing of it to copy.
 */
@Composable
private fun CreateProfileDialog(ui: ProfileUi, onDone: () -> Unit) {
    val from = ui.active.name
    val suggested = remember { Profiles.newName(ui.profiles.map { it.name }) }
    var name by rememberSaveable { mutableStateOf(suggested) }
    var keep by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(CREATE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Every shortcut starts at its default. Mouse mode stays as it is: that one is " +
                        "the Thor's own setting, shared by every profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= Profiles.MAX_NAME) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    title = "Keep the keep-running list",
                    detail = "Copy the apps $from never force-stops",
                    checked = keep,
                    onChange = { keep = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ui.create(Profiles.cleanName(name, suggested), keep)
                onDone()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** Renames a profile, or names a new one. */
@Composable
private fun RenameProfileDialog(profile: Profiles.Profile, onRename: (String) -> Unit, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Rename profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= Profiles.MAX_NAME) name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(Profiles.cleanName(name, profile.name))
                onDone()
            }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/** The Manage profiles page: enable, rename, add or remove. */
@Composable
internal fun ManageProfilesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val ui = remember { ProfileUi(context) }
    var acting by remember { mutableStateOf<Profiles.Profile?>(null) }
    var renaming by remember { mutableStateOf<Profiles.Profile?>(null) }
    var deleting by remember { mutableStateOf<Profiles.Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold(
        "Manage profiles",
        "A profile holds every shortcut, the keep-running list, the timings and vibration. " +
            "The main profile is the one an Enable shortcut goes back to, and the one that stays. " +
            "Mouse mode is the Thor's own setting and is shared by all of them.",
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ui.profiles.forEachIndexed { index, profile ->
                NavRow(
                    title = profile.name,
                    detail = profileTag(profile, ui),
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { acting = profile },
                )
            }
            NavRow(CREATE, if (ui.full) NO_ROOM else FRESH) { if (!ui.full) creating = true }
            SwitchRow(
                title = "Show the buttons on switching",
                detail = "Draw the Thor with this profile's shortcuts for a few seconds",
                checked = ui.showMap,
                onChange = { ui.showMap = it },
            )
        }
    }

    acting?.let { profile ->
        val choices = profileActions(profile, ui)
        PickDialog(
            title = profile.name,
            choices = choices.map { it.second },
            onPick = { index ->
                val action = choices[index].first
                acting = null
                when (action) {
                    ProfileRowAction.ENABLE -> ui.switchTo(profile.id)
                    ProfileRowAction.BACK_TO_MAIN -> ui.switchTo(ui.mainId)
                    ProfileRowAction.MAKE_MAIN -> ui.makeMain(profile.id)
                    ProfileRowAction.RENAME -> renaming = profile
                    ProfileRowAction.DELETE -> deleting = profile
                }
            },
            onDismiss = { acting = null },
        )
    }

    renaming?.let { profile ->
        RenameProfileDialog(profile, onRename = { ui.rename(profile.id, it) }) { renaming = null }
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.name}?") },
            text = {
                Text(
                    "Its shortcuts go with it, and this can't be undone. Any shortcut set to enable " +
                        "it cycles through the profiles instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ui.delete(profile.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    if (creating) CreateProfileDialog(ui) { creating = false }
}

/** How a profile is labelled wherever it is listed. */
private fun profileTag(profile: Profiles.Profile, ui: ProfileUi): String? {
    val main = profile.id == ui.mainId
    val active = profile.id == ui.activeId
    return when {
        main && active -> "Main profile, in use"
        main -> "Main profile"
        active -> "In use"
        else -> null
    }
}

/** What one profile's row offers: the main profile is never deleted, and is its own way back. */
private fun profileActions(
    profile: Profiles.Profile,
    ui: ProfileUi,
): List<Pair<ProfileRowAction, Pair<String, String?>>> = buildList {
    if (profile.id != ui.activeId) {
        add(ProfileRowAction.ENABLE to ("Enable ${profile.name}" to "Use its shortcuts from now on"))
    } else if (profile.id != ui.mainId) {
        add(ProfileRowAction.BACK_TO_MAIN to ("Back to ${ui.main.name}" to "This one is already in use"))
    }
    if (profile.id != ui.mainId) {
        add(
            ProfileRowAction.MAKE_MAIN to (
                "Make this the main profile" to "Where an Enable shortcut goes back to"
                ),
        )
    }
    add(ProfileRowAction.RENAME to ("Rename" to null))
    if (ui.deletable(profile.id)) {
        add(ProfileRowAction.DELETE to ("Delete" to "Its shortcuts go with it"))
    }
}
