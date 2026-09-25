package com.thorpathfinder.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.thorpathfinder.app.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PathfinderTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = runCatching {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse { if (dark) darkColorScheme() else lightColorScheme() }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Material's scheme has no warning colour, so the update notice brings its own. */
val WarningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4A3B12) else Color(0xFFFFF0C2)

val OnWarningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF7E3A8) else Color(0xFF473600)

/** Green for "nothing to do", yellow for "have a look"; both readable in either theme. */
val OkGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF3FA95E) else Color(0xFF2E7D32)

val WarningYellow: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF2C14E) else Color(0xFFB07800)

/** A button's symbol in a small filled circle, the way the profile map's Dismiss shows the A button. */
@Composable
fun ButtonSymbol(icon: ImageVector) {
    Box(
        Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A green dot with a white tick: everything is as it should be. */
@Composable
fun OkDot(size: Dp = 18.dp) {
    Box(Modifier.size(size).background(OkGreen, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}

/** A white dot with a dash: nothing to report, because nothing has been checked. */
@Composable
fun NeutralDot(size: Dp = 18.dp) {
    Box(
        Modifier
            .size(size)
            .background(Color.White, CircleShape)
            // White on a light screen needs an edge to be seen at all.
            .then(
                if (isSystemInDarkTheme()) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = size * 0.5f, height = 2.dp).background(Color(0xFF424242), RoundedCornerShape(1.dp)))
    }
}

/** A yellow triangle: something is waiting. */
@Composable
fun WarningTriangle(size: Dp = 18.dp) {
    Icon(
        Icons.Filled.Warning,
        contentDescription = null,
        tint = WarningYellow,
        modifier = Modifier.size(size),
    )
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

/**
 * A page of its own, with a back arrow and a title. Its content fills what's
 * left, so a list inside it can take `weight(1f)`.
 */
@Composable
fun PageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.focusOutline(PillShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/** A row that opens a page of its own. One focus stop. */
@Composable
fun NavRow(title: String, detail: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .focusOutline()
            .clip(RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A heading inside a list, for a group of rows. */
@Composable
fun ListHeading(title: String, detail: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A card that folds its content away behind its title, one focus stop that
 * opens and closes it. Closed, it shows [summary] under the title; open, it
 * shows [subtitle] and the content. A [symbol] sits to the left of the title
 * in a small circle, as a button's cap would show it, and a [badge] on the
 * right, beside the arrow and level with the title.
 */
@Composable
fun CollapsibleCard(
    title: String,
    summary: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    symbol: ImageVector? = null,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusOutline()
                    .clip(RowShape)
                    .clickable(
                        onClickLabel = stringResource(if (expanded) R.string.close else R.string.open),
                        onClick = onToggle,
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (symbol != null) {
                    ButtonSymbol(symbol)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val line = if (expanded) subtitle else summary
                    if (line != null) {
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Two lines, so a longer language still shows every gesture.
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Level with the title, so it sits above the arrow's middle.
                if (badge != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.align(Alignment.Top).padding(top = 4.dp)) { badge() }
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Opening animates; closing is instant. While a fold-away animation
            // runs, the rows are still there and can take focus, so a D-pad press
            // landed on one, which then vanished and threw focus to the top.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = ExitTransition.None,
            ) {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    HorizontalDivider(
                        Modifier.padding(top = 2.dp, bottom = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    content()
                }
            }
        }
    }
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
fun ValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
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
fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier
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

/** A few choices that each lead somewhere else, as rows with an arrow. Focus starts on the first. */
@Composable
fun PickDialog(title: String, choices: List<Pair<String, String?>>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Column(Modifier.padding(horizontal = 12.dp)) {
                    choices.forEachIndexed { index, (name, detail) ->
                        NavRow(
                            name,
                            detail,
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { onPick(index) },
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).focusOutline(PillShape),
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { first.requestFocus() } }
}

/** The ways [ChoiceDialog] can lay out a long list: one column, two, or a wide grid of three. */
object ChoiceLayout {
    const val LIST = 1
    const val TWO = 2
    const val WIDE = 3

    /** A stored layout, or wide when it is not one of these. */
    fun of(columns: Int): Int = if (columns == LIST || columns == TWO) columns else WIDE
}

/**
 * Pick one of [options]; focus starts on the current choice. An option that
 * [leadsOn] asks more before anything is saved, and carries the same arrow
 * as a row that opens a page; those come last, together, below a thin line.
 * With more than one of [columns], the options sit in a grid read across, in
 * a dialog wide enough for it, so a long list fits the Thor's short screens.
 * Given [onColumnsChange], the title row offers the [ChoiceLayout]s to switch
 * between.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    detail: (T) -> String? = { null },
    leadsOn: (T) -> Boolean = { false },
    columns: Int = 1,
    onColumnsChange: ((Int) -> Unit)? = null,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember { FocusRequester() }
    val grid = columns > 1
    // Sized by hand when it can be a grid, so switching layouts never changes
    // how Android sizes the window.
    val sized = grid || onColumnsChange != null
    val groups = listOf(options.filterNot(leadsOn), options.filter(leadsOn)).filter { it.isNotEmpty() }
    val shown = groups.flatten()

    @Composable
    fun Choice(index: Int, option: T, modifier: Modifier) {
        val isSelected = option == selected
        val focusFirst = isSelected || (selected == null && index == 0)
        Row(
            modifier
                .then(if (focusFirst) Modifier.focusRequester(initial) else Modifier)
                .focusOutline()
                .clip(RowShape)
                .selectable(selected = isSelected, role = Role.RadioButton) { onPick(option) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Spacer(Modifier.width(if (grid) 8.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(label(option), style = MaterialTheme.typography.bodyLarge)
                detail(option)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (leadsOn(option)) {
                Spacer(Modifier.width(if (grid) 4.dp else 12.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        // Android's own dialog width is for one column; a grid sizes itself.
        properties = DialogProperties(usePlatformDefaultWidth = !sized),
    ) {
        Box(
            if (sized) Modifier.fillMaxWidth().padding(horizontal = 16.dp) else Modifier,
            contentAlignment = Alignment.Center,
        ) {
            val maxWidth = when (columns) {
                ChoiceLayout.LIST -> 560.dp
                ChoiceLayout.TWO -> 640.dp
                else -> 760.dp
            }
            Card(if (sized) Modifier.widthIn(max = maxWidth) else Modifier) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    Row(
                        Modifier.padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (onColumnsChange != null) {
                            Spacer(Modifier.width(12.dp))
                            val layout = ChoiceLayout.of(columns)
                            LayoutSwitch(Icons.AutoMirrored.Filled.List, stringResource(R.string.list_view), layout == ChoiceLayout.LIST) {
                                onColumnsChange(ChoiceLayout.LIST)
                            }
                            Spacer(Modifier.width(4.dp))
                            LayoutSwitch(TwoIcon, stringResource(R.string.two_column_view), layout == ChoiceLayout.TWO) {
                                onColumnsChange(ChoiceLayout.TWO)
                            }
                            Spacer(Modifier.width(4.dp))
                            LayoutSwitch(WideIcon, stringResource(R.string.wide_view), layout == ChoiceLayout.WIDE) {
                                onColumnsChange(ChoiceLayout.WIDE)
                            }
                        }
                    }
                    // Shrinks to fit a short screen, so Cancel stays in sight below it.
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        groups.forEachIndexed { group, members ->
                            // A rule in the gap, like the ones under card titles, lined up with the radio buttons.
                            if (group > 0) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            if (grid) {
                                // Each group starts a row of its own.
                                members.chunked(columns).forEach { cells ->
                                    // Each cell as tall as the tallest beside it, so the outlines line up.
                                    Row(
                                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        cells.forEach { option ->
                                            Choice(shown.indexOf(option), option, Modifier.weight(1f).fillMaxHeight())
                                        }
                                        repeat(columns - cells.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            } else {
                                members.forEach { option -> Choice(shown.indexOf(option), option, Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).focusOutline(PillShape),
                    ) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
    val inputMode = LocalInputModeManager.current.inputMode
    LaunchedEffect(inputMode) { runCatching { initial.requestFocus() } }
}

/** One of the two layout icons on a [ChoiceDialog]'s title row; the one in use is filled in. */
@Composable
private fun LayoutSwitch(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .focusOutline(CircleShape)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Two columns of two: the two-column view, drawn as itself. */
private val TwoIcon: ImageVector = gridIcon("TwoColumnView", columns = 2)

/** Three columns of two: the wide view, drawn as itself. */
private val WideIcon: ImageVector = gridIcon("WideView", columns = 3)

/** Blocks in [columns] columns and two rows. Material's core icons have no grid. */
private fun gridIcon(name: String, columns: Int): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    val gap = 1.5f
    val width = (18f - gap * (columns - 1)) / columns
    path(fill = SolidColor(Color.Black)) {
        for (column in 0 until columns) {
            for (row in 0..1) {
                val x = 3f + column * (width + gap)
                val y = 5f + row * 7.5f
                moveTo(x, y)
                lineTo(x + width, y)
                lineTo(x + width, y + 6f)
                lineTo(x, y + 6f)
                close()
            }
        }
    }
}.build()
