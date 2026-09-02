package com.jvcs.tracky.design_system.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCollapsibleScrollBehavior(
    listState: LazyListState,
    pinned: Boolean = false
): TopAppBarScrollBehavior {
    // Without this guard the bar collapses on any drag, even when the whole list fits on screen and
    // there is nothing to scroll to. TopAppBarDefaults remembers the behavior keyed on this lambda,
    // so its identity has to stay stable across recompositions.
    val canScroll = remember(listState) {
        { listState.canScrollForward || listState.canScrollBackward }
    }
    // Each branch keeps its own TopAppBarState, so toggling `pinned` starts the incoming behavior
    // fully expanded. Sharing one state instead would carry a collapsed heightOffset into the
    // pinned branch, which reads that offset but never writes it — the bar would be stuck closed.
    val behavior = if (pinned) {
        TopAppBarDefaults.pinnedScrollBehavior(canScroll = canScroll)
    } else {
        TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = canScroll)
    }
    // If the content shrinks below one screen while the bar is collapsed, no scroll is left to
    // bring it back, so release it explicitly.
    LaunchedEffect(behavior) {
        snapshotFlow(canScroll).collect { scrollable ->
            if (!scrollable) behavior.state.heightOffset = 0f
        }
    }
    return behavior
}
