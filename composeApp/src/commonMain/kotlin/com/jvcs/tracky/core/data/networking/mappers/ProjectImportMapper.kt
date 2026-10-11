package com.jvcs.tracky.core.data.networking.mappers

import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.features.project.domain.models.Project

/**
 * A whole local project tree in the shape `POST /api/sync/import` takes, which is the shape
 * `GET /api/projects` returns.
 *
 * Built on the push mappers, then filled in where those leave fields out on purpose. A task or
 * subtask push carries no sort index because the order travels on its own endpoint, and an interval
 * push carries no device id because the timer endpoint sets it. An import is the only way any of it
 * reaches the server, so here they all have to ride along.
 */
fun Project.toImportProjectDto(): ProjectDto =
    toProjectDto().copy(
        tasks =
            projectTasks.orEmpty().map { task ->
                task.toProjectTaskDto().copy(
                    sortIndex = task.sortIndex,
                    intervals =
                        task.intervals.map { interval ->
                            interval.toTaskIntervalDto().copy(startedByDeviceId = interval.startedByDeviceId)
                        },
                    subTasks =
                        task.subTasks.orEmpty().map { subTask ->
                            subTask.toProjectSubTaskDto().copy(
                                sortIndex = subTask.sortIndex,
                                intervals =
                                    subTask.subTaskIntervals.map { interval ->
                                        interval
                                            .toSubTaskIntervalDto()
                                            .copy(startedByDeviceId = interval.startedByDeviceId)
                                    },
                            )
                        },
                )
            },
    )
