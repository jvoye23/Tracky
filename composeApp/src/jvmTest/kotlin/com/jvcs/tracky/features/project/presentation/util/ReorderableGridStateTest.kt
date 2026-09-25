package com.jvcs.tracky.features.project.presentation.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
internal class ReorderableGridStateTest {

    private val moves = mutableListOf<Pair<String, String>>()

    /**
     * A 200×600 px grid with two 100 px columns: a full-width header row on top, then
     * `a b` / `c d`. The header is keyed with an Int, as the overview's section headers are
     * keyed with something other than a project id.
     */
    private fun ComposeUiTest.grid(): ReorderableGridState {
        lateinit var reorderState: ReorderableGridState
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                val gridState = rememberLazyGridState()
                reorderState = rememberReorderableGridState(gridState) { from, to -> moves += from to to }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.size(200.dp, 600.dp),
                ) {
                    item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.height(100.dp))
                    }
                    items(listOf("a", "b", "c", "d"), key = { it }) {
                        Box(Modifier.height(100.dp))
                    }
                }
            }
        }
        waitForIdle()
        return reorderState
    }

    @Test
    fun draggingSidewaysOntoTheNeighbourMovesOntoIt() =
        runComposeUiTest {
            val state = grid()

            runOnIdle {
                state.onDragStart("a")
                state.onDrag(Offset(100f, 0f))
            }

            assertThat(moves).containsExactly("a" to "b")
            runOnIdle { assertThat(state.draggingItemOffset).isEqualTo(Offset(100f, 0f)) }
        }

    @Test
    fun draggingDownOntoTheCardBelowMovesOntoIt() =
        runComposeUiTest {
            val state = grid()

            runOnIdle {
                state.onDragStart("a")
                state.onDrag(Offset(0f, 100f))
            }

            assertThat(moves).containsExactly("a" to "c")
        }

    @Test
    fun aHeaderIsNeverADropTarget() =
        runComposeUiTest {
            val state = grid()

            runOnIdle {
                state.onDragStart("a")
                state.onDrag(Offset(0f, -100f))
            }

            assertThat(moves).isEmpty()
        }

    @Test
    fun releasingSettlesTheCardBackIntoItsSlot() =
        runComposeUiTest {
            val state = grid()
            runOnIdle {
                state.onDragStart("a")
                state.onDrag(Offset(10f, 10f))
                state.onDragEnd()
            }

            runOnIdle { assertThat(state.draggingItemKey).isNull() }
            waitUntil { state.settlingItemKey == null }
            assertThat(state.settlingItemOffset).isEqualTo(Offset.Zero)
        }

    private companion object {
        const val HEADER_KEY = 0
    }
}
