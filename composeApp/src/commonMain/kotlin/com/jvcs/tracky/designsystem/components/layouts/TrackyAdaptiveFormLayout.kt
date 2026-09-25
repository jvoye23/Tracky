package com.jvcs.tracky.designsystem.components.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.components.TrackyPrimaryButton
import com.jvcs.tracky.designsystem.components.Wordmark
import com.jvcs.tracky.designsystem.components.WordmarkSize
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.DeviceConfiguration
import com.jvcs.tracky.designsystem.util.PreviewDevices
import com.jvcs.tracky.designsystem.util.currentDeviceConfiguration

/**
 * Structural container for auth-style form screens.
 *
 * - Mobile portrait: [header] stacked above [formContent] in one scrolling column.
 * - Mobile landscape: two columns, [header] on the left and a scrolling [formContent] on the right.
 * - Tablet and desktop: both slots in a centered, rounded card that wraps its content instead of
 *   stretching across the window.
 *
 * Owns no state and no window insets; the calling screen's Scaffold handles those.
 */
@Composable
fun TrackyAdaptiveFormLayout(
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    formContent: @Composable ColumnScope.() -> Unit,
) {
    // Movable so the slots keep their state (focus, scroll, IME) when a rotation or window
    // resize moves them into a different branch below.
    // The slot lambdas are read through updated state: keying remember on them would rebuild the
    // subtree whenever the caller's state changes, dropping text field focus mid-typing.
    val currentHeader by rememberUpdatedState(header)
    val currentFormContent by rememberUpdatedState(formContent)
    val movableHeader = remember { movableContentWithReceiverOf<ColumnScope> { currentHeader() } }
    val movableFormContent = remember { movableContentWithReceiverOf<ColumnScope> { currentFormContent() } }

    when (currentDeviceConfiguration()) {
        DeviceConfiguration.MOBILE_PORTRAIT -> {
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                movableHeader()
                movableFormContent()
            }
        }

        DeviceConfiguration.MOBILE_LANDSCAPE -> {
            Row(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                LandscapePane(modifier = Modifier.weight(1f), content = movableHeader)
                LandscapePane(modifier = Modifier.weight(1f), content = movableFormContent)
            }
        }

        DeviceConfiguration.TABLET_PORTRAIT,
        DeviceConfiguration.TABLET_LANDSCAPE,
        DeviceConfiguration.DESKTOP,
        -> {
            Box(
                modifier =
                    modifier
                        .fillMaxSize()
                        .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 32.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    movableHeader()
                    movableFormContent()
                }
            }
        }
    }
}

/** Centers [content] vertically, scrolling it only when it outgrows the short landscape height. */
@Composable
private fun LandscapePane(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@PreviewDevices
@Preview(name = "Desktop", widthDp = 1440, heightDp = 1024)
@Composable
private fun TrackyAdaptiveFormLayoutPreview() {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TrackyAdaptiveFormLayout(
                header = {
                    Wordmark(size = WordmarkSize.Lg)
                    Text(
                        text = "Header",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                },
                formContent = {
                    Text(text = "Form content", modifier = Modifier.padding(vertical = 24.dp))
                    TrackyPrimaryButton(text = "Submit", onClick = {}, modifier = Modifier.fillMaxWidth())
                },
            )
        }
    }
}
