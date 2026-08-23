package com.jvcs.tracky.features.project.domain.subtaskinterval

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval

/**
 * Subtask intervals are written through their own endpoints, nested three levels deep under
 * `/api/projects/{projectId}/tasks/{taskId}/subtasks/{subTaskId}/intervals`. Reads come back nested
 * inside GET /api/projects, so there is deliberately no read method here.
 *
 * Every method takes [projectId] and [taskId] explicitly, unlike RemoteIntervalDataSource where
 * only the delete does. A SubTaskInterval carries its project and subtask ids but never its *task*
 * id — the model holds only what its foreign keys need — so the route cannot be built from the
 * interval alone. The repository resolves the task id from the parent subtask row and passes it in.
 */
interface RemoteSubTaskIntervalDataSource {
    suspend fun postInterval(
        projectId: String,
        taskId: String,
        interval: SubTaskInterval
    ): Result<SubTaskInterval, DataError.Remote>

    suspend fun updateInterval(
        projectId: String,
        taskId: String,
        interval: SubTaskInterval
    ): Result<SubTaskInterval, DataError.Remote>

    /** Takes the ids explicitly: by the time a queued delete drains, the local row is already gone. */
    suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        subTaskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote>
}
