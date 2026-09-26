package com.jvcs.tracky.designsystem.components.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.DeviceConfiguration
import com.jvcs.tracky.designsystem.util.PreviewDevices
import com.jvcs.tracky.designsystem.util.currentDeviceConfiguration

private val ItemSpacing = 8.dp

/** Breathing room between the top bar and the first item of each two-pane column. */
private val TwoPaneTopGap = 16.dp

/**
 * Structural container for a detail screen: a summary block followed by a list.
 *
 * - Portrait: one [LazyColumn]; the summary is its first item and the list follows beneath it.
 * - Landscape and desktop: two equal columns — the summary scrolls on its own on the left, the
 *   list on the right. Both columns start [listTopPadding] plus a small gap down, so their first
 *   items clear the top bar.
 *
 * Both branches drive the list through the same [listState], so a rotation keeps the list's
 * scroll position and anything keyed on it (reorder, collapsing top bar) keeps working.
 * [summary] and [list] receive `isTwoPane`, so the screen never re-derives the window size.
 *
 * @param contentPadding the Scaffold's padding; only its bottom and horizontal sides are applied,
 * because in portrait the summary paints behind the top bar and pads for it itself.
 * @param listTopPadding the height the top bar covers.
 */
@Composable
fun TrackyAdaptiveDetailLayout(
    listState: LazyListState,
    contentPadding: PaddingValues,
    listTopPadding: Dp,
    summary: @Composable ColumnScope.(isTwoPane: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    list: LazyListScope.(isTwoPane: Boolean) -> Unit,
) {
    // Movable so a rotation carries the summary's state (e.g. the per-day strip's scroll) across
    // branches. Read through updated state: keying remember on the slot would rebuild it every time.
    val currentSummary by rememberUpdatedState(summary)
    val movableSummary =
        remember {
            movableContentWithReceiverOf<ColumnScope, Boolean> { isTwoPane -> currentSummary(isTwoPane) }
        }
    val bottomPadding = contentPadding.calculateBottomPadding()

    when (currentDeviceConfiguration()) {
        DeviceConfiguration.MOBILE_PORTRAIT,
        DeviceConfiguration.TABLET_PORTRAIT,
        -> {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(ItemSpacing),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                        movableSummary(false)
                    }
                }
                list(false)
            }
        }

        DeviceConfiguration.MOBILE_LANDSCAPE,
        DeviceConfiguration.TABLET_LANDSCAPE,
        DeviceConfiguration.DESKTOP,
        -> {
            val layoutDirection = LocalLayoutDirection.current
            Row(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(
                            start = contentPadding.calculateStartPadding(layoutDirection),
                            end = contentPadding.calculateEndPadding(layoutDirection),
                        ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(top = listTopPadding + TwoPaneTopGap, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                ) {
                    movableSummary(true)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = listTopPadding + TwoPaneTopGap, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                ) {
                    list(true)
                }
            }
        }
    }
}

@PreviewDevices
@Preview(name = "Desktop", widthDp = 1440, heightDp = 1024)
@Composable
private fun TrackyAdaptiveDetailLayoutPreview() {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            TrackyAdaptiveDetailLayout(
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(),
                listTopPadding = 64.dp,
                summary = { isTwoPane ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = if (isTwoPane) "Summary beside the list" else "Summary above the list")
                    }
                },
                list = { _ ->
                    items((1..12).toList()) { index ->
                        Text(text = "List item $index", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                },
            )
        }
    }
}
