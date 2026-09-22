package com.jvcs.tracky.core.data.networking.mappers

import com.jvcs.tracky.core.data.networking.dto.ActiveTimerChangeDto
import com.jvcs.tracky.core.data.networking.dto.ActiveTimerConflictDto
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.timer.StartActiveTimer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Turns the measured `/api/timer/active` payloads into the rows the sync layer writes. */
class ActiveTimerMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun change(body: String) =
        json.decodeFromString<ActiveTimerChangeDto>(body).toActiveTimerChange()

    @Test
    fun touchedRowsAreSplitByTheKindDiscriminator() {
        // One shape on the wire, two tables locally. Getting this wrong writes a subtask interval
        // into task_intervals, where its parent ids do not resolve.
        val applied = change(
            """
            {
              "touched": [
                {
                  "kind": "task",
                  "id": "c6df0a86-0000-4000-8000-000000000004",
                  "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                  "parentTaskId": "7947002a-0000-4000-8000-000000000003",
                  "startDateTimeUtc": "2026-09-21T15:56:14.425Z",
                  "endDateTimeUtc": "2026-09-21T15:56:16.284Z",
                  "durationMillis": 1859,
                  "startedByDeviceId": "25247336-0000-4000-8000-00000000000a"
                },
                {
                  "kind": "sub_task",
                  "id": "11111111-0000-4000-8000-000000000005",
                  "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                  "parentSubTaskId": "22222222-0000-4000-8000-000000000006",
                  "parentTaskIntervalId": "c6df0a86-0000-4000-8000-000000000004",
                  "startDateTimeUtc": "2026-09-21T15:56:14.425Z"
                }
              ]
            }
            """.trimIndent()
        )

        val closed = applied.touchedTaskIntervals.single()
        assertEquals("c6df0a86-0000-4000-8000-000000000004", closed.intervalId)
        // The point of `touched`: the winning device writes the loser's closing row from this
        // response instead of waiting for the next delta to mention it.
        assertEquals(1859L, closed.durationMillis)
        assertEquals(Instant.parse("2026-09-21T15:56:16.284Z"), closed.endDateTimeUtc)
        // Provenance survives, or an adopted foreign timer reads as one this device started.
        assertEquals("25247336-0000-4000-8000-00000000000a", closed.startedByDeviceId)

        val nested = applied.touchedSubTaskIntervals.single()
        assertEquals("11111111-0000-4000-8000-000000000005", nested.subTaskIntervalId)
        assertEquals("c6df0a86-0000-4000-8000-000000000004", nested.parentTaskIntervalId)
        // No wire counterpart; the merge keeps whatever the local row holds.
        assertTrue(!nested.startedParentTimer)
    }

    @Test
    fun aRowMissingTheParentItNeedsIsDroppedRatherThanFailingTheResponse() {
        // Both parent ids back NOT NULL columns and cascading foreign keys, so such a row cannot
        // be written at all. Dropping it costs one row; throwing would cost the whole response,
        // including the rows that were fine.
        val applied = change(
            """
            {
              "touched": [
                {
                  "kind": "task",
                  "id": "aaaa1111-0000-4000-8000-000000000007",
                  "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                  "startDateTimeUtc": "2026-09-21T15:56:14.425Z"
                },
                {
                  "kind": "sub_task",
                  "id": "bbbb2222-0000-4000-8000-000000000008",
                  "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                  "parentSubTaskId": "22222222-0000-4000-8000-000000000006",
                  "startDateTimeUtc": "2026-09-21T15:56:14.425Z"
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(applied.touchedTaskIntervals.isEmpty())
        assertTrue(applied.touchedSubTaskIntervals.isEmpty())
    }

    @Test
    fun aReplayedStopMapsToAnAppliedChangeThatTouchedNothing() {
        // Measured against the live server. A caller that reads an empty echo as "the stop did not
        // take" would leave the local interval open forever on every retry.
        val applied = change("""{"active":null,"touched":[],"serverNowUtc":"2026-09-21T15:56:16.642Z"}""")

        assertNull(applied.active)
        assertTrue(applied.touchedTaskIntervals.isEmpty())
        assertEquals(Instant.parse("2026-09-21T15:56:16.642Z"), applied.serverNow)
    }

    @Test
    fun anUnparseableServerTimeIsTreatedAsAbsent() {
        // Same rule the delta feed follows: a clock sample the client cannot read is no sample,
        // not a reason to lose the rows that came with it.
        assertNull(change("""{"touched":[],"serverNowUtc":"not a timestamp"}""").serverNow)
    }

    @Test
    fun aConflictCarriesTheTimerThatIsActuallyRunning() {
        val rejected = json.decodeFromString<ActiveTimerConflictDto>(
            """
            {
              "code": "TIMER_CONFLICT",
              "active": {
                "intervalId": "d8bc4ae2-0000-4000-8000-000000000009",
                "kind": "sub_task",
                "parentProjectId": "4406e80a-0000-4000-8000-00000000000b",
                "parentTaskId": "1f4d3070-0000-4000-8000-00000000000c",
                "parentSubTaskId": "22222222-0000-4000-8000-000000000006",
                "parentTaskIntervalId": "c6df0a86-0000-4000-8000-000000000004",
                "startedAtUtc": "2026-09-21T16:07:15.289Z",
                "serverNowUtc": "2026-09-21T16:07:15.927Z"
              }
            }
            """.trimIndent()
        ).toRejected()

        // What lets the losing device converge on the truth without a second round trip.
        assertEquals("d8bc4ae2-0000-4000-8000-000000000009", rejected.active?.intervalId)
        assertEquals(ActiveTimerKind.SUB_TASK, rejected.active?.kind)
        // No envelope timestamp on a conflict, but the timer it names carries one.
        assertEquals(Instant.parse("2026-09-21T16:07:15.927Z"), rejected.serverNow)
    }

    @Test
    fun aRefusalToRestartAClosedIntervalNamesNoTimer() {
        val rejected = json.decodeFromString<ActiveTimerConflictDto>(
            """{"code":"TIMER_CONFLICT","active":null}"""
        ).toRejected()

        assertNull(rejected.active)
        assertNull(rejected.serverNow)
    }

    @Test
    fun aStartRequestSpellsTheKindTheWayTheServerDoes() {
        val request = StartActiveTimer(
            intervalId = "11111111-0000-4000-8000-000000000005",
            kind = ActiveTimerKind.SUB_TASK,
            parentTaskId = "7947002a-0000-4000-8000-000000000003",
            parentSubTaskId = "22222222-0000-4000-8000-000000000006",
            parentTaskIntervalId = "c6df0a86-0000-4000-8000-000000000004",
            startedAt = Instant.parse("2026-09-21T15:56:14.425Z"),
            deviceId = "25247336-0000-4000-8000-00000000000a"
        ).toRequest()

        assertEquals("sub_task", request.kind)
        assertEquals("2026-09-21T15:56:14.425Z", request.startedAtUtc)
        assertEquals("25247336-0000-4000-8000-00000000000a", request.deviceId)
    }
}
