package com.jvcs.tracky.features.project.presentation.edit_text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.components.TrackyTextField
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.theme.projectElevatedLabelStyle
import com.jvcs.tracky.design_system.theme.projectLabelStyle
import com.jvcs.tracky.design_system.util.DevicePreviews
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import com.jvcs.tracky.design_system.util.UiText
import com.jvcs.tracky.features.project.presentation.project_detail.components.ProjectSubDetailTopAppBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.edit_project_uppercase
import tracky.composeapp.generated.resources.project_details_uppercase
import tracky.composeapp.generated.resources.project_info_saved
import tracky.composeapp.generated.resources.title
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EditTextScreenRoot(
    onNavigateBack: () -> Unit,
    viewModel: EditTextViewModel = koinViewModel()
) {

    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is EditTextEvent.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.error.asStringAsync(),
                        duration = SnackbarDuration.Short
                    )
                }
            }
            is EditTextEvent.OnSavedSuccess -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = UiText.Resource(Res.string.project_info_saved).asStringAsync(),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    EditTextScreen(
        onNavigateBack = onNavigateBack,
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = { action ->
            when (action) {
                EditTextAction.OnBackClick -> onNavigateBack()
                else -> Unit
            }
            viewModel.onAction(action)

        }
    )
}

@Composable
private fun EditTextScreen(
    onNavigateBack: () -> Unit,
    state: EditTextState,
    snackbarHostState: SnackbarHostState,
    onAction: (EditTextAction) -> Unit
) {

    val focusRequester = remember { FocusRequester() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ProjectSubDetailTopAppBar(
                isEditMode = state.isEditMode,
                onNavigateBack = onNavigateBack,
                onEditClick = { onAction(EditTextAction.OnEditClick) },
                onSaveClick = { onAction(EditTextAction.OnSaveClick) },
                title = if (state.isEditMode) stringResource(Res.string.edit_project_uppercase)
                else stringResource(Res.string.project_details_uppercase),
                // A project without a colour of its own falls back to the theme accent.
                projectColor = state.projectColor ?: MaterialTheme.colorScheme.primary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackyTextField(
                state = state.titleState,
                label = stringResource(Res.string.title),
                modifier = Modifier
                    .fillMaxWidth()
                .focusRequester(focusRequester),
                labelStyle = MaterialTheme.typography.projectLabelStyle,
                elevatedLabelStyle = MaterialTheme.typography.projectElevatedLabelStyle,
                textStyle = MaterialTheme.typography.headlineLarge,
                lineLimits = TextFieldLineLimits.SingleLine,
                enabled = state.isEditMode,
                showLabel = true,
                backgroundDefaultColor = MaterialTheme.colorScheme.surface,
                borderDefaultColor = MaterialTheme.colorScheme.surface,
                borderIsFocusedColor = MaterialTheme.colorScheme.surface
            )
            TrackyTextField(
                state = state.descriptionState,
                label = stringResource(Res.string.description),
                modifier = Modifier
                    .fillMaxWidth(),
                labelStyle = MaterialTheme.typography.projectLabelStyle,
                elevatedLabelStyle = MaterialTheme.typography.projectElevatedLabelStyle,
                textStyle = MaterialTheme.typography.bodyMedium,
                lineLimits = TextFieldLineLimits.Default,
                enabled = state.isEditMode,
                showLabel = true,
                backgroundDefaultColor = MaterialTheme.colorScheme.surface,
                borderDefaultColor = MaterialTheme.colorScheme.surface,
                borderIsFocusedColor = MaterialTheme.colorScheme.surface
            )
        }
        LaunchedEffect(state.isEditMode) {
            if (state.isEditMode) {
                // A small delay to ensure the UI is fully drawn before the focus is requested
                delay(100.milliseconds)
                focusRequester.requestFocus()
            }
        }
    }
}

private const val PREVIEW_TITLE = "Tracky Redesign"
private const val PREVIEW_DESCRIPTION =
    "Rework the project detail screen so title and description share one continuous document. " +
        "Ship the new top bar together with it."

private val previewProjectColor = Color(0xFF7B61FF)

@Composable
private fun previewState(
    title: String = PREVIEW_TITLE,
    description: String = PREVIEW_DESCRIPTION,
    isEditMode: Boolean = false,
    projectColor: Color? = previewProjectColor
) = EditTextState(
    titleState = rememberTextFieldState(title),
    descriptionState = rememberTextFieldState(description),
    isEditMode = isEditMode,
    projectColor = projectColor
)

@Composable
private fun EditTextScreenPreviewContainer(
    state: EditTextState,
    darkTheme: Boolean = false
) {
    TrackyTheme(darkTheme = darkTheme) {
        EditTextScreen(
            onNavigateBack = {},
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {}
        )
    }
}

@Preview(name = "View mode · Light", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenViewModeLightPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(isEditMode = false)
    )
}

@Preview(name = "View mode · Dark", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenViewModeDarkPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(isEditMode = false),
        darkTheme = true
    )
}

@Preview(name = "Edit mode · Light", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenEditModeLightPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(isEditMode = true)
    )
}

@Preview(name = "Edit mode · Dark", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenEditModeDarkPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(isEditMode = true),
        darkTheme = true
    )
}

// Empty fields: both labels sit un-elevated and the hints are the only text on screen.
@Preview(name = "Edit mode · Empty", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenEmptyPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(
            title = "",
            description = "",
            isEditMode = true
        )
    )
}

@Preview(name = "Long content", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenLongContentPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(
            title = "Rebuild the project detail and edit screens on the new design system",
            description = "Move every field onto TrackyTextField so the label elevation, hint and " +
                "disabled styling come from one place.\n\nThen delete the old OutlinedTextField " +
                "variants and align the top bar tint with the project colour.",
            isEditMode = false
        )
    )
}

// projectColor = null falls back to the theme accent in the top bar.
@Preview(name = "No project color", device = Devices.PIXEL_9_PRO)
@Composable
private fun EditTextScreenDefaultColorPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(projectColor = null)
    )
}

@Preview(name = "Font scale 2x", device = Devices.PIXEL_9_PRO, fontScale = 2f)
@Composable
private fun EditTextScreenLargeFontPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(isEditMode = false)
    )
}

@DevicePreviews
@Composable
private fun EditTextScreenDevicesPreview() {
    EditTextScreenPreviewContainer(
        state = previewState(isEditMode = false)
    )
}
