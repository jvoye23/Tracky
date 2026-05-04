package com.jvcs.tracky.features.project_tracker.presentation.project_detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.core.presentation.model.ProjectSessionUi
import com.jvcs.tracky.core.presentation.model.ProjectUi
import com.jvcs.tracky.design_system.Icon_ChevronRight
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.project_tracker.domain.EditTextType
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.components.AddNewProjectSessionBottomSheet
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.components.TrackyColorPicker
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.description
import tracky.composeapp.generated.resources.title

@Composable
fun ProjectDetailScreenRoot(
    navigateBack: () -> Unit,
    onEditTextClick: (String?, EditTextType) -> Unit,
    onProjectSessionClick: (String) -> Unit,
    viewModel: ProjectDetailViewModel = koinViewModel ()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NewProjectDetailScreen(
        state = state,
        onAction = { action ->
            when(action) {
                ProjectDetailAction.OnBackClick -> navigateBack()
                is ProjectDetailAction.OnEditTextClick -> onEditTextClick(action.text, action.editTextType)
                is ProjectDetailAction.OnProjectSessionCardClick -> onProjectSessionClick(action.projectSessionId)
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDetailScreen(
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit
) {
    val project = state.project

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (state.isEditMode) "EDIT PROJECT" else "PROJECT DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isEditMode) onAction(ProjectDetailAction.OnCloseAndCancelClick)
                        else onAction(ProjectDetailAction.OnBackClick)
                    }) {
                        Icon(
                            if (state.isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (state.isEditMode) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (state.isEditMode) onAction(ProjectDetailAction.OnSaveClick)
                        else onAction(ProjectDetailAction.OnEditModeClick)
                    }) {
                        Icon(
                            if (state.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Action"
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFFFDFBFF)
    ) { paddingValues ->
        if (project == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Header
                item {
                    ProjectHeader(
                        title = state.titleText ?: stringResource(Res.string.title),
                        description = state.descriptionText ?: stringResource(Res.string.description),
                        onAction = onAction,
                        state = state
                    )
                }

                // 2. Info Grid
                item {
                    InfoGrid(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        startDate = state.project.startDateTimeUtc,
                        lastActive = state.project.startDateTimeUtc,
                        sessionCount = state.project.projectSessions?.size ?: 0,
                        color = state.selectedColor ?: Color.Cyan,
                        state = state,
                        onAction = onAction
                    )
                }

                // 3. Hero Card (Project-wide Timer)
                item {
                    DurationHeroCard(
                        modifier = Modifier
                            .padding(bottom = 16.dp),
                        totalDuration = state.project.totalProjectDuration ?: "00:00:00:00",
                        projectColor = state.selectedColor ?: MaterialTheme.colorScheme.primary,
                        useLightTextColor = state.useLightTextColor,
                        onStartStopClick = {
                            // Logic for project-wide tracker if needed
                            onAction(ProjectDetailAction.OnStartTrackerClick)
                        }
                    )
                }

                if (state.isEditMode) {
                    item {
                        TextColorToggle(
                            useLightTextColor = state.useLightTextColor,
                            onToggle = { onAction(ProjectDetailAction.OnUseLightTextColorToggled(it)) }
                        )
                    }
                }

                // 4. Sessions Header
                item {
                    SessionsHeader(onAddClick = {
                        onAction(ProjectDetailAction.OnToggleAddNewProjectSessionBottomSheet)
                    })
                }

                // 5. Session Items
                itemsIndexed(project.projectSessions ?: emptyList()) { index, session ->
                    SessionItem(
                        index = index + 1,
                        session = session,
                        projectColor = state.selectedColor ?: MaterialTheme.colorScheme.primary,
                        isEditMode = state.isEditMode,
                        onToggleTimer = {
                            session.id?.let { onAction(ProjectDetailAction.OnToggleSessionTimer(it)) }
                        },
                        onDeleteClick = {
                            session.id?.let { onAction(ProjectDetailAction.OnDeleteSessionClick(it)) }
                        },
                        onCardClick = {
                            session.id?.let { onAction(ProjectDetailAction.OnProjectSessionCardClick(it)) }
                        }
                    )
                }
            }
        }
    }
    if (state.isAddNewProjectSessionBottomSheetVisible) {
        AddNewProjectSessionBottomSheet(
            state = state,
            onAction = onAction
        )
    }
    if (state.isColorPickerVisible) {
        TrackyColorPicker(
            currentColor = state.selectedColor ?: Color.Cyan,
            onCancel = { onAction(ProjectDetailAction.OnToggleColorPicker) },
            onSave = { onAction(ProjectDetailAction.OnColorChanged(it)) }
        )
    }
}

// --- Private Components ---

@Composable
private fun ProjectHeader(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    onAction: (ProjectDetailAction) -> Unit,
    state: ProjectDetailState,
) {
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (state.isEditMode) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
                .padding(vertical = 4.dp)
                .clickable {
                    if (state.isEditMode) onAction(
                        ProjectDetailAction.OnEditTextClick(
                            text = state.titleText,
                            editTextType = EditTextType.TITLE

                        )
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                modifier = Modifier
                    .weight(1f),
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                modifier = Modifier
                    .size(20.dp),
                imageVector = Icon_ChevronRight,
                contentDescription = null,
                tint = if (state.isEditMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
            )
        }
        //Description
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (state.isEditMode) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
                .padding(vertical = 4.dp)
                .clickable {
                    if (state.isEditMode) onAction(
                        ProjectDetailAction.OnEditTextClick(
                            text = state.descriptionText,
                            editTextType = EditTextType.DESCRIPTION
                        )
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                modifier = Modifier
                    .weight(1f),
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                modifier = Modifier
                    .size(20.dp),
                imageVector = Icon_ChevronRight,
                contentDescription = null,
                tint = if (state.isEditMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0f),
            )
        }
    }
}

@Composable
private fun InfoGrid(
    modifier: Modifier = Modifier,
    startDate: String,
    lastActive: String,
    sessionCount: Int,
    color: Color,
    state: ProjectDetailState,
    onAction: (ProjectDetailAction) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f), Icons.Outlined.DateRange, "Started", startDate)
            InfoCard(Modifier.weight(1f), Icons.Outlined.History, "Last Active", lastActive)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(Modifier.weight(1f), Icons.Outlined.GridView, "Sessions", "$sessionCount Total")
            ColorInfoCard(
                modifier = Modifier.weight(1f),
                label = "Project Color",
                colorValue = color,
                hexCode = state.selectedColorHex,
                isEditMode = state.isEditMode,
                onClick = { onAction(ProjectDetailAction.OnToggleColorPicker) }
            )
        }
    }
}

