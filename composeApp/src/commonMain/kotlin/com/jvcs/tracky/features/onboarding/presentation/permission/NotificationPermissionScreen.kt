package com.jvcs.tracky.features.onboarding.presentation.permission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.designsystem.components.TrackyPrimaryButton
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.onboarding_permission_allow
import tracky.composeapp.generated.resources.onboarding_permission_description
import tracky.composeapp.generated.resources.onboarding_permission_skip
import tracky.composeapp.generated.resources.onboarding_permission_title

@Composable
fun NotificationPermissionScreenRoot(
    onFinish: () -> Unit,
    viewModel: NotificationPermissionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotificationPermissionEvent.Finished -> onFinish()
        }
    }

    NotificationPermissionScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun NotificationPermissionScreen(
    state: NotificationPermissionState,
    onAction: (NotificationPermissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        // Scrolls rather than clips when a large font pushes the buttons off screen.
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(20.dp).size(40.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.onboarding_permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.onboarding_permission_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TrackyPrimaryButton(
                    text = stringResource(Res.string.onboarding_permission_allow),
                    onClick = { onAction(NotificationPermissionAction.OnAllowClick) },
                    isLoading = state.isRequesting,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onAction(NotificationPermissionAction.OnSkipClick) },
                    enabled = !state.isRequesting,
                ) {
                    Text(stringResource(Res.string.onboarding_permission_skip))
                }
            }
        }
    }
}

@Suppress("ScreenStateOnlyInScreenComposable") // preview wrapper: hands the whole state to the Screen
@Composable
private fun ScreenPreview(state: NotificationPermissionState) {
    TrackyTheme {
        NotificationPermissionScreen(state = state, onAction = {})
    }
}

@PreviewLightDark
@Composable
private fun NotificationPermissionScreenPreview() = ScreenPreview(NotificationPermissionState())

@PreviewLightDark
@Composable
private fun NotificationPermissionScreenRequestingPreview() =
    ScreenPreview(NotificationPermissionState(isRequesting = true))

@Preview(name = "Tablet", widthDp = 840)
@Composable
private fun NotificationPermissionScreenTabletPreview() = ScreenPreview(NotificationPermissionState())

@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun NotificationPermissionScreenLargeFontPreview() = ScreenPreview(NotificationPermissionState())
