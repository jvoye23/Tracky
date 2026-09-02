package com.jvcs.tracky.features.project.presentation.project_detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.theme.TrackyTheme
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.back
import tracky.composeapp.generated.resources.edit_uppercase

import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTextTopAppBar(
    title: String,
    isEditMode: Boolean,
    onCancelClick: () -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    projectColor: Color = MaterialTheme.colorScheme.primary
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = projectColor
                )
            }
        },
        navigationIcon = {
            IconButton(
                onClick = { onCancelClick() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = projectColor.copy(alpha = 0.12f),
                    contentColor = projectColor
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                )
            }
        },
        actions = {
            if (isEditMode) {
                IconButton(
                    onClick = { onSaveClick() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = projectColor.copy(alpha = 0.12f),
                        contentColor = projectColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.edit_uppercase),
                    )
                }
            } else {
                IconButton(
                    onClick = { onEditClick() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = projectColor.copy(alpha = 0.12f),
                        contentColor = projectColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.edit_uppercase),
                        tint = projectColor
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
        )
    )
}


private val PreviewEditTextProjectColor = Color(0xFF4CAF50)

@OptIn(ExperimentalTime::class)
@Preview
@Composable
private fun EditTextTopAppBarEditModePreview() {
    TrackyTheme {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            EditTextTopAppBar(
                title = "EDIT PROJECT",
                isEditMode = true,
                onCancelClick = {},
                onEditClick = {},
                onSaveClick = {},
                projectColor = PreviewEditTextProjectColor
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
@Preview
@Composable
private fun EditTextTopAppBarReadModePreview() {
    TrackyTheme {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            EditTextTopAppBar(
                title = "PROJECT DETAILS",
                isEditMode = false,
                onCancelClick = {},
                onEditClick = {},
                onSaveClick = {},
                projectColor = PreviewEditTextProjectColor
            )
        }
    }
}
