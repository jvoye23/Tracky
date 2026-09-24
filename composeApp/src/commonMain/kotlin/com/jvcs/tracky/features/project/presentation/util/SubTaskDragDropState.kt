package com.jvcs.tracky.features.project.presentation.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [ReorderableListState]'s job for rows that are not in a lazy layout.
 *
 * Subtasks are a plain Column inside one LazyColumn item, so there is no `LazyListState` and no
 * `layoutInfo` to hit-test against. Each row reports its own measured bounds through [onRowPlaced]
 * instead, and the drop target is found the same way the lazy version finds it: the sibling whose
 * vertical range contains the dragged row's midpoint.
 *
 * The dragged row keeps its grip on the finger through [draggingItemOffset], which is derived from
 * the row's *current* slot rather than accumulated blindly — so when [onMove] reorders the list
 * underneath, the offset self-corrects on the next layout pass instead of jumping by a row height.
 *
 * Its siblings snap to their new slots rather than sliding into them. `Modifier.animateItem()` only
 * exists inside a lazy layout, and animating placement here would fight the bounds this state reads
 * back for hit-testing. The row under the finger is the one that has to track it, and it does.
 */
class SubTaskDragDropState internal constructor(
    private val scope: CoroutineScope,
    private val onMove: (fromKey: String, toKey: String) -> Unit,
) {

    var draggingItemKey by mutableStateOf<String?>(null)
        private set

    /** The row animating back into its slot right after release (null when nothing is settling). */
    var settlingItemKey by mutableStateOf<String?>(null)
        private set

    /** True once the finger has actually moved, so a tap on the grip never counts as a reorder. */
    var hasMoved by mutableStateOf(false)
        private set

    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialTop = 0f
    private val settleOffset = Animatable(0f)

    // Top edge and height of every row currently composed, in the subtask column's own space.
    private val rowTops = mutableStateMapOf<String, Float>()
    private val rowHeights = mutableStateMapOf<String, Float>()

    /** Vertical translation that keeps the dragged row pinned under the finger. */
    val draggingItemOffset: Float
        get() = rowTops[draggingItemKey]?.let { top -> (initialTop + draggedDelta) - top } ?: 0f

    /** Vertical translation of the row gliding into place after release. */
    val settlingItemOffset: Float
        get() = settleOffset.value

    /** Reported by each row as it is measured, and again whenever a reorder moves it. */
    fun onRowPlaced(
        key: String,
        top: Float,
        height: Float,
    ) {
        rowTops[key] = top
        rowHeights[key] = height
    }

    /** Rows leave when a subtask is deleted, or when the card collapses. */
    fun onRowDisposed(key: String) {
        rowTops.remove(key)
        rowHeights.remove(key)
    }

    fun onDragStart(key: String) {
        initialTop = rowTops[key] ?: return
        draggingItemKey = key
        draggedDelta = 0f
        hasMoved = false
    }

    fun onDrag(offsetY: Float) {
        val draggingKey = draggingItemKey ?: return
        draggedDelta += offsetY
        hasMoved = true

        val top = rowTops[draggingKey] ?: return
        val height = rowHeights[draggingKey] ?: return
        val middle = top + draggingItemOffset + height / 2f

        val target =
            rowTops.keys.firstOrNull { key ->
                if (key == draggingKey) return@firstOrNull false
                val otherTop = rowTops[key] ?: return@firstOrNull false
                val otherHeight = rowHeights[key] ?: return@firstOrNull false
                middle >= otherTop && middle <= otherTop + otherHeight
            }
        if (target != null) onMove(draggingKey, target)
    }

    fun onDragEnd() {
        val key = draggingItemKey
        if (key == null) {
            reset()
            return
        }

        val from = draggingItemOffset
        // Hand off to the settling state before clearing the dragging key, so the row never renders
        // one frame back in its old slot between the two.
        settlingItemKey = key
        draggingItemKey = null
        draggedDelta = 0f
        hasMoved = false

        scope.launch {
            settleOffset.snapTo(from)
            settleOffset.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 220))
            settlingItemKey = null
        }
    }

    fun onDragCancel() = onDragEnd()

    private fun reset() {
        draggingItemKey = null
        draggedDelta = 0f
        hasMoved = false
    }
}

/**
 * One state per task card. Keyed on the task id so two cards never share a drag, and so a card
 * recycled by the LazyColumn into a different task starts with empty bounds.
 */
@Composable
fun rememberSubTaskDragDropState(
    taskId: String,
    onMove: (fromKey: String, toKey: String) -> Unit,
): SubTaskDragDropState {
    val scope = rememberCoroutineScope()
    // The state outlives the lambda: onMove closes over the screen's onAction and is recreated on
    // every recomposition, so route through the latest one instead of capturing the first.
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(taskId) {
        SubTaskDragDropState(scope) { fromKey, toKey -> currentOnMove(fromKey, toKey) }
    }
}
