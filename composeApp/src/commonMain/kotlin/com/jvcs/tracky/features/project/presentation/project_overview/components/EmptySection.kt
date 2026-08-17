package com.jvcs.tracky.features.project.presentation.project_overview.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.features.project.presentation.util.DeviceConfiguration
import com.jvcs.tracky.features.project.presentation.util.currentDeviceConfiguration
import org.jetbrains.compose.resources.painterResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.tracky_icon

@Composable
fun EmptySection(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val configuration = currentDeviceConfiguration()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.tracky_icon),
            contentDescription = title,
            modifier = Modifier.size(
                if(configuration == DeviceConfiguration.MOBILE_LANDSCAPE) {
                    100.dp
                } else {
                    200.dp
                }
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}