package com.jvcs.tracky.core.data.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.database.inMemoryTrackyDatabase
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant

internal class RoomPendingSyncDataSourceTest {

    private val db = inMemoryTrackyDatabase()
    private val queue = RoomPendingSyncDataSource(db.pendingSyncDao)

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun enqueue(
        entityId: String,
        operation: String,
        at: Long = 0L,
    ) = queue.enqueue(entityId, PendingSyncOperation.ENTITY_PROJECT, operation, null, Instant.fromEpochMilliseconds(at))

    @Test
    fun aCreateAbsorbsLaterUpdatesAndIsReadBackInOrder() =
        runBlocking {
            enqueue("p1", PendingSyncOperation.OP_CREATE, at = 1)
            enqueue("p1", PendingSyncOperation.OP_UPDATE, at = 2)
            enqueue("p2", PendingSyncOperation.OP_UPDATE, at = 3)

            assertThat(queue.getPendingOperations().map { ops -> ops.map { it.entityId to it.operationType } })
                .isEqualTo(
                    Result.Success(
                        listOf(
                            "p1" to PendingSyncOperation.OP_CREATE,
                            "p2" to PendingSyncOperation.OP_UPDATE,
                        ),
                    ),
                )
            assertThat(queue.hasPendingCreate("p1")).isEqualTo(Result.Success(true))
            assertThat(queue.hasPendingCreate("p2")).isEqualTo(Result.Success(false))
        }

    @Test
    fun operationsAreRemovedOneAtATimeOrByEntity() =
        runBlocking {
            enqueue("p1", PendingSyncOperation.OP_UPDATE)
            enqueue("p2", PendingSyncOperation.OP_DELETE)
            val first = (queue.getOperationsByEntityId("p1") as Result.Success).data.single()

            queue.deleteOperation(first.operationId)
            queue.deleteOperationsByEntityId("p2")

            assertThat(queue.getPendingOperations()).isEqualTo(Result.Success(emptyList()))
        }
}
