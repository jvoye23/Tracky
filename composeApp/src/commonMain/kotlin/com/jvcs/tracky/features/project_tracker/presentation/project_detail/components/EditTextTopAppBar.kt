package com.jvcs.tracky.features.project_tracker.presentation.project_detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.theme.TrackyTheme
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.save

import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTextTopAppBar(
    title: String,
    onCancelClick: () -> Unit,
    onSaveClick: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
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
                    color = contentColor
                )
            }
        },
        navigationIcon = {
            TextButton(
                onClick = { onCancelClick() },
                modifier = Modifier,
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.cancel),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        },
        actions = {
            TextButton(
                onClick = { onSaveClick() } ,
                modifier = Modifier,
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.save),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Green
                )
            }

        },
        colors = TopAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = contentColor,
            titleContentColor = contentColor,
            actionIconContentColor = contentColor
        )
    )
}


@OptIn(ExperimentalTime::class)
@Preview
@Composable
private fun TopAppBarPreview() {
    TrackyTheme {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            EditTextTopAppBar(
                title = "EDIT TASK",
                onCancelClick = {},
                onSaveClick = {},
            )
        }
    }
}