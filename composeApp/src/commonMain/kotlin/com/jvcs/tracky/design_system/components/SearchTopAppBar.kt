package com.jvcs.tracky.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.design_system.Icon_Swap_Vert_Down
import com.jvcs.tracky.design_system.Icon_Swap_Vert_Up
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.SortOption
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.sort_by

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopAppBar(
    title: String? = null,
    isSearchBoxExpanded: Boolean = false,
    searchHint: String,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    sortOption: SortOption? = null,
    onToggleSortBottomSheet: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            if (isSearchBoxExpanded) {
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 8.dp)
                        .shadow(2.dp, CircleShape),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { onQueryChange(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = searchHint,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (sortOption != null) {
                            val isSortActive = sortOption != SortOption.CUSTOM
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSortActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable { onToggleSortBottomSheet() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icon_Swap_Vert_Up,
                                    contentDescription = stringResource(Res.string.sort_by),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icon_Swap_Vert_Down,
                                    contentDescription = stringResource(Res.string.sort_by),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSortActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            else {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = actions,
        modifier = modifier,
        navigationIcon = navigationIcon,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}