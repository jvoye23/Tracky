package com.jvcs.tracky.features.project.presentation.projectdetail

import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.presentation.mappers.toProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The finished-flag rules of the project detail screen: a finished row stops counting, and a task
 * with subtasks is finished exactly when all of them are.
 *
 * Split out of [ProjectDetailViewModel], which still owns the [state] this patches and the [scope]
 * it runs in. [onTimeBanked] is called whenever finishing stopped a timer, so the caller can rebuild
 * anything derived from banked time.
 */
internal class TaskCompletion(
    private val state: MutableStateFlow<ProjectDetailState>,
    private val scope: CoroutineScope,
    private val projectTaskRepository: ProjectTaskRepository,
    private val subTaskRepository: SubTaskRepository,
    private val timeProvider: TimeProvider,
    private val onTimeBanked: suspend () -> Unit,
) {

    /**
     * Flips the subtask's finished flag. Finishing also stops a running timer — a done subtask
     * must not keep counting — which the repository turns into a closed interval.
     *
     * There is no task-level equivalent yet: `OnCheckedChange` for a task is still an unwired stub
     * at the call site, so finishing currently works on subtasks only.
     */
    fun onSubTaskCheckedChange(subTaskId: String) {
        val parentTaskId =
            state.value.project
                ?.projectTasks
                ?.find { task -> task.subTasks.any { it.projectSubTaskId == subTaskId } }
                ?.projectTaskId ?: return

        scope.launch {
            val subTask =
                subTaskRepository
                    .getSubTasksForTask(parentTaskId)
                    .first()
                    .find { it.projectSubTaskId == subTaskId } ?: return@launch

            val nowFinished = !subTask.isFinished
            val stopped =
                if (nowFinished && subTask.isTimerRunning) {
                    subTaskRepository.stopSubTask(subTaskId)
                    // Re-read: stopSubTask banks the elapsed time into the row, so copying the
                    // pre-stop snapshot would write the old duration straight back over it.
                    subTaskRepository
                        .getSubTasksForTask(parentTaskId)
                        .first()
                        .find { it.projectSubTaskId == subTaskId } ?: return@launch
                } else {
                    subTask
                }

            val updatedSubTask =
                stopped.copy(
                    isFinished = nowFinished,
                    endDateTimeUtc = if (nowFinished) timeProvider.nowInstant else null,
                    ownUpdatedAt = timeProvider.nowInstant,
                )
            subTaskRepository.upsertSubTask(updatedSubTask)

            // The screen loads its project once (getProject is a suspend read, not a Flow), so
            // nothing would refresh this row until the user leaves and comes back. Only the fields
            // the flip owns are patched: formattedDuration is deliberately left alone, because
            // `subTask` was read before stopSubTask banked the elapsed time, while state already
            // holds the last ticked value.
            val updatedUi = updatedSubTask.toProjectSubTaskUi()
            state.update { currentState ->
                val project = currentState.project ?: return@update currentState
                currentState.copy(
                    project =
                        project.copy(
                            projectTasks =
                                project.projectTasks?.map { task ->
                                    task.copy(
                                        subTasks =
                                            task.subTasks.map { ui ->
                                                if (ui.projectSubTaskId != subTaskId) {
                                                    ui
                                                } else {
                                                    ui.copy(
                                                        isFinished = updatedUi.isFinished,
                                                        // Finishing stopped it above; unfinishing can only happen on a
                                                        // row that was already stopped.
                                                        isTimerRunning = false,
                                                        formattedEndDateTimeUtc = updatedUi.formattedEndDateTimeUtc,
                                                    )
                                                }
                                            },
                                    )
                                },
                        ),
                )
            }

            // Keeps the parent in step: unchecking a subtask re-opens it (the escape route the
            // uncheck-blocked dialog names), checking the last open one finishes it.
            syncTaskFinishedFromSubTasks(parentTaskId)
        }
    }

    /**
     * A task with subtasks is finished exactly when all of them are, so its checkbox is a bulk
     * "finish everything" switch with no meaningful inverse — there is no single subtask the UI
     * could reopen on the user's behalf. Unchecking is therefore refused with an explanation, and
     * the escape routes the dialog names (uncheck a subtask, add a new one) un-finish it instead.
     *
     * A task without subtasks is unconstrained and toggles freely.
     */
    fun onTaskCheckedChange(taskId: String) {
        val task =
            state.value.project
                ?.projectTasks
                ?.find { it.projectTaskId == taskId } ?: return

        if (task.isFinished && task.subTasks.isNotEmpty()) {
            state.update { it.copy(isUncheckTaskBlockedDialogVisible = true) }
            return
        }

        val nowFinished = !task.isFinished

        scope.launch {
            // A finished task must not keep counting, at either level.
            if (nowFinished) {
                if (task.subTasks.isEmpty()) {
                    if (task.isTimerRunning) {
                        projectTaskRepository.stopProjectTask(taskId)
                    }
                } else {
                    finishAllSubTasks(taskId)
                }
            }
            setTaskFinished(taskId, nowFinished)
        }
    }

    /** Finishes every still-open subtask of [taskId], stopping any whose timer is running. */
    private suspend fun finishAllSubTasks(taskId: String) {
        val open = subTaskRepository.getSubTasksForTask(taskId).first().filterNot { it.isFinished }
        val finishedIds = open.map { it.projectSubTaskId }.toSet()

        val stoppedAnyTimer = open.any { it.isTimerRunning }

        // Stop first, then re-read. stopSubTask banks the elapsed time into the row, so upserting
        // a copy of the pre-stop snapshot would write the old duration back over it and lose the
        // time the user just tracked.
        open.filter { it.isTimerRunning }.forEach {
            subTaskRepository.stopSubTask(it.projectSubTaskId)
        }

        subTaskRepository
            .getSubTasksForTask(taskId)
            .first()
            .filter { it.projectSubTaskId in finishedIds }
            .forEach { subTask ->
                subTaskRepository.upsertSubTask(
                    subTask.copy(
                        isFinished = true,
                        endDateTimeUtc = timeProvider.nowInstant,
                        ownUpdatedAt = timeProvider.nowInstant,
                    ),
                )
            }

        state.update { currentState ->
            currentState.mapSubTasksOf(taskId) { ui ->
                if (ui.projectSubTaskId in finishedIds) ui.copy(isFinished = true) else ui
            }
        }

        // Only when a timer actually stopped: finishing already-idle subtasks banks no new time,
        // and the strip would be rebuilt from an unchanged database for nothing.
        if (stoppedAnyTimer) onTimeBanked()
    }

    /**
     * Writes [isFinished] to the task row and mirrors it into state. Reads the row fresh rather than
     * rebuilding it from the UI model: that one holds formatted strings, and any timer this flip
     * just stopped has already banked its time into the row.
     *
     * Deliberately leaves isTimerRunning alone — the view model's timer observer owns that field, and the
     * stopAndResetTimer calls above make it emit.
     */
    private suspend fun setTaskFinished(taskId: String, isFinished: Boolean) {
        val task = projectTaskRepository.getProjectTaskWithIntervalsById(taskId).first() ?: return

        // No "already in that state, skip" guard here on purpose. The row is only the base for the
        // copy — it carries the freshly banked duration and intervals — and it can legitimately
        // disagree with what the user sees. Skipping on that comparison would silently swallow the
        // gesture and freeze the checkbox. Callers that want to avoid a redundant write compare
        // against the UI state instead; see syncTaskFinishedFromSubTasks.
        //
        // upsertProjectTask stamps ownUpdatedAt itself, so this must not set it.
        val updated =
            task.copy(
                isFinished = isFinished,
                endDateTimeUtc = if (isFinished) timeProvider.nowInstant else null,
            )
        projectTaskRepository.upsertProjectTask(updated)

        val updatedUi = updated.toProjectTaskUi()
        state.update { currentState ->
            currentState.mapTask(taskId) { ui ->
                ui.copy(
                    isFinished = updatedUi.isFinished,
                    formattedEndDateTimeUtc = updatedUi.formattedEndDateTimeUtc,
                )
            }
        }
    }

    /**
     * Re-derives a task's finished flag from the subtasks currently in state, which is what keeps
     * the parent checkbox escapable. Call it *after* patching state, so it sees the post-change
     * list. A task with no subtasks is left alone: the rule only binds while subtasks exist.
     */
    suspend fun syncTaskFinishedFromSubTasks(taskId: String) {
        val task =
            state.value.project
                ?.projectTasks
                ?.find { it.projectTaskId == taskId } ?: return
        if (task.subTasks.isEmpty()) return

        val shouldBeFinished = task.subTasks.all { it.isFinished }
        // Compared against the UI flag, not the stored row: this is the only place a redundant
        // write is worth skipping, and the user's view is the thing that must stay consistent.
        if (task.isFinished == shouldBeFinished) return
        setTaskFinished(taskId, shouldBeFinished)
    }
}
