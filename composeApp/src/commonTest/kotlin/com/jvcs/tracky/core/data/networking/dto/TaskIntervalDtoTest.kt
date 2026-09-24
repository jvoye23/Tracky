package com.jvcs.tracky.core.data.networking.dto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * Guards the wire contract for task intervals against the documented server response
 * (Requirements/api/backend_documentation.md).
 *
 * This matters more than it looks: the server names the fields `id` and `parentTaskId`, while the
 * domain calls them `intervalId` and `parentSessionId`. Until intervals actually sync, every
 * `"intervals"` array comes back empty, so a mismatch here stays invisible — and then breaks every
 * `GET /api/projects` the moment the first interval is uploaded.
 */
class TaskIntervalDtoTest {

    // Mirrors the HttpClientFactory configuration, so this test fails for the same reasons the app would.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesTheDocumentedServerResponse() {
        val dto =
            json.decodeFromString<TaskIntervalDto>(
                """
                {
                  "id": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
                  "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                  "startDateTimeUtc": "2026-03-28T15:16:40Z",
                  "endDateTimeUtc": "2026-03-28T16:16:40Z",
                  "durationMillis": 3600000,
                  "updatedAtUtc": "2026-03-28T16:16:40.402000000Z"
                }
                """.trimIndent(),
            )

        assertThat(dto.intervalId).isEqualTo("9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31")
        assertThat(dto.parentSessionId).isEqualTo("fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e")
        assertThat(dto.startDateTimeUtc).isEqualTo("2026-03-28T15:16:40Z")
        assertThat(dto.endDateTimeUtc).isEqualTo("2026-03-28T16:16:40Z")
        assertThat(dto.durationMillis).isEqualTo(3600000L)
        assertThat(dto.updatedAt).isEqualTo("2026-03-28T16:16:40.402000000Z")
    }

    @Test
    fun decodesAnOpenIntervalWithTheOptionalFieldsOmitted() {
        // The create-interval response for a running timer: no end, no duration yet.
        val dto =
            json.decodeFromString<TaskIntervalDto>(
                """
                {
                  "id": "8e2a91d4-6c2c-4b6d-9e2f-77a8d2c1f5b1",
                  "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                  "startDateTimeUtc": "2026-03-28T15:16:40Z"
                }
                """.trimIndent(),
            )

        assertThat(dto.endDateTimeUtc).isNull()
        assertThat(dto.durationMillis).isEqualTo(0L)
        assertThat(dto.updatedAt).isNull()
    }

    @Test
    fun decodesATaskCarryingItsIntervals() {
        // The nested shape GET /api/projects returns — the path that used to blow up.
        val task =
            json.decodeFromString<ProjectTaskDto>(
                """
                {
                  "id": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                  "description": "Work task",
                  "durationMillis": 3600000,
                  "startDateTimeUtc": "2026-03-28T15:16:40Z",
                  "endDateTimeUtc": null,
                  "isFinished": false,
                  "isTimerRunning": true,
                  "updatedAtUtc": "2026-03-28T15:16:40.112000000Z",
                  "intervals": [
                    {
                      "id": "9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31",
                      "parentTaskId": "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                      "startDateTimeUtc": "2026-03-28T15:16:40Z",
                      "endDateTimeUtc": "2026-03-28T16:16:40Z",
                      "durationMillis": 3600000,
                      "updatedAtUtc": "2026-03-28T16:16:40.402000000Z"
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertThat(task.intervals.size).isEqualTo(1)
        assertThat(task.intervals.first().intervalId).isEqualTo("9c1f0b52-6a4e-4f0d-9d16-2b5b0f8c9a31")
    }

    @Test
    fun serializesBackToTheServerFieldNames() {
        val encoded =
            json.encodeToString(
                TaskIntervalDto(
                    intervalId = "8e2a91d4-6c2c-4b6d-9e2f-77a8d2c1f5b1",
                    parentSessionId = "fe316e35-bd3f-4c6f-9d7d-23d6b6e8877e",
                    startDateTimeUtc = "2026-03-28T15:16:40Z",
                    endDateTimeUtc = null,
                    durationMillis = 0L,
                ),
            )

        assertThat(encoded.contains("\"id\""), name = "expected the server's \"id\" key in: $encoded").isTrue()
        assertThat(
            encoded.contains("\"parentTaskId\""),
            name = "expected the server's \"parentTaskId\" key in: $encoded",
        ).isTrue()
        assertThat(!encoded.contains("\"intervalId\""), name = "leaked the Kotlin property name in: $encoded").isTrue()
    }
}
