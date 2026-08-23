package com.jvcs.tracky.features.project.domain.interval

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.TaskInterval

/**
 * Intervals are written through their own endpoints nested under the task — the task create/update
 * bodies do not carry them (see Requirements/api/backend_documentation.md). Reads come back nested inside
 * GET /api/projects, so there is deliberately no interval read method here.
 */
interface RemoteIntervalDataSource {
    // A TaskInterval carries both its parentProjectId and parentTaskId, so the route can be built
    // from the interval alone.
    suspend fun postInterval(interval: TaskInterval): Result<TaskInterval, DataError.Remote>
    suspend fun updateInterval(interval: TaskInterval): Result<TaskInterval, DataError.Remote>

    /** Takes the ids explicitly: by the time a queued delete drains, the local row is already gone. */
    suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote>
}
