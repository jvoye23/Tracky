package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.dao.StrandedIntervalDao
import com.jvcs.tracky.core.database.dao.SubTaskIntervalDao
import com.jvcs.tracky.core.database.dao.TaskDao
import com.jvcs.tracky.core.database.dao.TaskIntervalDao
import com.jvcs.tracky.core.database.entity.StrandedIntervalEntity
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.startup.StartupReconciliation
import com.jvcs.tracky.core.domain.util.ServerClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Parks the intervals *this device* left open by a previous process, once, at start-up.
 *
 * [com.jvcs.tracky.core.domain.util.TimeManager] holds the running timer in memory over a monotonic
 * time source, so a timer this device started cannot outlive its process. An interval it left open
 * therefore has nothing timing it, and how long it really ran is unknowable: the user may have
 * worked for ten minutes and then swiped the app away. Closing it at `now` would bank the whole
 * gap, which is how one day comes to read 75 hours.
 *
 * So the pass banks nothing. It records the row in `stranded_intervals` and clears the task's timer
 * flag, which is enough to make it invisible: an open interval contributes to no total anywhere
 * (see `countedDayIntervals`), and every "what is open" query skips a parked row. The time is held,
 * not counted, until the user says what to do with it.
 *
 * **Scoped to this device.** Once the tree syncs, an open interval may have been started on the
 * user's other phone and be running there right now. That is a timer to adopt and display, not
 * wreckage to interrogate the user about — so the queries filter on
 * [com.jvcs.tracky.core.database.entity.TaskIntervalEntity.startedByDeviceId], and a row with no id
 * counts as this device's, which is what every row written before sync existed means. Crash
 * recovery is unchanged: a crash here leaves this device's rows open, and those are exactly the
 * ones still selected.
 */
class StrandedTimerReconciler(
    private val projectDao: ProjectDao,
    private val taskDao: TaskDao,
    private val subTaskIntervalDao: SubTaskIntervalDao,
    private val taskIntervalDao: TaskIntervalDao,
    private val strandedIntervalDao: StrandedIntervalDao,
    /**
     * The corrected clock, because [detectedAtEpochMs] is subtracted from an interval's
     * `startDateTimeEpochMs` to work out the duration the dialog offers, and that start is written
     * on the corrected clock. Two bases here would offer the user the wrong number of minutes.
     */
    private val serverClock: ServerClock,
    private val deviceIdProvider: DeviceIdProvider,
    private val applicationScope: CoroutineScope,
) : StartupReconciliation {

    private val reconciled = CompletableDeferred<Unit>()
    private var started = false

    override suspend fun awaitReconciled() = reconciled.await()

    fun start() {
        if (started) return
        started = true
        applicationScope.launch {
            try {
                reconcile()
            } finally {
                // Even a failed pass has to open the gate, or every timer start blocks forever.
                // A missed parking banks a wrong duration; a jammed gate breaks the whole app.
                reconciled.complete(Unit)
            }
        }
    }

    /**
     * Visible for testing, and safe to run twice: a row already parked keeps its original
     * [StrandedIntervalEntity.detectedAtEpochMs], so the duration the dialog offers never grows.
     */
    internal suspend fun reconcile() {
        val detectedAt = serverClock.now().toEpochMilliseconds()
        val deviceId = deviceIdProvider.deviceId()

        // Children first, so a subtask interval is never left counted under a parked parent.
        subTaskIntervalDao.getAllOpenSubTaskIntervalsForDevice(deviceId).forEach { interval ->
            park(interval.subTaskIntervalId, isSubTaskInterval = true, detectedAt)
            projectDao.updateSubTaskTimerStatus(interval.parentSubTaskId, false)
        }

        taskIntervalDao.getAllOpenTaskIntervalsForDevice(deviceId).forEach { interval ->
            park(interval.intervalId, isSubTaskInterval = false, detectedAt)
            taskDao.updateSessionTimerStatus(interval.parentTaskId, false)
        }
    }

    private suspend fun park(
        intervalId: String,
        isSubTaskInterval: Boolean,
        detectedAt: Long,
    ) {
        if (strandedIntervalDao.getStrandedInterval(intervalId) != null) return
        strandedIntervalDao.upsertStrandedInterval(
            StrandedIntervalEntity(
                intervalId = intervalId,
                isSubTaskInterval = isSubTaskInterval,
                detectedAtEpochMs = detectedAt,
            ),
        )
    }
}
