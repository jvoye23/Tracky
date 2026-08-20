package com.jvcs.tracky.features.project.data.interval

import com.jvcs.tracky.core.data.networking.CreateTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.UpdateTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.TaskIntervalDto
import com.jvcs.tracky.core.data.networking.mappers.toTaskInterval
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.data.mappers.toCreateTaskIntervalRequest
import com.jvcs.tracky.features.project.data.mappers.toUpdateTaskIntervalRequest
import com.jvcs.tracky.features.project.domain.interval.RemoteIntervalDataSource
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import io.ktor.client.HttpClient

class KtorRemoteIntervalDataSource(
    private val httpClient: HttpClient
) : RemoteIntervalDataSource {

    override suspend fun postInterval(interval: TaskInterval): Result<TaskInterval, DataError.Remote> {
        val projectId = interval.parentProjectId
        return httpClient.post<CreateTaskIntervalRequest, TaskIntervalDto>(
            route = "/api/projects/$projectId/tasks/${interval.parentTaskId}/intervals",
            body = interval.toCreateTaskIntervalRequest()
        ).map { it.toTaskInterval(projectId) }
    }

    override suspend fun updateInterval(interval: TaskInterval): Result<TaskInterval, DataError.Remote> {
        val projectId = interval.parentProjectId
        return httpClient.put<UpdateTaskIntervalRequest, TaskIntervalDto>(
            route = "/api/projects/$projectId/tasks/${interval.parentTaskId}/intervals/${interval.intervalId}",
            body = interval.toUpdateTaskIntervalRequest()
        ).map { it.toTaskInterval(projectId) }
    }

    override suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote> {
        return httpClient.delete(
            route = "/api/projects/$projectId/tasks/$taskId/intervals/$intervalId"
        )
    }
}
