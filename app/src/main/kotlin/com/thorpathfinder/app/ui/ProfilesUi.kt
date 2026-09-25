package com.thorpathfinder.app.ui

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.runtime.DisposableEffect
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
import com.thorpathfinder.app.R
import com.thorpathfinder.app.Shell
import com.thorpathfinder.app.Words
import com.thorpathfinder.app.words
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * The profiles as Compose sees them.
 *
 * Every screen that shows profiles builds its own through [rememberProfileUi],
 * which also follows switches made elsewhere while the screen is up: a
 * Profile switcher shortcut, or the question an "Ask" one puts up.
 * [onSwitched] lets the settings screen redraw its shortcut cards, since a
 * different profile means different shortcuts.
 */
@Stable
internal class ProfileUi(private val context: Context, private val onSwitched: () -> Unit = {}) {

    // Kept here, since Android only holds a preference listener weakly.
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    fun watch() = Profiles.watch(context, changed)

    fun unwatch() = Profiles.unwatch(context, changed)

    var profiles by mutableStateOf(Profiles.all(context))
        private set

    var activeId by mutableIntStateOf(Profiles.activeId(context))
        private set

    /** The app whose profile is in use, when an app's is. */
    var appInUse by mutableStateOf(Profiles.appInUse(context))
        private set

    /** Each profile's linked apps. */
    var links by mutableStateOf(allLinks())
        private set

    private fun allLinks(): Map<Int, Set<String>> =
        Profiles.ids(context).associateWith { Profiles.linkedApps(context, it) }.filterValues { it.isNotEmpty() }

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
        appInUse = Profiles.appInUse(context)
        links = allLinks()
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

    /** Links [apps] to profile [id], taking them from any other profile. */
    fun linkApps(id: Int, apps: Set<String>) {
        Profiles.setLinkedApps(context, id, apps)
        refresh()
    }

    /** Makes one profile the main one, which every "Enable" shortcut then returns to. */
    fun makeMain(id: Int) {
        Profiles.setMain(context, id)
        refresh()
    }
}

/** The profiles for one screen, kept up to date for as long as it is showing. */
@Composable
internal fun rememberProfileUi(onSwitched: () -> Unit = {}): ProfileUi {
    val context = LocalContext.current
    val ui = remember { ProfileUi(context, onSwitched) }
    DisposableEffect(ui) {
        ui.watch()
        onDispose { ui.unwatch() }
    }
    return ui
}

/** What a row on the Manage profiles page offers for one profile. */
private enum class ProfileRowAction { ENABLE, BACK_TO_MAIN, APPS, MAKE_MAIN, RENAME, DELETE }

private val CREATE = R.string.profile_create
private val NO_ROOM = R.string.profile_no_room
private val FRESH = R.string.profile_fresh

