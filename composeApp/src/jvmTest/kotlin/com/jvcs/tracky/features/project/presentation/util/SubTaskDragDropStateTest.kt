package com.jvcs.tracky.features.project.presentation.util

import androidx.compose.runtime.MonotonicFrameClock
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class SubTaskDragDropStateTest {

    private val moves = mutableListOf<Pair<String, String>>()

    /** Advances 16 ms per frame, so the settle animation runs to its end instead of stalling. */
    private val frameClock =
        object : MonotonicFrameClock {
            private var frameNanos = 0L

            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                frameNanos += 16_000_000L
                return onFrame(frameNanos)
            }
        }

    private fun TestScope.state() =
        SubTaskDragDropState(
            scope = CoroutineScope(coroutineContext + frameClock),
            onMove = { from, to -> moves += from to to },
        ).apply {
            onRowPlaced("a", top = 0f, height = 50f)
            onRowPlaced("b", top = 50f, height = 50f)
            onRowPlaced("c", top = 100f, height = 50f)
        }

    @Test
    fun draggingPastASiblingsMidpointMovesOntoIt() =
        runTest {
            val state = state()

            state.onDragStart("a")
            state.onDrag(60f)

            assertThat(moves).containsExactly("a" to "b")
            assertThat(state.hasMoved).isTrue()
            assertThat(state.draggingItemOffset).isEqualTo(60f)
        }

    @Test
    fun releasingSettlesTheRowBackIntoItsSlot() =
        runTest {
            val state = state()
            state.onDragStart("a")
            state.onDrag(10f)

            state.onDragCancel()

            assertThat(state.draggingItemKey).isNull()
            assertThat(state.settlingItemKey).isEqualTo("a")
            testScheduler.advanceUntilIdle()
            assertThat(state.settlingItemKey).isNull()
            assertThat(state.settlingItemOffset).isEqualTo(0f)
        }

    @Test
    fun anUnplacedRowOrNoDragChangesNothing() =
        runTest {
            val state = state()
            state.onRowDisposed("c")

            state.onDragStart("c")
            state.onDrag(40f)
            state.onDragEnd()

            assertThat(state.draggingItemKey).isNull()
            assertThat(state.hasMoved).isFalse()
            assertThat(state.draggingItemOffset).isEqualTo(0f)
            assertThat(moves).isEqualTo(emptyList())
        }
}
