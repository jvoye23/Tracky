package com.jvcs.tracky.features.project.data.subtaskinterval

import com.jvcs.tracky.core.data.networking.CreateSubTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.UpdateSubTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.delete
import com.jvcs.tracky.core.data.networking.dto.SubTaskIntervalDto
import com.jvcs.tracky.core.data.networking.mappers.toSubTaskInterval
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.data.networking.put
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.data.mappers.toCreateSubTaskIntervalRequest
import com.jvcs.tracky.features.project.data.mappers.toUpdateSubTaskIntervalRequest
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.subtaskinterval.RemoteSubTaskIntervalDataSource
import io.ktor.client.HttpClient

class KtorRemoteSubTaskIntervalDataSource(
    private val httpClient: HttpClient
) : RemoteSubTaskIntervalDataSource {

    override suspend fun postInterval(
        projectId: String,
        taskId: String,
        interval: SubTaskInterval
    ): Result<SubTaskInterval, DataError.Remote> {
        return httpClient.post<CreateSubTaskIntervalRequest, SubTaskIntervalDto>(
            route = intervalsRoute(projectId, taskId, interval.parentSubTaskId),
            body = interval.toCreateSubTaskIntervalRequest()
        ).map { it.toDomain(interval) }
    }

    override suspend fun updateInterval(
        projectId: String,
        taskId: String,
        interval: SubTaskInterval
    ): Result<SubTaskInterval, DataError.Remote> {
        return httpClient.put<UpdateSubTaskIntervalRequest, SubTaskIntervalDto>(
            route = "${intervalsRoute(projectId, taskId, interval.parentSubTaskId)}/${interval.subTaskIntervalId}",
            body = interval.toUpdateSubTaskIntervalRequest()
        ).map { it.toDomain(interval) }
    }

    override suspend fun deleteInterval(
        projectId: String,
        taskId: String,
        subTaskId: String,
        intervalId: String
    ): EmptyResult<DataError.Remote> {
        return httpClient.delete(
            route = "${intervalsRoute(projectId, taskId, subTaskId)}/$intervalId"
        )
    }

    private fun intervalsRoute(projectId: String, taskId: String, subTaskId: String) =
        "/api/projects/$projectId/tasks/$taskId/subtasks/$subTaskId/intervals"

    /**
     * The server echoes back neither `parentTaskIntervalId` nor `startedParentTimer` — it has no
     * column for either. Both are taken from [sent], the row this call was built from, so writing
     * the echo to Room cannot blank out a NOT NULL foreign key or lose which timer opened which.
     */
    private fun SubTaskIntervalDto.toDomain(sent: SubTaskInterval): SubTaskInterval =
        toSubTaskInterval(
            parentProjectId = sent.parentProjectId,
            parentTaskIntervalId = sent.parentTaskIntervalId,
            startedParentTimer = sent.startedParentTimer
        )
}
