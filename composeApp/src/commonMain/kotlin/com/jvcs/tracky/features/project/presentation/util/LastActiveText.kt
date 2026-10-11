package com.jvcs.tracky.features.project.presentation.util

import androidx.compose.runtime.Composable
import com.jvcs.tracky.features.project.presentation.models.ProjectUi
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.last_active_never
import tracky.composeapp.generated.resources.last_active_today

/**
 * What a "Last Active" field shows for this project.
 *
 * A running timer reads as today: its interval is still open, so [ProjectUi.lastActiveDate] cannot
 * include it yet. anyTimerRunning is kept live by the ticker overlay on both the overview and the
 * detail screen, so this needs no clock of its own.
 */
@Composable
fun lastActiveText(project: ProjectUi): String =
    when {
        project.anyTimerRunning -> stringResource(Res.string.last_active_today)
        else -> project.lastActiveDate ?: stringResource(Res.string.last_active_never)
    }
