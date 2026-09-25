package com.jvcs.tracky.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvcs.tracky.designsystem.Icon_Archive
import com.jvcs.tracky.designsystem.Icon_Delete
import com.jvcs.tracky.designsystem.Icon_Help
import com.jvcs.tracky.designsystem.Icon_Settings
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.drawer_about_feedback
import tracky.composeapp.generated.resources.drawer_archive
import tracky.composeapp.generated.resources.drawer_projects
import tracky.composeapp.generated.resources.drawer_reminders
import tracky.composeapp.generated.resources.drawer_settings
import tracky.composeapp.generated.resources.drawer_trash

enum class MainNavDrawerItem { PROJECTS, ARCHIVE, TRASH }

/** One destination, shared by the drawer and the rail so the two can never list different items. */
private data class MainNavEntry(
    val label: StringResource,
    val icon: ImageVector,
    val target: MainNavDrawerItem?,
    val dividerBefore: Boolean = false,
)

// The project's own icons are resolved in composition, so the list is built there too.
@Composable
private fun mainNavEntries(): List<MainNavEntry> =
    listOf(
        MainNavEntry(Res.string.drawer_projects, Icons.Outlined.FolderOpen, MainNavDrawerItem.PROJECTS),
        MainNavEntry(Res.string.drawer_reminders, Icons.Outlined.Notifications, target = null),
        MainNavEntry(Res.string.drawer_archive, Icon_Archive, MainNavDrawerItem.ARCHIVE, dividerBefore = true),
        MainNavEntry(Res.string.drawer_trash, Icon_Delete, MainNavDrawerItem.TRASH),
        MainNavEntry(Res.string.drawer_settings, Icon_Settings, target = null),
        MainNavEntry(Res.string.drawer_about_feedback, Icon_Help, target = null),
    )

@Composable
fun MainNavigationDrawer(
    drawerState: DrawerState,
    selectedItem: MainNavDrawerItem,
    modifier: Modifier = Modifier,
    onProjectsClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Wordmark(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                )

                mainNavEntries().forEach { entry ->
                    if (entry.dividerBefore) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                    }
                    NavigationDrawerItem(
                        label = { Text(text = stringResource(entry.label)) },
                        icon = { Icon(imageVector = entry.icon, contentDescription = null) },
                        selected = entry.target != null && entry.target == selectedItem,
                        onClick = {
                            scope.launch { drawerState.close() }
                            entry.navigate(onProjectsClick, onArchiveClick, onTrashClick)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
        content = content,
    )
}

/**
 * The compact rail used instead of [MainNavigationDrawer] once the window is wide enough: every
 * destination is always visible, icon above its label.
 */
@Composable
fun MainNavigationRail(
    selectedItem: MainNavDrawerItem,
    modifier: Modifier = Modifier,
    onProjectsClick: () -> Unit = {},
    onArchiveClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
    ) {
        // Six labelled items barely fit a landscape phone's height, so the rail scrolls rather than clips.
        // Fixed at the compact rail width; longer labels wrap instead of widening the rail.
        Column(
            modifier =
                Modifier
                    .width(80.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            mainNavEntries().forEach { entry ->
                if (entry.dividerBefore) {
                    HorizontalDivider(modifier = Modifier.width(40.dp).padding(vertical = 4.dp))
                }
                NavigationRailItem(
                    selected = entry.target != null && entry.target == selectedItem,
                    onClick = { entry.navigate(onProjectsClick, onArchiveClick, onTrashClick) },
                    icon = { Icon(imageVector = entry.icon, contentDescription = null) },
                    label = {
                        Text(
                            text = stringResource(entry.label),
                            // M3's 12sp rail label size; the theme's label styles are larger and
                            // would break single words like "Reminders" across two lines at 80dp.
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    },
                    alwaysShowLabel = true,
                )
            }
        }
    }
}

private fun MainNavEntry.navigate(
    onProjectsClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onTrashClick: () -> Unit,
) {
    when (target) {
        MainNavDrawerItem.PROJECTS -> onProjectsClick()
        MainNavDrawerItem.ARCHIVE -> onArchiveClick()
        MainNavDrawerItem.TRASH -> onTrashClick()
        null -> Unit
    }
}

@Preview
@Composable
private fun MainNavigationDrawerPreview() {
    TrackyTheme {
        MainNavigationDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            selectedItem = MainNavDrawerItem.PROJECTS,
        ) {
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}

@Preview(heightDp = 600)
@Composable
private fun MainNavigationRailPreview() {
    TrackyTheme {
        MainNavigationRail(selectedItem = MainNavDrawerItem.PROJECTS)
    }
}
