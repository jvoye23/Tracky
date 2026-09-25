package com.jvcs.tracky.features.project.presentation.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The two-dimensional sibling of [ReorderableListState] for a [LazyGridState]: the dragged card
 * follows the finger on both axes, and it swaps with whichever card its center is hovering over,
 * whether that card sits beside it or above/below it.
 *
 * Drag targets are identified by their item key, which must be a [String]. Anything keyed
 * otherwise (the overview's section headers) is ignored, which keeps a drag inside the section it
 * started in; rejecting cross-section moves beyond that belongs in [onMove].
 */
class ReorderableGridState internal constructor(
    private val gridState: LazyGridState,
    private val scope: CoroutineScope,
    private val onMove: (fromKey: String, toKey: String) -> Unit,
) {

    var draggingItemKey by mutableStateOf<String?>(null)
        private set

    /** The card animating back into its slot right after release (null when nothing is settling). */
    var settlingItemKey by mutableStateOf<String?>(null)
        private set

    /** True once the finger has actually moved, distinguishing a reorder from a long-press-to-select. */
    var hasMoved by mutableStateOf(false)
        private set

    private var draggingItemDraggedDelta by mutableStateOf(Offset.Zero)
    private var draggingItemInitialOffset by mutableStateOf(Offset.Zero)
    private val settleOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    // Conflated so onDrag (not a suspend fun) never blocks; a single consumer performs the scroll.
    private val scrollChannel = Channel<Float>(Channel.CONFLATED)

    init {
        scope.launch {
            for (diff in scrollChannel) {
                gridState.scrollBy(diff)
            }
        }
    }

    private fun itemInfo(key: String?): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }

    /** Translation that keeps the dragged card pinned under the finger. */
    val draggingItemOffset: Offset
        get() =
            itemInfo(draggingItemKey)?.let { item ->
                draggingItemInitialOffset + draggingItemDraggedDelta - item.offset.toOffset()
            } ?: Offset.Zero

    /** Translation of the card gliding into place after release. */
    val settlingItemOffset: Offset
        get() = settleOffset.value

    fun onDragStart(key: String) {
        val info = itemInfo(key) ?: return
        draggingItemKey = key
        draggingItemInitialOffset = info.offset.toOffset()
        draggingItemDraggedDelta = Offset.Zero
        hasMoved = false
    }

    fun onDrag(delta: Offset) {
        val draggingKey = draggingItemKey ?: return
        draggingItemDraggedDelta += delta
        hasMoved = true

        val dragging = itemInfo(draggingKey) ?: return
        val draggedBounds = Rect(dragging.offset.toOffset() + draggingItemOffset, dragging.size.toSize())
        val center = draggedBounds.center

        val target =
            gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                val key = item.key
                key is String &&
                    key != draggingKey &&
                    Rect(item.offset.toOffset(), item.size.toSize()).contains(center)
            }
        if (target != null) {
            onMove(draggingKey, target.key as String)
        }

        // Auto-scroll (vertical only) when the dragged card is pushed past a viewport edge.
        val viewport = gridState.layoutInfo
        val overscroll =
            when {
                draggingItemDraggedDelta.y > 0 -> {
                    (draggedBounds.bottom - viewport.viewportEndOffset).coerceAtLeast(0f)
                }

                draggingItemDraggedDelta.y < 0 -> {
                    (draggedBounds.top - viewport.viewportStartOffset).coerceAtMost(0f)
                }

                else -> {
                    0f
                }
            }
        if (overscroll != 0f) scrollChannel.trySend(overscroll)
    }

    fun onDragEnd() {
        val key = draggingItemKey
        if (key == null) {
            reset()
            return
        }

        val from = draggingItemOffset
        // Hand off to the settling state synchronously (before clearing the dragging key) so the grid
        // never sees a frame where nothing is reordering and re-syncs the order from a stale source.
        settlingItemKey = key
        draggingItemKey = null
        draggingItemDraggedDelta = Offset.Zero
        hasMoved = false

        scope.launch {
            settleOffset.snapTo(from)
            settleOffset.animateTo(targetValue = Offset.Zero, animationSpec = tween(durationMillis = 220))
            settlingItemKey = null
        }
    }

    fun onDragCancel() = onDragEnd()

    private fun reset() {
        draggingItemKey = null
        draggingItemDraggedDelta = Offset.Zero
        hasMoved = false
    }
}

@Composable
fun rememberReorderableGridState(
    gridState: LazyGridState,
    onMove: (fromKey: String, toKey: String) -> Unit,
): ReorderableGridState {
    val scope = rememberCoroutineScope()
    // The state outlives the lambda: onMove is recreated on every recomposition (it closes over the
    // screen's onAction), so route through the latest one instead of capturing the first.
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(gridState) {
        ReorderableGridState(gridState, scope) { fromKey, toKey -> currentOnMove(fromKey, toKey) }
    }
}
