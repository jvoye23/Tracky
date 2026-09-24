package com.jvcs.tracky.core.data.networking.dto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.networking.CreateProjectTaskRequest
import com.jvcs.tracky.core.data.networking.mappers.toProjectTask
import com.jvcs.tracky.core.data.networking.mappers.toProjectTaskDto
import com.jvcs.tracky.features.project.data.mappers.toCreateProjectTaskRequest
import com.jvcs.tracky.features.project.data.mappers.toUpdateProjectTaskRequest
import kotlinx.serialization.json.Json
import kotlin.test.Test

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
        val dto =
            json.decodeFromString<ProjectTaskDto>(
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
                """.trimIndent(),
            )

        assertThat(dto.title).isEqualTo("Work task")
        assertThat(dto.description).isEqualTo("Quarterly report")
        assertThat(dto.toProjectTask("p1").title).isEqualTo("Work task")
    }

    @Test
    fun aNestedIntervalKeepsTheDeviceThatOpenedIt() {
        // GET /api/projects carries startedByDeviceId too, not just the delta feed. Handing the
        // nested intervals a hardcoded null here would blank the provenance of every interval on
        // every full pull - including the fallback pull a full resync triggers.
        val dto =
            json.decodeFromString<ProjectTaskDto>(
                """
                {
                  "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                  "title": "Work task",
                  "startDateTimeUtc": "2026-03-28T15:16:40Z",
                  "intervals": [
                    {
                      "id": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
                      "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                      "startDateTimeUtc": "2026-03-28T15:16:40Z",
                      "startedByDeviceId": "d41c7f90-0000-4000-8000-00000000000a"
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertThat(
            dto
                .toProjectTask("p1")
                .intervals
                .single()
                .startedByDeviceId,
        ).isEqualTo("d41c7f90-0000-4000-8000-00000000000a")
    }

    @Test
    fun fallsBackToDescriptionWhenTheDeploymentPredatesTheTitleField() {
        // A pre-1.6.0 server sends no title at all, and the title lives in description.
        val dto =
            json.decodeFromString<ProjectTaskDto>(
                """
                {
                  "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                  "description": "Work task",
                  "startDateTimeUtc": "2026-03-28T15:16:40Z"
                }
                """.trimIndent(),
            )

        assertThat(dto.title).isEqualTo("")
        assertThat(dto.toProjectTask("p1").title).isEqualTo("Work task")
        // The title was only ever in description because the deployment is old; it must not also
        // land in the task's description, or the fallback re-creates the conflation.
        assertThat(dto.toProjectTask("p1").description).isNull()
    }

    @Test
    fun serialisingATaskPutsTheTitleInTheTitleField() {
        val task =
            json
                .decodeFromString<ProjectTaskDto>(
                    """{"id":"t1","title":"Work task","startDateTimeUtc":"2026-03-28T15:16:40Z"}""",
                ).toProjectTask("p1")

        val encoded = json.encodeToString(task.toProjectTaskDto())

        assertThat(encoded.contains(""""title":"Work task""""), name = encoded).isTrue()
        // Nothing supplied a description, so none is invented to fill the field.
        assertThat(task.toProjectTaskDto().description).isNull()
    }

    @Test
    fun titleAndDescriptionBothSurviveARoundTrip() {
        // Since the local table gained its own title column, description is the task's *other*
        // text rather than a second copy of the title, and both have to survive a round trip.
        val task =
            json
                .decodeFromString<ProjectTaskDto>(
                    """{"id":"t1","title":"Work task","description":"Quarterly report",""" +
                        """"startDateTimeUtc":"2026-03-28T15:16:40Z"}""",
                ).toProjectTask("p1")

        assertThat(task.title).isEqualTo("Work task")
        assertThat(task.description).isEqualTo("Quarterly report")

        val dto = task.toProjectTaskDto()
        assertThat(dto.title).isEqualTo("Work task")
        assertThat(dto.description).isEqualTo("Quarterly report")

        // The update body carries it too, so editing a task no longer blanks its description.
        assertThat(task.toUpdateProjectTaskRequest().description).isEqualTo("Quarterly report")
        assertThat(task.toCreateProjectTaskRequest().description).isEqualTo("Quarterly report")
    }

    @Test
    fun theCreateBodyCarriesANonBlankTitle() {
        val task =
            json
                .decodeFromString<ProjectTaskDto>(
                    """{"id":"t1","title":"Work task","startDateTimeUtc":"2026-03-28T15:16:40Z"}""",
                ).toProjectTask("p1")

        val body: CreateProjectTaskRequest = task.toCreateProjectTaskRequest()

        // The exact condition the server's @NotBlank enforces.
        assertThat(body.title.isNotBlank()).isTrue()
        assertThat(body.title).isEqualTo("Work task")
        assertThat(body.id).isEqualTo("t1")
    }
}
