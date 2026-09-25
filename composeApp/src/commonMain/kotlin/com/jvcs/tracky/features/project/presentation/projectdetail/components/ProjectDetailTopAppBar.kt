package com.jvcs.tracky.features.project.presentation.projectdetail.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.designsystem.Icon_File_Export
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.projectdetail.ExportFormat
import com.jvcs.tracky.features.project.presentation.projectdetail.ProjectDetailAction
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cd_export_project
import tracky.composeapp.generated.resources.daily_overview_title
import tracky.composeapp.generated.resources.edit
import tracky.composeapp.generated.resources.export_json
import tracky.composeapp.generated.resources.export_pdf
import tracky.composeapp.generated.resources.save

/** Sentinel for "open on today", matching the route's default. */
private const val OPEN_ON_TODAY = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectDetailTopAppBar(
    isEditMode: Boolean,
    headerColor: Color,
    isExportMenuExpanded: Boolean,
    onAction: (ProjectDetailAction) -> Unit,
    onExportClick: () -> Unit,
    onExportMenuDismiss: () -> Unit,
    onExportFormatClick: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                if (isEditMode) "EDIT PROJECT" else "PROJECT DETAILS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                if (isEditMode) {
                    onAction(ProjectDetailAction.OnCloseAndCancelClick)
                } else {
                    onAction(ProjectDetailAction.OnBackClick)
                }
            }) {
                Icon(
                    if (isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isEditMode) "Cancel" else "Back",
                )
            }
        },
        actions = {
            // Hidden in edit mode: leaving the screen mid-edit would drop the changes.
            if (!isEditMode) {
                IconButton(onClick = {
                    onAction(ProjectDetailAction.OnDailyOverviewClick(OPEN_ON_TODAY))
                }) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(Res.string.daily_overview_title),
                    )
                }
                ExportMenuButton(
                    isExpanded = isExportMenuExpanded,
                    onClick = onExportClick,
                    onDismiss = onExportMenuDismiss,
                    onFormatClick = onExportFormatClick,
                )
            }
            IconButton(onClick = {
                if (isEditMode) {
                    onAction(ProjectDetailAction.OnSaveClick)
                } else {
                    onAction(ProjectDetailAction.OnEditModeClick)
                }
            }) {
                Icon(
                    if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription =
                        stringResource(
                            if (isEditMode) Res.string.save else Res.string.edit,
                        ),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = headerColor,
                scrolledContainerColor = headerColor,
            ),
        scrollBehavior = scrollBehavior,
    )
}

/** The export icon and the format menu it anchors. */
@Composable
private fun ExportMenuButton(
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    onFormatClick: (ExportFormat) -> Unit,
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icon_File_Export,
                contentDescription = stringResource(Res.string.cd_export_project),
            )
        }
        DropdownMenu(expanded = isExpanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.export_pdf)) },
                onClick = { onFormatClick(ExportFormat.Pdf) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.export_json)) },
                onClick = { onFormatClick(ExportFormat.Json) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBarPreview(
    darkTheme: Boolean,
    isEditMode: Boolean = false,
    isExportMenuExpanded: Boolean = false,
) {
    TrackyTheme(darkTheme = darkTheme) {
        ProjectDetailTopAppBar(
            isEditMode = isEditMode,
            headerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            isExportMenuExpanded = isExportMenuExpanded,
            onAction = {},
            onExportClick = {},
            onExportMenuDismiss = {},
            onExportFormatClick = {},
        )
    }
}

@Preview(name = "Menu closed · Light")
@Composable
private fun MenuClosedLightPreview() = TopAppBarPreview(darkTheme = false)

@Preview(name = "Menu closed · Dark")
@Composable
private fun MenuClosedDarkPreview() = TopAppBarPreview(darkTheme = true)

@Preview(name = "Menu open · Light", heightDp = 200)
@Composable
private fun MenuOpenLightPreview() = TopAppBarPreview(darkTheme = false, isExportMenuExpanded = true)

@Preview(name = "Menu open · Dark", heightDp = 200)
@Composable
private fun MenuOpenDarkPreview() = TopAppBarPreview(darkTheme = true, isExportMenuExpanded = true)

@Preview(name = "Edit mode · Light")
@Composable
private fun EditModeLightPreview() = TopAppBarPreview(darkTheme = false, isEditMode = true)

@Preview(name = "Edit mode · Dark")
@Composable
private fun EditModeDarkPreview() = TopAppBarPreview(darkTheme = true, isEditMode = true)
