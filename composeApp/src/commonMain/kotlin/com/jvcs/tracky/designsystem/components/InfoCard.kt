package com.jvcs.tracky.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.theme.TrackyTheme

@Composable
fun InfoCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.03f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

/** The pair layout every caller uses: two cards sharing a row, each taking half the width. */
@PreviewLightDark
@Composable
private fun InfoCardPairPreview() {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(
                    icon = Icons.Outlined.DateRange,
                    label = "Busiest day",
                    value = "Tue 08",
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    icon = Icons.Outlined.Schedule,
                    label = "This month",
                    value = "20:08",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Labels are uppercased and unwrapped, so a long one is the case that has to hold up. */
@Preview(name = "Font scale 2x", fontScale = 2f)
@Composable
private fun InfoCardFontScalePreview() {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoCard(
                    icon = Icons.Outlined.DateRange,
                    label = "Busiest day",
                    value = "Tue 08",
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    icon = Icons.Outlined.Schedule,
                    label = "This month",
                    value = "20:08",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
