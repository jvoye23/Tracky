package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.FakeServerClockOffsetStore
import com.jvcs.tracky.core.domain.util.FakeTimeProvider
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.projecttracker.data.FakeLocalProjectDataSource
import com.jvcs.tracky.features.projecttracker.data.FakePendingSyncDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeRemoteProjectDataSource
import com.jvcs.tracky.features.projecttracker.data.FakeSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield

/** A change feed that always answers with an empty page, and counts how often it was asked. */
internal class CountingRemoteSyncDataSource(private var failures: Int = 0) : RemoteSyncDataSource {

    var calls = 0
        private set

    /** The most callers ever inside getChanges at once — 2 means two pulls overlapped. */
    var maxConcurrent = 0
        private set
    private var inFlight = 0

    override suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote> {
        calls++
        inFlight++
        if (inFlight > maxConcurrent) maxConcurrent = inFlight
        // Suspend, so an overlapping caller actually gets a chance to observe the collision. A
        // fake that returns without ever yielding cannot show the thing this is asserting.
        yield()
        inFlight--
        if (failures > 0) {
            failures--
            return Result.Error(DataError.Remote.NO_INTERNET)
        }
        return Result.Success(
            SyncChanges(
                cursor = since ?: 0,
                serverNow = null,
                fullResyncRequired = false,
                hasMore = false,
                projects = emptyList(),
                tasks = emptyList(),
                taskIntervals = emptyList(),
                subTasks = emptyList(),
                subTaskIntervals = emptyList(),
                tombstones = emptyList(),
            ),
        )
    }
}

/** A real applier over fakes, for tests that only need one of these to exist. */
internal fun testDeltaSyncApplier(
    remote: RemoteSyncDataSource = CountingRemoteSyncDataSource(),
    timeProvider: TimeProvider = FakeTimeProvider(),
    syncRecency: SyncRecency = SyncRecency(),
    cursorStore: SyncCursorStore = FakeSyncCursorStore(),
    local: FakeLocalProjectDataSource = FakeLocalProjectDataSource(),
) = DeltaSyncApplier(
    remoteSyncDataSource = remote,
    localProjectDataSource = local,
    projectRepository =
        OfflineFirstProjectRepository(
            localProjectDataSource = local,
            remoteProjectDataSource = FakeRemoteProjectDataSource(),
            pendingSyncDataSource = FakePendingSyncDataSource(),
            syncScheduler = FakeSyncScheduler(),
            applicationScope = CoroutineScope(Dispatchers.Unconfined),
            timeProvider = timeProvider,
        ),
    syncCursorStore = cursorStore,
    serverClock = ServerClock(timeProvider, FakeServerClockOffsetStore()),
    timeProvider = timeProvider,
    syncRecency = syncRecency,
)
