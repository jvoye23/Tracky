package com.jvcs.tracky.designsystem.components.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.designsystem.components.MainNavDrawerItem
import com.jvcs.tracky.designsystem.components.MainNavigationDrawer
import com.jvcs.tracky.designsystem.components.MainNavigationRail
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.DeviceConfiguration
import com.jvcs.tracky.designsystem.util.PreviewDevices
import com.jvcs.tracky.designsystem.util.currentDeviceConfiguration

/**
 * Structural container for the app's top-level destinations.
 *
 * - Mobile portrait: a modal drawer, opened from the content's menu button.
 * - Every wider configuration: a compact navigation rail beside the content, with no drawer.
 *
 * [content] receives `showMenuButton`, the one place that says whether a drawer exists to open,
 * so screens never re-derive it from the window size. Owns no state beyond passing [drawerState].
 */
@Composable
fun TrackyAdaptiveNavigationLayout(
    drawerState: DrawerState,
    selectedItem: MainNavDrawerItem,
    modifier: Modifier = Modifier,
    onProjectsClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    content: @Composable (showMenuButton: Boolean) -> Unit,
) {
    // Movable so the screen keeps its scroll position and selection when a rotation or window
    // resize moves it between the drawer and the rail branch. The slot is read through updated
    // state: keying remember on it would rebuild the screen on every recomposition of the caller.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { showMenuButton: Boolean -> currentContent(showMenuButton) } }

    when (currentDeviceConfiguration()) {
        DeviceConfiguration.MOBILE_PORTRAIT -> {
            MainNavigationDrawer(
                modifier = modifier,
                drawerState = drawerState,
                selectedItem = selectedItem,
                onProjectsClick = onProjectsClick,
                onArchiveClick = onArchiveClick,
                onTrashClick = onTrashClick,
            ) {
                movableContent(true)
            }
        }

        DeviceConfiguration.MOBILE_LANDSCAPE,
        DeviceConfiguration.TABLET_PORTRAIT,
        DeviceConfiguration.TABLET_LANDSCAPE,
        DeviceConfiguration.DESKTOP,
        -> {
            Row(modifier = modifier.fillMaxSize()) {
                MainNavigationRail(
                    selectedItem = selectedItem,
                    onProjectsClick = onProjectsClick,
                    onArchiveClick = onArchiveClick,
                    onTrashClick = onTrashClick,
                )
                // The rail already pads for the start inset (cutout, 3-button nav bar); consume it so
                // the content's Scaffold does not pad for it a second time.
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)),
                ) {
                    movableContent(false)
                }
            }
        }
    }
}

@PreviewDevices
@Preview(name = "Desktop", widthDp = 1440, heightDp = 1024)
@Composable
private fun TrackyAdaptiveNavigationLayoutPreview() {
    TrackyTheme {
        TrackyAdaptiveNavigationLayout(
            drawerState = rememberDrawerState(DrawerValue.Closed),
            selectedItem = MainNavDrawerItem.PROJECTS,
        ) { showMenuButton ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = if (showMenuButton) "Content with menu button" else "Content beside the rail")
                }
            }
        }
    }
}
