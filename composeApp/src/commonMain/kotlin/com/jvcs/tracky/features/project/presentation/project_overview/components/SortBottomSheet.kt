package com.jvcs.tracky.features.project.presentation.project_overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.Icon_Check
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.project_overview.ProjectOverviewAction
import com.jvcs.tracky.features.project.presentation.project_overview.SortOption
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.check_icon
import tracky.composeapp.generated.resources.sort_by
import tracky.composeapp.generated.resources.sort_creation_date
import tracky.composeapp.generated.resources.sort_custom
import tracky.composeapp.generated.resources.sort_modification_date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    modifier: Modifier = Modifier,
    sortOption: SortOption,
    onAction: (ProjectOverviewAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = { onAction(ProjectOverviewAction.OnToggleSortBottomSheet) },
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {}
    ) {
        SortSheetContent(
            selectedOption = sortOption,
            onOptionSelected = { option ->
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onAction(ProjectOverviewAction.OnSortOptionSelected(option))
                    }
                }
            }
        )
    }
}

@Composable
internal fun SortSheetContent(
    modifier: Modifier = Modifier,
    selectedOption: SortOption,
    onOptionSelected: (SortOption) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 8.dp)
        ) {
            Text(
                modifier = Modifier.padding(start = 20.dp, bottom = 20.dp),
                text = stringResource(Res.string.sort_by),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SortOption.entries.forEach { option ->
                SortOptionRow(
                    selected = option == selectedOption,
                    label = stringResource(option.labelRes),
                    onClick = { onOptionSelected(option) }
                )
            }
        }
    }
}

@Composable
private fun SortOptionRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                imageVector = Icon_Check,
                contentDescription = stringResource(Res.string.check_icon),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
        )
    }
}

private val SortOption.labelRes: StringResource
    get() = when (this) {
        SortOption.CUSTOM -> Res.string.sort_custom
        SortOption.CREATION_DATE -> Res.string.sort_creation_date
        SortOption.MODIFICATION_DATE -> Res.string.sort_modification_date
    }

@Preview(showSystemUi = true)
@Composable
private fun SortBottomSheetPreview() {
    TrackyTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            SortSheetContent(
                selectedOption = SortOption.CUSTOM,
                onOptionSelected = {}
            )
        }
    }
}