/** The oval under the title: the profile in use, and a tap to change it. */
@Composable
internal fun ProfileBar(ui: ProfileUi) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.active_profile),
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
                    contentDescription = stringResource(R.string.choose_profile),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        ui.appInUse?.let { app ->
            Text(
                stringResource(R.string.for_app, appLabel(context, app)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    if (picking) {
        PickDialog(
            title = stringResource(R.string.profiles_title),
            choices = ui.profiles.map { it.name to profileTag(context, it, ui) } +
                listOf(stringResource(CREATE) to stringResource(if (ui.full) NO_ROOM else FRESH)),
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
    val context = LocalContext.current
    val suggested = remember { Profiles.newName(ui.profiles.map { it.name }) { context.getString(R.string.profile_n, it) } }
    var name by rememberSaveable { mutableStateOf(suggested) }
    var keep by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(CREATE)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.create_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= Profiles.MAX_NAME) name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    title = stringResource(R.string.keep_list),
                    detail = stringResource(R.string.keep_list_detail, from),
                    checked = keep,
                    onChange = { keep = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ui.create(Profiles.cleanName(name, suggested), keep)
                onDone()
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Renames a profile, or names a new one. */
@Composable
private fun RenameProfileDialog(profile: Profiles.Profile, onRename: (String) -> Unit, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= Profiles.MAX_NAME) name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onRename(Profiles.cleanName(name, profile.name))
                onDone()
            }) { Text(stringResource(R.string.rename)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The pages inside Manage profiles. */
enum class ManagePage { PROFILES, BACKUP }

/**
 * The Manage profiles page: Profiles (the list itself, a page of its own),
 * how a switch is shown, and Backup & share. Back from an inner page lands
 * on the row that opened it.
 */
@Composable
internal fun ManageProfilesPage(onBack: () -> Unit, openAt: ManagePage? = null) {
    val ui = rememberProfileUi()
    var page by rememberSaveable { mutableStateOf(openAt) }
    var last by rememberSaveable { mutableStateOf(openAt ?: ManagePage.PROFILES) }
    fun close() {
        last = page ?: last
        page = null
    }
    when (page) {
        ManagePage.PROFILES -> {
            ProfileListPage(ui, onBack = ::close)
            return
        }
        ManagePage.BACKUP -> {
            BackupPage(onBack = ::close)
            return
        }
        null -> Unit
    }
    val rows = remember { ManagePage.entries.associateWith { FocusRequester() } }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { rows.getValue(last).requestFocus() } }

    PageScaffold(
        stringResource(R.string.page_profiles),
        stringResource(R.string.manage_subtitle),
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NavRow(
                stringResource(R.string.profiles_title),
                stringResource(
                    R.string.profiles_row_detail,
                    pluralStringResource(R.plurals.profiles_count, ui.profiles.size, ui.profiles.size),
                    ui.active.name,
                ),
                modifier = Modifier.focusRequester(rows.getValue(ManagePage.PROFILES)),
            ) { page = ManagePage.PROFILES }
            SwitchRow(
                title = stringResource(R.string.show_buttons),
                detail = stringResource(R.string.show_buttons_detail),
                checked = ui.showMap,
                onChange = { ui.showMap = it },
            )
            NavRow(
                stringResource(R.string.backup_row),
                stringResource(R.string.backup_row_detail),
                modifier = Modifier.focusRequester(rows.getValue(ManagePage.BACKUP)),
            ) { page = ManagePage.BACKUP }
        }
    }
}

/** Every profile, and a new one: tap one to use, rename, link apps to, or delete it. */
@Composable
private fun ProfileListPage(ui: ProfileUi, onBack: () -> Unit) {
    val context = LocalContext.current
    var acting by remember { mutableStateOf<Profiles.Profile?>(null) }
    var renaming by remember { mutableStateOf<Profiles.Profile?>(null) }
    var deleting by remember { mutableStateOf<Profiles.Profile?>(null) }
    var linking by remember { mutableStateOf<Profiles.Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    PageScaffold(
        stringResource(R.string.profiles_title),
        stringResource(R.string.profiles_subtitle),
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
                    detail = listOfNotNull(profileTag(context, profile, ui), appsTag(context, ui.links[profile.id]))
                        .joinToString(" \u00b7 ")
                        .ifEmpty { null },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { acting = profile },
                )
            }
            NavRow(stringResource(CREATE), stringResource(if (ui.full) NO_ROOM else FRESH)) { if (!ui.full) creating = true }
        }
    }

    acting?.let { profile ->
        val choices = profileActions(context.words(), profile, ui)
        PickDialog(
            title = profile.name,
            choices = choices.map { it.second },
            onPick = { index ->
                val action = choices[index].first
                acting = null
                when (action) {
                    ProfileRowAction.ENABLE -> ui.switchTo(profile.id)
                    ProfileRowAction.BACK_TO_MAIN -> ui.switchTo(ui.mainId)
                    ProfileRowAction.APPS -> linking = profile
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
            title = { Text(stringResource(R.string.delete_title, profile.name)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    ui.delete(profile.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    linking?.let { profile ->
        AppMultiPickerDialog(
            title = stringResource(R.string.apps_for, profile.name),
            initial = ui.links[profile.id].orEmpty(),
            includeHomes = true,
            allowNone = true,
            note = { pkg ->
                ui.links.entries.firstOrNull { (id, apps) -> id != profile.id && pkg in apps }
                    ?.let { (id, _) -> ui.profiles.firstOrNull { it.id == id }?.name }
                    ?.let { context.getString(R.string.uses_now, it) }
            },
            onDone = { apps ->
                ui.linkApps(profile.id, apps)
                linking = null
            },
            onDismiss = { linking = null },
        )
    }

    if (creating) CreateProfileDialog(ui) { creating = false }
}

/** A profile's linked apps, for its row: two by name, then how many more. */
private fun appsTag(context: Context, apps: Set<String>?): String? {
    if (apps.isNullOrEmpty()) return null
    val names = apps.map { appLabel(context, it) }.sortedBy { it.lowercase() }
    val shown = names.take(2).joinToString(context.getString(R.string.list_sep))
    val more = names.size - 2
    return if (more > 0) {
        context.resources.getQuantityString(R.plurals.apps_tag_more, more, shown, more)
    } else {
        context.getString(R.string.apps_tag, shown)
    }
}

/** How a profile is labelled wherever it is listed. */
private fun profileTag(context: Context, profile: Profiles.Profile, ui: ProfileUi): String? {
    val main = profile.id == ui.mainId
    val active = profile.id == ui.activeId
    val app = ui.appInUse?.takeIf { active }?.let { appLabel(context, it) }
    return when {
        app != null -> context.getString(if (main) R.string.tag_main_in_use_for else R.string.tag_in_use_for, app)
        main && active -> context.getString(R.string.tag_main_in_use)
        main -> context.getString(R.string.tag_main)
        active -> context.getString(R.string.tag_in_use)
        else -> null
    }
}

/** What one profile's row offers: the main profile is never deleted, and is its own way back. */
private fun profileActions(
    words: Words,
    profile: Profiles.Profile,
    ui: ProfileUi,
): List<Pair<ProfileRowAction, Pair<String, String?>>> = buildList {
    if (profile.id != ui.activeId) {
        add(
            ProfileRowAction.ENABLE to (
                words.text(R.string.map_profile_enable, profile.name) to words.text(R.string.action_enable_detail)
                ),
        )
    } else if (profile.id != ui.mainId) {
        add(
            ProfileRowAction.BACK_TO_MAIN to (
                words.text(R.string.action_back_to, ui.main.name) to words.text(R.string.action_back_to_detail)
                ),
        )
    }
    val linked = ui.links[profile.id].orEmpty()
    add(
        ProfileRowAction.APPS to (
            words.text(R.string.action_apps) to when {
                !Shell.ready -> words.text(R.string.needs_shizuku)
                linked.isEmpty() -> words.text(R.string.action_apps_none)
                else -> words.count(R.plurals.list_apps, linked.size, linked.size)
            }
            ),
    )
    if (profile.id != ui.mainId) {
        add(
            ProfileRowAction.MAKE_MAIN to (
                words.text(R.string.action_make_main) to words.text(R.string.action_make_main_detail)
                ),
        )
    }
    add(ProfileRowAction.RENAME to (words.text(R.string.rename) to null))
    if (ui.deletable(profile.id)) {
        add(ProfileRowAction.DELETE to (words.text(R.string.delete) to words.text(R.string.delete_detail)))
    }
}
