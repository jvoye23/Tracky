package com.jvcs.tracky.features.project.domain.subtask

import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.analytics.api.FakeAnalyticsLogger
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

internal class SubTasksCountPropertySyncTest {

    private val analytics = FakeAnalyticsLogger()
    private val dataSource = SubTaskCountDataSource()

    // runCurrent, not advanceUntilIdle: the sync collects on backgroundScope, which
    // advanceUntilIdle does not wait for.
    private fun TestScope.startSync(times: Int = 1) {
        val sync =
            SubTasksCountPropertySync(
                localSubTaskDataSource = dataSource,
                analyticsLogger = analytics,
                applicationScope = backgroundScope,
            )
        repeat(times) { sync.start() }
        runCurrent()
    }

    private fun setValues() = analytics.userProperties.map { it.name to it.value }

    @Test
    fun theCountIsSetAtLaunch() =
        runTest {
            startSync()

            assertThat(setValues()).containsExactly("sub_tasks_count" to "0")
        }

    @Test
    fun everyChangeOfBucketSetsItAgain() =
        runTest {
            startSync()

            dataSource.count.value = 4
            runCurrent()
            dataSource.count.value = 9
            runCurrent()

            assertThat(setValues().map { it.second }).containsExactly("0", "1-5", "9")
        }

    @Test
    fun aChangeInsideTheSameBucketSetsNothing() =
        runTest {
            dataSource.count.value = 1
            startSync()

            dataSource.count.value = 5
            runCurrent()

            assertThat(setValues().map { it.second }).containsExactly("1-5")
        }

    @Test
    fun sixSubtasksLeaveTheLowBucket() =
        runTest {
            dataSource.count.value = 5
            startSync()

            dataSource.count.value = 6
            runCurrent()

            assertThat(setValues().map { it.second }).containsExactly("1-5", "6")
        }

    @Test
    fun startingTwiceStillSetsItOnce() =
        runTest {
            startSync(times = 2)

            assertThat(setValues()).containsExactly("sub_tasks_count" to "0")
        }
}

/** Serves [count] as the number of stored subtasks; the sync reads nothing else. */
private class SubTaskCountDataSource : LocalSubTaskDataSource {

    val count = MutableStateFlow(0)

    override fun observeSubTaskCount(): Flow<Int> = count

    // Unused by the sync; stubbed rather than implemented.
    override fun getSubTasksForTask(taskId: String): Flow<List<ProjectSubTask>> = flowOf(emptyList())

    override suspend fun getSubTaskById(subTaskId: String): Result<ProjectSubTask?, DataError.Local> =
        Result.Success(null)

    override suspend fun upsertSubTask(subTask: ProjectSubTask): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun deleteSubTask(subTaskId: String): EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun startSubTask(subTaskId: String): Result<SubTaskTimerChange, DataError.Local> =
        Result.Error(DataError.Local.UNKNOWN)

    override suspend fun stopSubTask(subTaskId: String): Result<SubTaskTimerChange?, DataError.Local> =
        Result.Success(null)

    override suspend fun lastStartedSubTaskId(taskId: String): Result<String?, DataError.Local> = Result.Success(null)

    override suspend fun getSubTaskSortIndices(taskId: String): Result<Map<String, Long?>, DataError.Local> =
        Result.Success(emptyMap())

    override suspend fun updateSubTaskSortIndices(
        indices: Map<String, Long>,
        updatedAt: Instant,
    ): EmptyResult<DataError.Local> = Result.Success(Unit)
}
