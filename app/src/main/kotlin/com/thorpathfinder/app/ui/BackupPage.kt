package com.thorpathfinder.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.thorpathfinder.app.Backup
import com.thorpathfinder.app.Profiles
import com.thorpathfinder.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * The cog's Backup & share page: save everything to a file and bring it
 * back, or pass one profile to someone else. Files come and go through
 * Android's own file picker. Nothing changes until the dialog that says what
 * a file holds is confirmed.
 */
@Composable
internal fun BackupPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ui = rememberProfileUi()
    val version = remember { versionName(context) }
    var status by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<Backup.Opened?>(null) }
    var choosingShare by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<Profiles.Profile?>(null) }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }

    fun save(uri: Uri?, what: String, text: () -> String) {
        uri ?: return
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = text().toByteArray()
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                    context.getString(R.string.saved, what)
                }.getOrElse { context.getString(R.string.save_failed, it.message.orEmpty()) }
            }
        }
    }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        save(uri, context.getString(R.string.backup_what)) { Backup.backup(context, version, created()) }
    }
    val saveProfile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON)) { uri ->
        val profile = sharing
        sharing = null
        if (profile != null) {
            save(uri, context.getString(R.string.shared_what, profile.name)) {
                Backup.share(context, profile.id, version, created())
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            opened = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readNBytes(Backup.MAX_BYTES + 1) }
                    if (bytes.size > Backup.MAX_BYTES) {
                        Backup.Opened.Unreadable(R.string.too_big)
                    } else {
                        Backup.open(String(bytes))
                    }
                }.getOrElse { Backup.Opened.Unreadable(R.string.read_failed, it.message.orEmpty()) }
            }
        }
    }

    PageScaffold(
        stringResource(R.string.backup_row),
        stringResource(R.string.backup_subtitle),
        onBack = onBack,
    ) {
        ScrollingColumn(
            state = rememberScrollState(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NavRow(
                stringResource(R.string.save_backup),
                stringResource(R.string.save_backup_detail),
                modifier = Modifier.focusRequester(first),
            ) { saveBackup.launch("Thor-Pathfinder-backup-${LocalDate.now()}.json") }
            NavRow(stringResource(R.string.restore_backup), stringResource(R.string.restore_backup_detail)) {
                openFile.launch(arrayOf("*/*"))
            }
            NavRow(stringResource(R.string.share_profile), stringResource(R.string.share_profile_detail)) {
                choosingShare = true
            }
            NavRow(stringResource(R.string.add_shared), stringResource(R.string.add_shared_detail)) {
                openFile.launch(arrayOf("*/*"))
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }

    if (choosingShare) {
        PickDialog(
            title = stringResource(R.string.share_which),
            choices = ui.profiles.map { profile ->
                profile.name to Profiles.linkedApps(context, profile.id).size.let { n ->
                    if (n == 0) null else context.resources.getQuantityString(R.plurals.share_apps, n, n)
                }
            },
            onPick = { index ->
                choosingShare = false
                val profile = ui.profiles[index]
                sharing = profile
                saveProfile.launch(Backup.profileFileName(profile.name))
            },
            onDismiss = { choosingShare = false },
        )
    }

    when (val file = opened) {
        null -> Unit
        is Backup.Opened.Unreadable -> AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(stringResource(R.string.cant_use)) },
            text = { Text(stringResource(file.reason, file.detail)) },
            confirmButton = { TextButton(onClick = { opened = null }) { Text(stringResource(R.string.ok)) } },
        )
        is Backup.Opened.Whole -> AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(stringResource(R.string.restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_body,
                        file.date,
                        file.version,
                        pluralStringResource(R.plurals.profiles_count, file.profiles.size, file.profiles.size),
                        file.profiles.joinToString(stringResource(R.string.list_sep)),
                        pluralStringResource(R.plurals.combos_count, file.combos, file.combos),
                        pluralStringResource(R.plurals.linked_apps_count, file.apps, file.apps),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    opened = null
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            runCatching {
                                Backup.restore(context, file)
                                context.resources.getQuantityString(R.plurals.restored, file.profiles.size, file.profiles.size)
                            }.getOrElse { context.getString(R.string.restore_failed, it.message.orEmpty()) }
                        }
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { opened = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is Backup.Opened.Shared -> {
            val full = ui.full
            AlertDialog(
                onDismissRequest = { opened = null },
                title = { Text(stringResource(R.string.add_title, file.name)) },
                text = {
                    Text(
                        if (full) {
                            stringResource(R.string.add_no_room, Profiles.LIMIT)
                        } else {
                            stringResource(
                                R.string.add_body,
                                file.version,
                                pluralStringResource(R.plurals.shortcuts_count, file.shortcuts, file.shortcuts),
                                pluralStringResource(R.plurals.combos_count, file.combos, file.combos),
                                pluralStringResource(R.plurals.linked_apps_count, file.apps, file.apps),
                            )
                        },
                    )
                },
                confirmButton = {
                    TextButton(enabled = !full, onClick = {
                        opened = null
                        scope.launch {
                            status = withContext(Dispatchers.IO) {
                                runCatching {
                                    val made = Backup.addShared(context, file) { pkg ->
                                        runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
                                    }
                                    if (made == null) {
                                        context.getString(R.string.no_room_msg)
                                    } else {
                                        context.getString(R.string.added, made.name)
                                    }
                                }.getOrElse { context.getString(R.string.add_failed, it.message.orEmpty()) }
                            }
                        }
                    }) { Text(stringResource(R.string.add)) }
                },
                dismissButton = { TextButton(onClick = { opened = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

private const val JSON = "application/json"

private fun created(): String = OffsetDateTime.now().withNano(0).toString()


private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
