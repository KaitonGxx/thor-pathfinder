package com.thorpathfinder.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun PathfinderTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = runCatching {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse { if (dark) darkColorScheme() else lightColorScheme() }
    MaterialTheme(colorScheme = colors, content = content)
}

val RowShape: Shape = RoundedCornerShape(12.dp)

/** Material buttons' shape, so the focus outline hugs them. */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/**
 * A clear outline while an element has focus. The Thor is driven with its
 * controller as much as by touch, and Material's own focus tint is too faint
 * to follow. Put it before the element's click handling in the chain.
 */
fun Modifier.focusOutline(shape: Shape = RowShape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
    onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(2.dp, if (focused) color.copy(alpha = 0.9f) else Color.Transparent, shape)
}

/** A titled card; a rule separates the title from what's under it. */
@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            content()
        }
    }
}

/** A setting that shows its value and opens a picker. One focus stop. */
@Composable
fun ValueRow(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusOutline()
            .clip(RowShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A whole-row switch: one focus stop for the D-pad, touch anywhere on it. */
@Composable
fun SwitchRow(title: String, detail: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusOutline()
            .clip(RowShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Pick one of [options]; focus starts on the current choice. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        val isSelected = option == selected
                        val focusFirst = isSelected || (selected == null && index == 0)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (focusFirst) Modifier.focusRequester(initial) else Modifier)
                                .focusOutline()
                                .clip(RowShape)
                                .selectable(selected = isSelected, role = Role.RadioButton) { onPick(option) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(label(option), style = MaterialTheme.typography.bodyLarge)
                                detail(option)?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).focusOutline(),
                ) { Text("Cancel") }
            }
        }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { initial.requestFocus() } }
}
