package com.thorpathfinder.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thorpathfinder.app.ComboKey
import com.thorpathfinder.app.Combos
import com.thorpathfinder.app.R
import com.thorpathfinder.app.words
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * The small A + B beside the buttons that combos start from: two tiny round
 * buttons in a thin pill, so it reads as a label and never as something to
 * press. It only marks them.
 */
@Composable
internal fun ComboBadge() {
    val description = stringResource(R.string.combos_badge)
    Row(
        Modifier
            .semantics(mergeDescendants = true) { contentDescription = description }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PillShape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeButton("A")
        Text(
            "+",
            style = BadgeText,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        BadgeButton("B")
    }
}

/** One face button of [ComboBadge]: a letter in a tiny filled circle. */
@Composable
private fun BadgeButton(letter: String) {
    Box(
        Modifier.size(13.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = BadgeText,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private val BadgeText = TextStyle(fontSize = 8.sp, lineHeight = 8.sp)

/**
 * The buttons a combo can use, as the picker lays them out: a row for each
 * kind, each row's buttons in the order they sit on the Thor.
 */
internal val COMBO_KEY_GROUPS: List<Pair<Int, List<ComboKey>>> = listOf(
    R.string.group_face to listOf(ComboKey.A, ComboKey.B, ComboKey.X, ComboKey.Y),
    R.string.group_dpad to listOf(ComboKey.UP, ComboKey.DOWN, ComboKey.LEFT, ComboKey.RIGHT),
    R.string.group_shoulders to listOf(ComboKey.L1, ComboKey.R1, ComboKey.L2, ComboKey.R2),
    R.string.group_sticks to listOf(ComboKey.L3, ComboKey.R3),
    R.string.group_select_start to listOf(ComboKey.SELECT, ComboKey.START),
    R.string.group_system to listOf(ComboKey.BACK, ComboKey.HOME, ComboKey.AYN),
)

/** A button's name in its row, where the row's name already says what kind it is. */
@StringRes
private fun cellLabel(key: ComboKey): Int = when (key) {
    ComboKey.UP -> R.string.key_up_short
    ComboKey.DOWN -> R.string.key_down_short
    ComboKey.LEFT -> R.string.key_left_short
    ComboKey.RIGHT -> R.string.key_right_short
    else -> key.text
}

/**
 * The first step of adding a combo to [held]: which button, or which two
 * pressed together, go with it, from a table of the Thor's buttons by kind.
 * Next hands back the whole combo, [held] included, for the shortcut list to
 * fill in.
 */
@Composable
internal fun ComboKeysDialog(held: ComboKey, onNext: (Set<ComboKey>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val words = remember(context) { context.words() }
    val heldName = stringResource(held.text)
    val groups = COMBO_KEY_GROUPS.map { (name, keys) -> name to (keys - held) }.filter { it.second.isNotEmpty() }
    val columns = groups.maxOf { it.second.size }
    var chosen by remember { mutableStateOf(emptySet<ComboKey>()) }
    val first = remember { FocusRequester() }
    val firstKey = groups.first().second.first()
    val most = Combos.MAX - 1

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Card(Modifier.widthIn(max = 640.dp)) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        stringResource(R.string.combo_hold_press, heldName),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.combo_pick_detail, heldName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        groups.forEach { (name, keys) ->
                            Row(
                                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(name),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(112.dp).padding(start = 12.dp),
                                )
                                repeat(columns) { index ->
                                    val key = keys.getOrNull(index)
                                    if (key == null) {
                                        Spacer(Modifier.weight(1f))
                                        return@repeat
                                    }
                                    val checked = key in chosen
                                    val open = checked || chosen.size < most
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(if (key == firstKey) Modifier.focusRequester(first) else Modifier)
                                            .focusOutline()
                                            .clip(RowShape)
                                            .toggleable(value = checked, enabled = open, role = Role.Checkbox) { want ->
                                                chosen = if (want) chosen + key else chosen - key
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = null, enabled = open)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(cellLabel(key)),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (open) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.align(Alignment.End).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.focusOutline(PillShape)) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = { onNext(chosen + held) },
                            enabled = chosen.isNotEmpty(),
                            modifier = Modifier.focusOutline(PillShape),
                        ) {
                            Text(
                                if (chosen.isEmpty()) {
                                    stringResource(R.string.next)
                                } else {
                                    stringResource(R.string.next_with, Combos.label(words, chosen + held))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }
}
