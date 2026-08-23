package com.jvcs.tracky.core.data.networking.dto

import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.mappers.toProjectTask
import com.jvcs.tracky.core.data.networking.mappers.toProjectTaskDto
import com.jvcs.tracky.features.project.data.mappers.toCreateProjectTaskRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the task title contract, which API 1.6.0 changed underneath the client.
 *
 * `title` became a required `@NotBlank` field on the create and update bodies, and the response
 * grew it alongside the pre-existing `description`. The client had neither: it sent the domain's
 * title *as* `description`, so every task push came back 400 and every pull read the title out of
 * the wrong field. Nothing in the app surfaces that as anything but a failed sync, which is why it
 * is pinned here.
 */
class ProjectTaskDtoTest {

    // Mirrors the HttpClientFactory configuration, so this test fails for the same reasons the app would.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesTheDocumentedServerResponse() {
        val dto = json.decodeFromString<ProjectTaskDto>(
            """
            {
              "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
              "title": "Work task",
              "description": "Quarterly report",
              "durationMillis": 3600000,
              "startDateTimeUtc": "2026-03-28T15:16:40Z",
              "endDateTimeUtc": null,
              "isFinished": false,
              "isTimerRunning": true,
              "updatedAtUtc": "2026-03-28T15:16:40.112000000Z",
              "intervals": []
            }
            """.trimIndent()
        )

        assertEquals("Work task", dto.title)
        assertEquals("Quarterly report", dto.description)
        assertEquals("Work task", dto.toProjectTask("p1").title)
    }

    @Test
    fun fallsBackToDescriptionWhenTheDeploymentPredatesTheTitleField() {
        // A pre-1.6.0 server sends no title at all, and the title lives in description.
        val dto = json.decodeFromString<ProjectTaskDto>(
            """
            {
              "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
              "description": "Work task",
              "startDateTimeUtc": "2026-03-28T15:16:40Z"
            }
            """.trimIndent()
        )

        assertEquals("", dto.title)
        assertEquals("Work task", dto.toProjectTask("p1").title)
    }

    @Test
    fun serialisingATaskPutsTheTitleInTheTitleField() {
        val task = json.decodeFromString<ProjectTaskDto>(
            """{"id":"t1","title":"Work task","startDateTimeUtc":"2026-03-28T15:16:40Z"}"""
        ).toProjectTask("p1")

        val encoded = json.encodeToString(task.toProjectTaskDto())

        assertTrue(encoded.contains(""""title":"Work task""""), encoded)
        assertNull(task.toProjectTaskDto().description)
    }

    @Test
    fun theCreateBodyCarriesANonBlankTitle() {
        val task = json.decodeFromString<ProjectTaskDto>(
            """{"id":"t1","title":"Work task","startDateTimeUtc":"2026-03-28T15:16:40Z"}"""
        ).toProjectTask("p1")

        val body: CreateProjectTaskRequest = task.toCreateProjectTaskRequest()

        // The exact condition the server's @NotBlank enforces.
        assertTrue(body.title.isNotBlank())
        assertEquals("Work task", body.title)
        assertEquals("t1", body.id)
    }
}
