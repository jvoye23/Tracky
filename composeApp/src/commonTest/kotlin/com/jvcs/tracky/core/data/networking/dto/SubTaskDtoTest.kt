package com.jvcs.tracky.core.data.networking.dto

import com.jvcs.tracky.core.data.networking.CreateSubTaskIntervalRequest
import com.jvcs.tracky.core.data.networking.CreateSubTaskRequest
import com.jvcs.tracky.core.data.networking.mappers.toProjectSubTask
import com.jvcs.tracky.core.data.networking.mappers.toSubTaskInterval
import com.jvcs.tracky.features.project.data.mappers.toCreateSubTaskIntervalRequest
import com.jvcs.tracky.features.project.data.mappers.toCreateSubTaskRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the wire contract for subtasks and subtask intervals against the documented server
 * response (Requirements/api/api_endpoints.md).
 *
 * The same trap TaskIntervalDtoTest exists for applies one level down, twice over: the server names
 * the ids `id`, `parentTaskId` and `parentSubTaskId` while the domain calls them
 * `projectSubTaskId`, `parentProjectTaskId` and `parentSubTaskId`. A mismatch makes every
 * `GET /api/projects` fail to decode the moment the first subtask is uploaded.
 */
class SubTaskDtoTest {

    // Mirrors the HttpClientFactory configuration, so this test fails for the same reasons the app would.
    private val json = Json { ignoreUnknownKeys = true }

    // The tree from api_endpoints.md, trimmed to the levels this test is about.
    private val documentedTask = """
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
        assertEquals("3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48", subTask.subTaskId)
        assertEquals("fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e", subTask.parentProjectTaskId)
        assertEquals("Draft the outline", subTask.title)
        assertEquals(600000L, subTask.durationMillis)
        assertTrue(subTask.isFinished)

        val interval = subTask.intervals.single()
        assertEquals("7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72", interval.subTaskIntervalId)
        assertEquals("3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48", interval.parentSubTaskId)
    }

    @Test
    fun mappingASubTaskHandsTheProjectIdDownFromTheEnclosingTask() {
        // The wire never repeats the project id below the project level.
        val dto = json.decodeFromString<ProjectTaskDto>(documentedTask).subTasks.single()

        val subTask = dto.toProjectSubTask(parentProjectId = "p1")

        assertEquals("p1", subTask.parentProjectId)
        assertEquals("2026-03-28T15:26:40.771Z", subTask.ownUpdatedAt.toString())
        // The wire carries no parentTaskIntervalId, so a pulled subtask cannot bring its intervals
        // along yet. See Requirements/backend-subtask-interval-nesting.md.
        assertTrue(subTask.subTaskIntervals.isEmpty())
    }

    @Test
    fun mappingAnIntervalTakesTheLocalOnlyFieldsFromTheCaller() {
        val dto = json.decodeFromString<ProjectTaskDto>(documentedTask)
            .subTasks.single().intervals.single()

        val interval = dto.toSubTaskInterval(
            parentProjectId = "p1",
            parentTaskIntervalId = "ti1",
            startedParentTimer = true
        )

        // Neither field exists on the wire; both must survive a server echo unchanged.
        assertEquals("ti1", interval.parentTaskIntervalId)
        assertTrue(interval.startedParentTimer)
        assertEquals("p1", interval.parentProjectId)
    }

    @Test
    fun aTaskWithNoSubTasksDecodesEitherWay() {
        // The server always sends `[]`; the default only covers a pre-subtask deployment.
        val explicit = json.decodeFromString<ProjectTaskDto>(
            """{"id":"t1","title":"T","startDateTimeUtc":"2026-03-28T15:16:40Z","subTasks":[]}"""
        )
        val omitted = json.decodeFromString<ProjectTaskDto>(
            """{"id":"t1","title":"T","startDateTimeUtc":"2026-03-28T15:16:40Z"}"""
        )

        assertTrue(explicit.subTasks.isEmpty())
        assertTrue(omitted.subTasks.isEmpty())
    }

    @Test
    fun decodesAnOpenSubTaskIntervalWithTheOptionalFieldsOmitted() {
        // The create response for a running timer: no end, no duration yet.
        val dto = json.decodeFromString<SubTaskIntervalDto>(
            """
            {
              "id": "7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72",
              "parentSubTaskId": "3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48",
              "startDateTimeUtc": "2026-03-28T15:16:40Z"
            }
            """.trimIndent()
        )

        assertNull(dto.endDateTimeUtc)
        assertEquals(0L, dto.durationMillis)
    }

    @Test
    fun theCreateBodiesCarryTheClientGeneratedIdAndLeakNoLocalOnlyFields() {
        val subTask = json.decodeFromString<ProjectTaskDto>(documentedTask).subTasks.single()

        val body: CreateSubTaskRequest = subTask.toProjectSubTask("p1").toCreateSubTaskRequest()
        assertEquals("3d90b1ac-51f7-4a02-9e64-1c7b2f0e5d48", body.id)
        assertTrue(body.title.isNotBlank()) // the server's @NotBlank rule

        val intervalBody: CreateSubTaskIntervalRequest = subTask.intervals.single()
            .toSubTaskInterval("p1", "ti1", startedParentTimer = true)
            .toCreateSubTaskIntervalRequest()
        assertEquals("7a41e0c9-2b8d-4f31-8c05-9e6a3d1f4b72", intervalBody.id)

        // The server has no column for either field and would reject the unknown property.
        val encoded = json.encodeToString(intervalBody)
        assertTrue(!encoded.contains("parentTaskIntervalId"), encoded)
        assertTrue(!encoded.contains("startedParentTimer"), encoded)
    }
}
