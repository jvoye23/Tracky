package com.jvcs.tracky.core.domain.sync

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation.Companion.OP_CREATE
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation.Companion.OP_DELETE
import com.jvcs.tracky.core.domain.sync.PendingSyncOperation.Companion.OP_UPDATE
import com.jvcs.tracky.features.project.domain.models.testInterval
import com.jvcs.tracky.features.project.domain.models.testProject
import com.jvcs.tracky.features.project.domain.models.testSubTask
import com.jvcs.tracky.features.project.domain.models.testSubTaskInterval
import com.jvcs.tracky.features.project.domain.models.testTask
import kotlin.test.Test
import kotlin.time.Instant

/** What has to be queued after an import, from the tree that was uploaded and the tree as it is now. */
class SyncRowDiffTest {

    private val tree =
        listOf(
            testProject(
                "p1",
                tasks =
                    listOf(
                        testTask(
                            "t1",
                            "p1",
                            intervals = listOf(testInterval("i1", "t1", "p1")),
                            subTasks =
                                listOf(
                                    testSubTask(
                                        "s1",
                                        "t1",
                                        "p1",
                                        intervals = listOf(testSubTaskInterval("si1", "s1", "i1", "p1")),
                                    ),
                                ),
                        ),
                    ),
            ),
        )

    private fun changes(after: List<com.jvcs.tracky.features.project.domain.models.Project>) =
        diffForSync(tree.flattenForSync(), after.flattenForSync()).map { it.row.entityId to it.operationType }

    @Test
    fun everyLevelOfTheTreeIsItsOwnRowWithItsParent() {
        val rows = tree.flattenForSync()

        assertThat(rows.mapValues { it.value.entityType to it.value.parentEntityId }).isEqualTo(
            mapOf(
                "p1" to (PendingSyncOperation.ENTITY_PROJECT to null),
                "t1" to (PendingSyncOperation.ENTITY_TASK to "p1"),
                "i1" to (PendingSyncOperation.ENTITY_INTERVAL to "t1"),
                "s1" to (PendingSyncOperation.ENTITY_SUBTASK to "t1"),
                "si1" to (PendingSyncOperation.ENTITY_SUBTASK_INTERVAL to "s1"),
            ),
        )
    }

    @Test
    fun anUnchangedTreeQueuesNothing() {
        assertThat(changes(tree)).isEmpty()
    }

    @Test
    fun aChildChangingDoesNotMarkItsParentChanged() {
        val project = tree.single()
        val task = project.projectTasks!!.single()
        val after =
            listOf(
                project.copy(
                    projectTasks = listOf(task.copy(intervals = listOf(testInterval("i1", "t1", "p1", 120_000)))),
                ),
            )

        assertThat(changes(after)).containsExactlyInAnyOrder("i1" to OP_UPDATE)
    }

    @Test
    fun newRowsAreCreatedAndRenamedOnesUpdated() {
        val after = listOf(tree.single().copy(title = "Renamed"), testProject("p2"))

        assertThat(changes(after).filter { it.first in setOf("p1", "p2") })
            .containsExactlyInAnyOrder("p1" to OP_UPDATE, "p2" to OP_CREATE)
    }

    @Test
    fun aDeletedProjectQueuesADeleteForEveryRowItTookWithIt() {
        assertThat(changes(emptyList())).containsExactlyInAnyOrder(
            "p1" to OP_DELETE,
            "t1" to OP_DELETE,
            "i1" to OP_DELETE,
            "s1" to OP_DELETE,
            "si1" to OP_DELETE,
        )
    }

    @Test
    fun aSubTaskIntervalChangingDoesNotMarkItsSubTaskChanged() {
        val project = tree.single()
        val task = project.projectTasks!!.single()
        val subTask = task.subTasks!!.single()
        val changedInterval = subTask.subTaskIntervals.single().copy(durationMillis = 120_000)
        val after =
            listOf(
                project.copy(
                    projectTasks =
                        listOf(task.copy(subTasks = listOf(subTask.copy(subTaskIntervals = listOf(changedInterval))))),
                ),
            )

        assertThat(changes(after)).containsExactlyInAnyOrder("si1" to OP_UPDATE)
    }

    @Test
    fun theDerivedLastTrackedTimeIsNotAChange() {
        val after = listOf(tree.single().copy(lastTrackedEndUtc = Instant.fromEpochMilliseconds(120_000)))

        assertThat(changes(after)).isEmpty()
    }
}
