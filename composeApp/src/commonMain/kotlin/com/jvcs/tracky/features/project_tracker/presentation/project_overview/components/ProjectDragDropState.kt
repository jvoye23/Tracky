package com.jvcs.tracky.features.project_tracker.presentation.project_overview.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Hand-rolled reorder state for the project [LazyListState]. It tracks the card being dragged, keeps
 * it under the finger via [draggingItemOffset], and asks [onMove] to swap two cards as the dragged
 * one crosses a neighbour. On release the dragged card glides into its final slot via
 * [settlingItemOffset] while every other card animates through `Modifier.animateItem()`.
 *
 * Item keys in the list are project ids ([String]); section headers use non-String keys and are
 * ignored as drag targets. Cross-section moves are rejected by [onMove] (see the screen wiring).
 */
class ProjectDragDropState internal constructor(
    private val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromKey: String, toKey: String) -> Unit
) {
    var draggingItemKey by mutableStateOf<String?>(null)
        private set

    /** The card animating back into its slot right after release (null when nothing is settling). */
    var settlingItemKey by mutableStateOf<String?>(null)
        private set

    /** True once the finger has actually moved, distinguishing a reorder from a long-press-to-select. */
    var hasMoved by mutableStateOf(false)
        private set

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)
    private val settleOffset = Animatable(0f)

    // Conflated so onDrag (not a suspend fun) never blocks; a single consumer performs the scroll.
    private val scrollChannel = Channel<Float>(Channel.CONFLATED)

    init {
        scope.launch {
            for (diff in scrollChannel) {
                lazyListState.scrollBy(diff)
            }
        }
    }

    private val draggingItemLayoutInfo
        get() = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggingItemKey }

    /** Vertical translation that keeps the dragged card pinned under the finger. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            (draggingItemInitialOffset + draggingItemDraggedDelta) - item.offset
        } ?: 0f

    /** Vertical translation of the card gliding into place after release. */
    val settlingItemOffset: Float
        get() = settleOffset.value

    fun onDragStart(key: String) {
        val info = lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key } ?: return
        draggingItemKey = key
        draggingItemInitialOffset = info.offset
        draggingItemDraggedDelta = 0f
        hasMoved = false
    }

    fun onDrag(offsetY: Float) {
        val draggingKey = draggingItemKey ?: return
        draggingItemDraggedDelta += offsetY
        hasMoved = true

        val dragging = draggingItemLayoutInfo ?: return
        val startOffset = dragging.offset + draggingItemOffset
        val endOffset = startOffset + dragging.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val target = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val key = item.key
            key is String && key != draggingKey &&
                middleOffset.toInt() in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            onMove(draggingKey, target.key as String)
        }

        // Auto-scroll when the dragged card is pushed past a viewport edge.
        val viewport = lazyListState.layoutInfo
        val overscroll = when {
            draggingItemDraggedDelta > 0 -> (endOffset - viewport.viewportEndOffset).coerceAtLeast(0f)
            draggingItemDraggedDelta < 0 -> (startOffset - viewport.viewportStartOffset).coerceAtMost(0f)
            else -> 0f
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
        // Hand off to the settling state synchronously (before clearing the dragging key) so the list
        // never sees a frame where nothing is reordering and re-syncs the mirrors from stale source.
        settlingItemKey = key
        draggingItemKey = null
        draggingItemDraggedDelta = 0f
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
        draggingItemDraggedDelta = 0f
        hasMoved = false
    }
}

@Composable
fun rememberProjectDragDropState(
    lazyListState: LazyListState,
    onMove: (fromKey: String, toKey: String) -> Unit
): ProjectDragDropState {
    val scope = rememberCoroutineScope()
    // onMove closes over remembered lists (stable identities), so capturing it once is safe.
    return remember(lazyListState) {
        ProjectDragDropState(lazyListState, scope, onMove)
    }
}
