package com.jvcs.tracky.core.data.networking.dto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.networking.mappers.toSyncChanges
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Instant

/**
 * Decodes `GET /api/sync/changes` as `Requirements/backend-delta-sync-api.md` documents it.
 *
 * The endpoint does not exist yet, so these payloads are the spec, and this file is where a
 * deviation gets caught rather than at runtime on a device.
 */
class SyncChangesDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val documentedResponse =
        """
        {
          "cursor": 84213,
          "serverNowUtc": "2026-09-20T09:14:02.118Z",
          "fullResyncRequired": false,
          "hasMore": false,
          "projects": [],
          "tasks": [],
          "taskIntervals": [
            {
              "id": "6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03",
              "parentTaskId": "06357c1e-0000-4000-8000-000000000001",
              "parentProjectId": "a3a2aef0-0000-4000-8000-000000000002",
              "startDateTimeUtc": "2026-09-20T08:41:07Z",
              "endDateTimeUtc": "2026-09-20T09:22:44Z",
              "durationMillis": 2497000,
              "updatedAtUtc": "2026-09-20T09:22:44.771Z"
            }
          ],
          "subTasks": [],
          "subTaskIntervals": [],
          "tombstones": [
            {
              "entityType": "project",
              "entityId": "6f2a0000-0000-4000-8000-000000000003",
              "deletedAtUtc": "2026-09-20T08:02:11Z"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun theDocumentedResponseDecodes() {
        val changes = json.decodeFromString<SyncChangesDto>(documentedResponse).toSyncChanges()

        assertThat(changes.cursor).isEqualTo(84_213L)
        assertThat(changes.serverNow).isEqualTo(Instant.parse("2026-09-20T09:14:02.118Z"))
        assertThat(changes.fullResyncRequired).isFalse()
        assertThat(changes.hasMore).isFalse()
    }

    @Test
    fun aClosedIntervalArrivesFlatWithBothParentIds() {
        val changes = json.decodeFromString<SyncChangesDto>(documentedResponse).toSyncChanges()

        val interval = changes.taskIntervals.single()
        assertThat(interval.intervalId).isEqualTo("6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03")
        assertThat(interval.parentTaskId).isEqualTo("06357c1e-0000-4000-8000-000000000001")
        // The flat feed has no enclosing project to hand this down from, so the row carries it.
        assertThat(interval.parentProjectId).isEqualTo("a3a2aef0-0000-4000-8000-000000000002")
        assertThat(interval.endDateTimeUtc).isEqualTo(Instant.parse("2026-09-20T09:22:44Z"))
        // This row predates the column, so the server has nothing to send and the merge falls back
        // to whatever the local row holds. A row that *does* carry provenance is covered below.
        assertThat(interval.startedByDeviceId).isNull()
    }

    @Test
    fun provenanceCrossesTheWireOnBothIntervalLevels() {
        // The whole point of the column: a device that did not open this interval has to be able
        // to tell. Discarding the field here makes a foreign timer read as one this device
        // started, which is what StrandedTimerReconciler would then offer to reclaim.
        val changes =
            json
                .decodeFromString<SyncChangesDto>(
                    """
                    {
                      "cursor": 84214,
                      "projects": [],
                      "tasks": [],
                      "taskIntervals": [
                        {
                          "id": "6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03",
                          "parentTaskId": "06357c1e-0000-4000-8000-000000000001",
                          "parentProjectId": "a3a2aef0-0000-4000-8000-000000000002",
                          "startDateTimeUtc": "2026-09-20T08:41:07Z",
                          "startedByDeviceId": "d41c7f90-0000-4000-8000-00000000000a"
                        }
                      ],
                      "subTasks": [],
                      "subTaskIntervals": [
                        {
                          "id": "11111111-0000-4000-8000-000000000004",
                          "parentSubTaskId": "22222222-0000-4000-8000-000000000005",
                          "parentTaskIntervalId": "6f2a91b4-0c3d-4e58-a77f-1b9e2c4d8a03",
                          "parentProjectId": "a3a2aef0-0000-4000-8000-000000000002",
                          "startDateTimeUtc": "2026-09-20T08:41:07Z",
                          "startedByDeviceId": "d41c7f90-0000-4000-8000-00000000000a"
                        }
                      ],
                      "tombstones": []
                    }
                    """.trimIndent(),
                ).toSyncChanges()

        assertThat(changes.taskIntervals.single().startedByDeviceId).isEqualTo("d41c7f90-0000-4000-8000-00000000000a")
        assertThat(
            changes.subTaskIntervals.single().startedByDeviceId,
        ).isEqualTo("d41c7f90-0000-4000-8000-00000000000a")
    }

    @Test
    fun aTombstoneCarriesItsTypeAndId() {
        val changes = json.decodeFromString<SyncChangesDto>(documentedResponse).toSyncChanges()

        val tombstone = changes.tombstones.single()
        assertThat(tombstone.entityType).isEqualTo("project")
        assertThat(tombstone.entityId).isEqualTo("6f2a0000-0000-4000-8000-000000000003")
    }

    @Test
    fun aRowWithNoParentProjectIdIsDroppedRatherThanFailingThePull() {
        val payload =
            """
            {
              "cursor": 5,
              "taskIntervals": [
                {
                  "id": "i1",
                  "parentTaskId": "t1",
                  "startDateTimeUtc": "2026-09-20T08:41:07Z",
                  "durationMillis": 0
                }
              ]
            }
            """.trimIndent()

        val changes = json.decodeFromString<SyncChangesDto>(payload).toSyncChanges()

        // parentProjectId is NOT NULL locally and backs the cascade onto projects, so the row
        // cannot be written. One malformed entry must not cost the whole delta.
        assertThat(changes.taskIntervals.isEmpty()).isTrue()
        assertThat(changes.cursor).isEqualTo(5L)
    }

    @Test
    fun aMinimalResponseDecodesWithEveryCollectionEmpty() {
        val changes = json.decodeFromString<SyncChangesDto>("""{"cursor": 0}""").toSyncChanges()

        // A quiet poll, and a server that omits empty arrays rather than sending [].
        assertThat(changes.isEmpty).isTrue()
        assertThat(changes.serverNow).isNull()
        assertThat(changes.fullResyncRequired).isFalse()
    }

    @Test
    fun anExpiredCursorAsksForAFullResync() {
        val payload = """{"cursor": 0, "fullResyncRequired": true}"""

        val changes = json.decodeFromString<SyncChangesDto>(payload).toSyncChanges()

        assertThat(changes.fullResyncRequired).isTrue()
    }

    @Test
    fun unknownFieldsAreTolerated() {
        val payload = """{"cursor": 9, "somethingNewerServersSend": {"a": 1}}"""

        assertThat(json.decodeFromString<SyncChangesDto>(payload).toSyncChanges().cursor).isEqualTo(9L)
    }

    @Test
    fun anUnparseableServerTimeIsTreatedAsAbsent() {
        val payload = """{"cursor": 9, "serverNowUtc": "not a timestamp"}"""

        // Losing clock correction degrades the timer by the skew; throwing would lose the pull.
        assertThat(json.decodeFromString<SyncChangesDto>(payload).toSyncChanges().serverNow).isNull()
    }
}