@Composable
private fun InfoCard(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF0F3FA),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.03f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.width(6.dp))
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColorInfoCard(
    modifier: Modifier,
    label: String,
    colorValue: Color,
    hexCode: String,
    isEditMode: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = isEditMode) { onClick() },
        color = if (isEditMode) MaterialTheme.colorScheme.surfaceContainerLow else Color(0xFFF0F3FA),
        shape = RoundedCornerShape(16.dp),
        border = if (isEditMode) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Palette,
                        null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colorValue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        hexCode,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (isEditMode) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icon_ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TextColorToggle(
    modifier: Modifier = Modifier,
    useLightTextColor: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Light Project Textcolor",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = useLightTextColor,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        }
    }
}

@Composable
private fun DurationHeroCard(
    modifier: Modifier = Modifier,
    totalDuration: String,
    projectColor: Color,
    useLightTextColor: Boolean,
    onStartStopClick: () -> Unit
) {
    val contentColor = if (useLightTextColor) Color.White else Color.Black

    Surface(
        modifier = modifier.fillMaxWidth().clickable { onStartStopClick() },
        color = projectColor,
        shape = RoundedCornerShape(40.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "TOTAL DURATION",
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = totalDuration,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = contentColor.copy(alpha = 0.2f),
                shape = CircleShape
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(12.dp), tint = contentColor)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PROJECT TRACKER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun SessionsHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Sessions", style = MaterialTheme.typography.headlineMedium)
        FilledIconButton(
            onClick = onAddClick,
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Session")
        }
    }
}

@Composable
private fun SessionItem(
    index: Int,
    session: ProjectSessionUi,
    projectColor: Color,
    isEditMode: Boolean,
    onToggleTimer: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit
) {
    val isCompleted = session.formattedEndDateTimeUtc.isNotBlank()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(
            1.dp,
            if (session.isTimerRunning) projectColor.copy(0.2f) else Color.Transparent
        ),
        shadowElevation = if (session.isTimerRunning) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (session.isTimerRunning) projectColor.copy(alpha = pulseAlpha)
                        else Color(0xFFF0F3FA)
                    )
                    .clickable { onToggleTimer() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (session.isTimerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (session.isTimerRunning) Color.White else projectColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val textDecoration = if (isCompleted) TextDecoration.LineThrough else null
                    val alpha = if (isCompleted) 0.4f else 1f

                    Text(
                        text = index.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (session.isTimerRunning) projectColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = alpha),
                        textDecoration = textDecoration
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        textDecoration = textDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = session.formattedDuration,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (session.isTimerRunning) projectColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isCompleted) 0.5f else 1f),
                    letterSpacing = (-1).sp
                )
            }

            // Status Indicator Icon / Delete Icon
            if (isEditMode) {
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Checkbox(
                    checked = false,
                    onCheckedChange = {

                    }
                )
            }
        }
    }
}

@Preview(device = Devices.PIXEL_9_PRO)
@Composable
private fun NewProjectDetailScreenPreview() {
    TrackyTheme {
        NewProjectDetailScreen(
            onAction = {},
            state = ProjectDetailState(
                project = ProjectUi(
                    projectId = "1",
                    title = "Project One",
                    description = "Description 1",
                    color = Color.Red,
                    totalDuration = "10:00:00",
                    startDateTimeUtc = "2023-01-01T00:00:00Z",
                    isFinished = false,
                    endDateTimeUtc = null,
                    projectSessions = projectSessionsPreview
                ),
                isEditMode = true
            )
        )
    }
}

private val projectSessionsPreview = listOf(
    ProjectSessionUi(
        id = "1",
        title = "This is session One",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    ),
    ProjectSessionUi(
        id = "2",
        title = "This is session Two",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false

    ),
    ProjectSessionUi(
        id = "3",
        title = "This is session Three",
        formattedDuration = "10:00:00",
        formattedStateDateTime = "2023-01-01T00:00:00Z",
        formattedEndDateTimeUtc = "2023-01-01T00:00:00Z",
        isTimerRunning = false
    )
)