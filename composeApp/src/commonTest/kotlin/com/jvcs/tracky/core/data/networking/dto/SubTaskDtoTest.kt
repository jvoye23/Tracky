package com.jvcs.tracky.core.data.networking.dto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.networking.CreateSubTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.CreateSubTaskRequest
import com.jvcs.tracky.core.data.networking.mappers.toProjectSubTask
import com.jvcs.tracky.core.data.networking.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toCreateSubTaskIntervalRequest
import com.jvcs.tracky.features.project.data.mappers.toCreateSubTaskRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Guards the wire contract for subtasks and subtask intervals against the documented server
 * response (Requirements/api/backend_documentation.md).
 *
 * The same trap TaskIntervalDtoTest exists for applies one level down, twice over: the server names
 * the ids `id`, `parentTaskId` and `parentSubTaskId` while the domain calls them
 * `projectSubTaskId`, `parentProjectTaskId` and `parentSubTaskId`. A mismatch makes every
 * `GET /api/projects` fail to decode the moment the first subtask is uploaded.
 */
class SubTaskDtoTest {

    // Mirrors the HttpClientFactory configuration, so this test fails for the same reasons the app would.
    private val json = Json { ignoreUnknownKeys = true }

    // The tree from backend_documentation.md, trimmed to the levels this test is about.
    private val documentedTask =
        """
        {
          "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
          "title": "Work task",
          "description": "Quarterly report",
          "startDateTimeUtc": "2026-03-28T15:16:40Z",
          "isTimerRunning": true,
          "intervals": [],
          "subTasks": [
            {
              "id": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
              "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
              "title": "Draft the outline",
              "description": null,
              "durationMillis": 600000,
              "startDateTimeUtc": "2026-03-28T15:16:40Z",
              "endDateTimeUtc": "2026-03-28T15:26:40Z",
              "isFinished": true,
              "isTimerRunning": false,
              "updatedAtUtc": "2026-03-28T15:26:40.771000000Z",
              "intervals": [
                {
                  "id": "7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72",
                  "parentSubTaskId": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
                  "parentTaskIntervalId": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
                  "startDateTimeUtc": "2026-03-28T15:16:40Z",
                  "endDateTimeUtc": "2026-03-28T15:26:40Z",
                  "durationMillis": 600000,
                  "updatedAtUtc": "2026-03-28T15:26:40.771000000Z"
                }
              ]
            }
          ]
        }
        """.trimIndent()

    @Test
    fun decodesTheDocumentedFourLevelTree() {
        val task = json.decodeFromString<ProjectTaskDto>(documentedTask)

        val subTask = task.subTasks.single()
        assertThat(subTask.subTaskId).isEqualTo("3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48")
        assertThat(subTask.parentProjectTaskId).isEqualTo("fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e")
        assertThat(subTask.title).isEqualTo("Draft the outline")
        assertThat(subTask.durationMillis).isEqualTo(600000L)
        assertThat(subTask.isFinished).isTrue()

        val interval = subTask.intervals.single()
        assertThat(interval.subTaskIntervalId).isEqualTo("7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72")
        assertThat(interval.parentSubTaskId).isEqualTo("3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48")
        assertThat(interval.parentTaskIntervalId).isEqualTo("9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31")
    }

    @Test
    fun mappingASubTaskHandsTheProjectIdDownFromTheEnclosingTask() {
        // The wire never repeats the project id below the project level.
        val dto = json.decodeFromString<ProjectTaskDto>(documentedTask).subTasks.single()

        val subTask = dto.toProjectSubTask(parentProjectId = "p1")

        assertThat(subTask.parentProjectId).isEqualTo("p1")
        assertThat(subTask.ownUpdatedAt.toString()).isEqualTo("2026-03-28T15:26:40.771Z")
        // A pulled subtask now brings its intervals with it.
        assertThat(
            subTask.subTaskIntervals.single().parentTaskIntervalId,
        ).isEqualTo("9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31")
    }

    @Test
    fun mappingAnIntervalTakesStartedParentTimerFromTheCaller() {
        val dto =
            json
                .decodeFromString<ProjectTaskDto>(documentedTask)
                .subTasks
                .single()
                .intervals
                .single()

        val interval =
            dto.toSubTaskInterval(
                parentProjectId = "p1",
                startedParentTimer = true,
                startedByDeviceId = "device-1",
            )

        // The fields with no wire counterpart: they must survive a server echo unchanged.
        assertThat(interval.startedParentTimer).isTrue()
        assertThat(interval.startedByDeviceId).isEqualTo("device-1")
        // The nesting now comes off the wire rather than from the caller.
        assertThat(interval.parentTaskIntervalId).isEqualTo("9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31")
        assertThat(interval.parentProjectId).isEqualTo("p1")
    }

    @Test
    fun aTaskWithNoSubTasksDecodesEitherWay() {
        // The server always sends `[]`; the default only covers a pre-subtask deployment.
        val explicit =
            json.decodeFromString<ProjectTaskDto>(
                """{"id":"t1","title":"T","startDateTimeUtc":"2026-03-28T15:16:40Z","subTasks":[]}""",
            )
        val omitted =
            json.decodeFromString<ProjectTaskDto>(
                """{"id":"t1","title":"T","startDateTimeUtc":"2026-03-28T15:16:40Z"}""",
            )

        assertThat(explicit.subTasks.isEmpty()).isTrue()
        assertThat(omitted.subTasks.isEmpty()).isTrue()
    }

    @Test
    fun decodesAnOpenSubTaskIntervalWithTheOptionalFieldsOmitted() {
        // The create response for a running timer: no end, no duration yet.
        val dto =
            json.decodeFromString<SubTaskIntervalDto>(
                """
                {
                  "id": "7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72",
                  "parentSubTaskId": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
                  "parentTaskIntervalId": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
                  "startDateTimeUtc": "2026-03-28T15:16:40Z"
                }
                """.trimIndent(),
            )

        assertThat(dto.endDateTimeUtc).isNull()
        assertThat(dto.durationMillis).isEqualTo(0L)
    }

    @Test
    fun theCreateBodiesCarryTheClientGeneratedIdAndLeakNoLocalOnlyFields() {
        val subTask = json.decodeFromString<ProjectTaskDto>(documentedTask).subTasks.single()

        val body: CreateSubTaskRequest = subTask.toProjectSubTask("p1").toCreateSubTaskRequest()
        assertThat(body.id).isEqualTo("3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48")
        assertThat(body.title.isNotBlank()).isTrue() // the server's @NotBlank rule

        val intervalBody: CreateSubTaskIntervalRequest =
            subTask.intervals
                .single()
                .toSubTaskInterval("p1", startedParentTimer = true, startedByDeviceId = null)
                .toCreateSubTaskIntervalRequest()
        assertThat(intervalBody.id).isEqualTo("7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72")
        // Required on create; a missing value is a 400.
        assertThat(intervalBody.parentTaskIntervalId).isEqualTo("9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31")

        // The server has no column for this one and would reject the unknown property.
        assertThat(!json.encodeToString(intervalBody).contains("startedParentTimer")).isTrue()
    }
}
