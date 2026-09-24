package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the wire shapes of `/api/timer/active`.
 *
 * The fixtures were captured from the deployed backend rather than transcribed from
 * `Requirements/backend-active-timer-api.md`, so a drift between the two shows up here.
 */
class ActiveTimerDtoTest {

    // Mirrors the HttpClientFactory configuration, so this fails for the same reasons the app would.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesTheStartResponse() {
        val dto =
            json.decodeFromString<ActiveTimerChangeDto>(
                """
                {
                  "active": {
                    "intervalId": "9d42d176-0000-4000-8000-000000000001",
                    "kind": "task",
                    "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                    "parentTaskId": "7947002a-0000-4000-8000-000000000003",
                    "parentSubTaskId": null,
                    "parentTaskIntervalId": null,
                    "startedAtUtc": "2026-09-21T15:56:16.284Z",
                    "startedByDeviceId": "25247336-0000-4000-8000-00000000000a",
                    "serverNowUtc": "2026-09-21T15:56:16.642Z"
                  },
                  "touched": [
                    {
                      "kind": "task",
                      "id": "c6df0a86-0000-4000-8000-000000000004",
                      "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                      "parentTaskId": "7947002a-0000-4000-8000-000000000003",
                      "parentSubTaskId": null,
                      "parentTaskIntervalId": null,
                      "startDateTimeUtc": "2026-09-21T15:56:14.425Z",
                      "endDateTimeUtc": "2026-09-21T15:56:16.284Z",
                      "durationMillis": 1859,
                      "startedByDeviceId": "25247336-0000-4000-8000-00000000000a",
                      "updatedAtUtc": "2026-09-21T15:56:16.615Z",
                      "changeSeq": 351
                    }
                  ],
                  "serverNowUtc": "2026-09-21T15:56:16.642Z"
                }
                """.trimIndent(),
            )

        assertEquals("9d42d176-0000-4000-8000-000000000001", dto.active?.intervalId)
        assertEquals("25247336-0000-4000-8000-00000000000a", dto.active?.startedByDeviceId)
        // changeSeq is on the wire but deliberately not decoded: the client never stores a
        // sequence per row, only the feed cursor. Tolerating it is what ignoreUnknownKeys buys.
        assertEquals("c6df0a86-0000-4000-8000-000000000004", dto.touched.single().id)
        assertEquals(1859L, dto.touched.single().durationMillis)
        assertEquals("2026-09-21T15:56:16.642Z", dto.serverNowUtc)
    }

    @Test
    fun aSubTaskRowCarriesItsOwnParentsAndNoTaskId() {
        val dto =
            json.decodeFromString<TouchedIntervalDto>(
                """
                {
                  "kind": "sub_task",
                  "id": "11111111-0000-4000-8000-000000000005",
                  "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                  "parentTaskId": null,
                  "parentSubTaskId": "22222222-0000-4000-8000-000000000006",
                  "parentTaskIntervalId": "c6df0a86-0000-4000-8000-000000000004",
                  "startDateTimeUtc": "2026-09-21T15:56:14.425Z"
                }
                """.trimIndent(),
            )

        // One shape covers both levels; the parent ids that do not apply come back null.
        assertEquals("sub_task", dto.kind)
        assertNull(dto.parentTaskId)
        assertEquals("22222222-0000-4000-8000-000000000006", dto.parentSubTaskId)
        assertEquals("c6df0a86-0000-4000-8000-000000000004", dto.parentTaskIntervalId)
        // An interval that is still open has no end and no duration yet.
        assertNull(dto.endDateTimeUtc)
        assertEquals(0L, dto.durationMillis)
    }

    @Test
    fun aReplayedStopDecodesAsAnEmptyChange() {
        // Measured: replaying a stop the server has already applied answers 200 with nothing
        // touched, because nothing changed.
        val dto =
            json.decodeFromString<ActiveTimerChangeDto>(
                """{"active":null,"touched":[],"serverNowUtc":"2026-09-21T15:56:16.642Z"}""",
            )

        assertNull(dto.active)
        assertTrue(dto.touched.isEmpty())
    }

    @Test
    fun theConflictBodyDecodes() {
        val dto =
            json.decodeFromString<ActiveTimerConflictDto>(
                """
                {
                  "code": "TIMER_CONFLICT",
                  "message": "Interval a7b5f4f2 is not the timer that is running",
                  "active": {
                    "intervalId": "d8bc4ae2-0000-4000-8000-000000000009",
                    "kind": "task",
                    "parentProjectId": "4406e80a-0000-4000-8000-00000000000b",
                    "parentTaskId": "1f4d3070-0000-4000-8000-00000000000c",
                    "startedAtUtc": "2026-09-21T16:07:15.289Z",
                    "serverNowUtc": "2026-09-21T16:07:15.927Z"
                  }
                }
                """.trimIndent(),
            )

        assertEquals("TIMER_CONFLICT", dto.code)
        assertEquals("d8bc4ae2-0000-4000-8000-000000000009", dto.active?.intervalId)
    }

    @Test
    fun aRefusalToRestartAClosedIntervalNamesNoTimer() {
        // The other 409. Nothing is running, and the interval must never be resurrected.
        val dto =
            json.decodeFromString<ActiveTimerConflictDto>(
                """{"code":"TIMER_CONFLICT","message":"already closed","active":null}""",
            )

        assertNull(dto.active)
    }
}
