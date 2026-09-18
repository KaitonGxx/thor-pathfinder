package com.thorpathfinder.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * How far to scroll to bring a focused item into view, for a controller.
 *
 * Compose scrolls just far enough to show the item. On the first or last one
 * that leaves the page's padding, and anything else that can't take focus,
 * out of sight at that end. So an item within [edge] of either end of the
 * content takes the scroll all the way to that end, as long as the whole item
 * is still in view there. Anything else scrolls the usual way ([usual]).
 *
 * All in pixels: [value] and [max] are the scroll position and its limit,
 * [offset] the item's top relative to the top of the [viewport].
 */
internal fun edgeAwareScrollDistance(
    value: Float,
    max: Float,
    offset: Float,
    size: Float,
    viewport: Float,
    edge: Float,
    usual: () -> Float,
): Float {
    val top = value + offset // where the item sits in the content
    val bottom = top + size
    return when {
        top <= edge && bottom <= viewport -> -value
        bottom >= max + viewport - edge && top >= max -> max - value
        else -> usual()
    }
}

/** How close to an end of the page a focused item must be to take the scroll there. */
private val EdgeReach = 64.dp

@OptIn(ExperimentalFoundationApi::class)
private class EdgeAwareBringIntoView(
    private val state: ScrollState,
    private val edge: Float,
    private val usual: BringIntoViewSpec,
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = usual.scrollAnimationSpec

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        edgeAwareScrollDistance(
            value = state.value.toFloat(),
            max = state.maxValue.toFloat(),
            offset = offset,
            size = size,
            viewport = containerSize,
            edge = edge,
        ) { usual.calculateScrollDistance(offset, size, containerSize) }
}

/**
 * A scrolling [Column] whose true top and bottom a controller reaches (see
 * [edgeAwareScrollDistance]). [modifier] applies to the visible area;
 * [contentPadding] sits inside the scrolling content, so it scrolls with it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollingColumn(
    state: ScrollState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val usual = LocalBringIntoViewSpec.current
    val edge = with(LocalDensity.current) { EdgeReach.toPx() }
    val spec = remember(state, usual, edge) { EdgeAwareBringIntoView(state, edge, usual) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        Column(
            modifier.verticalScroll(state).padding(contentPadding),
            verticalArrangement = verticalArrangement,
        ) {
            // Only this Column scrolls this way; lists in dialogs opened from
            // inside it keep their own behaviour.
            CompositionLocalProvider(LocalBringIntoViewSpec provides usual) { content() }
        }
    }
}
