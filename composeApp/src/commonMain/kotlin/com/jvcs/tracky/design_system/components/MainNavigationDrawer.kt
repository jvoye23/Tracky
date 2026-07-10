package com.jvcs.tracky.design_system.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.Icon_Archive
import com.jvcs.tracky.design_system.Icon_Delete
import com.jvcs.tracky.design_system.Icon_Help
import com.jvcs.tracky.design_system.Icon_Settings
import com.jvcs.tracky.design_system.theme.TrackyTheme
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
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Wordmark(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                )

                DrawerItem(
                    label = Res.string.drawer_projects,
                    icon = Icons.Outlined.FolderOpen,
                    selected = selectedItem == MainNavDrawerItem.PROJECTS,
                    onClick = { closeDrawer(); onProjectsClick() }
                )
                DrawerItem(
                    label = Res.string.drawer_reminders,
                    icon = Icons.Outlined.Notifications,
                    selected = false,
                    onClick = closeDrawer
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))

                DrawerItem(
                    label = Res.string.drawer_archive,
                    icon = Icon_Archive,
                    selected = selectedItem == MainNavDrawerItem.ARCHIVE,
                    onClick = { closeDrawer(); onArchiveClick() }
                )
                DrawerItem(
                    label = Res.string.drawer_trash,
                    icon = Icon_Delete,
                    selected = selectedItem == MainNavDrawerItem.TRASH,
                    onClick = { closeDrawer(); onTrashClick() }
                )
                DrawerItem(
                    label = Res.string.drawer_settings,
                    icon = Icon_Settings,
                    selected = false,
                    onClick = closeDrawer
                )
                DrawerItem(
                    label = Res.string.drawer_about_feedback,
                    icon = Icon_Help,
                    selected = false,
                    onClick = closeDrawer
                )
            }
        },
        content = content
    )
}

@Composable
private fun DrawerItem(
    label: StringResource,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(text = stringResource(label)) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Preview
@Composable
private fun MainNavigationDrawerPreview() {
    TrackyTheme {
        MainNavigationDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            selectedItem = MainNavDrawerItem.PROJECTS
        ) {
            Spacer(modifier = Modifier.height(0.dp))
        }
    }
}
