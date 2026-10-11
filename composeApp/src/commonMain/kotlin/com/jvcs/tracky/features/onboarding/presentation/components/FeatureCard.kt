package com.jvcs.tracky.features.onboarding.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.onboarding.presentation.model.listOfColors

/**
 * One feature: an icon on a light tint of its accent colour, a title and a sentence. The accent also draws the border.
 * Informational only, so not clickable.
 */
@Composable
internal fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, accentColor),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A light wash of the accent rather than the solid colour, so it reads on light and dark surfaces.
            Surface(shape = CircleShape, color = accentColor.copy(alpha = 0.18f)) {
                // Decorative: the title next to it says what the feature is.
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun FeatureCardPreview() {
    TrackyTheme {
        FeatureCard(
            icon = Icons.Outlined.CloudSync,
            title = "Cloud sync",
            description = "Sync all your project data with the cloud server in real time.",
            accentColor = listOfColors[0],
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Long text · Large font", fontScale = 2f)
@Composable
private fun FeatureCardLongTextPreview() {
    TrackyTheme {
        FeatureCard(
            icon = Icons.Outlined.CloudSync,
            title = "Tracking across all of your devices at the same time",
            description = "Sync your timers on up to 3 devices in real time, so a timer started on one stops on all.",
            accentColor = listOfColors[3],
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun FeatureCardLightAccentPreview() {
    TrackyTheme {
        FeatureCard(
            icon = Icons.Outlined.CloudSync,
            title = "Cloud sync",
            description = "Sync all your project data with the cloud server in real time.",
            accentColor = listOfColors[6],
            modifier = Modifier.padding(16.dp),
        )
    }
}
