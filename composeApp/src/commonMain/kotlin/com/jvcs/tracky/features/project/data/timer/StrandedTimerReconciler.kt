package com.jvcs.tracky.features.project.data.timer

import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.StrandedIntervalEntity
import com.jvcs.tracky.core.domain.startup.StartupReconciliation
import com.jvcs.tracky.core.domain.util.TimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Parks every interval left open by a previous process, once, at start-up.
 *
 * [com.jvcs.tracky.core.domain.util.TimeManager] holds the running timer in memory over a monotonic
 * time source, and there is no foreground service, so the timer cannot outlive the process. Any
 * interval still open when this runs therefore has nothing timing it, and how long it really ran is
 * unknowable: the user may have worked for ten minutes and then swiped the app away. Closing it at
 * `now` would bank the whole gap, which is how one day comes to read 75 hours.
 *
 * So the pass banks nothing. It records the row in `stranded_intervals` and clears the task's timer
 * flag, which is enough to make it invisible: an open interval contributes to no total anywhere
 * (see `countedDayIntervals`), and every "what is open" query skips a parked row. The time is held,
 * not counted, until the user says what to do with it.
 */
class StrandedTimerReconciler(
    private val projectDao: ProjectDao,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope
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
        val detectedAt = timeProvider.nowInstant.toEpochMilliseconds()

        // Children first, so a subtask interval is never left counted under a parked parent.
        projectDao.getAllOpenSubTaskIntervals().forEach { interval ->
            park(interval.subTaskIntervalId, isSubTaskInterval = true, detectedAt)
            projectDao.updateSubTaskTimerStatus(interval.parentSubTaskId, false)
        }

        projectDao.getAllOpenTaskIntervals().forEach { interval ->
            park(interval.intervalId, isSubTaskInterval = false, detectedAt)
            projectDao.updateSessionTimerStatus(interval.parentTaskId, false)
        }
    }

    private suspend fun park(intervalId: String, isSubTaskInterval: Boolean, detectedAt: Long) {
        if (projectDao.getStrandedInterval(intervalId) != null) return
        projectDao.upsertStrandedInterval(
            StrandedIntervalEntity(
                intervalId = intervalId,
                isSubTaskInterval = isSubTaskInterval,
                detectedAtEpochMs = detectedAt
            )
        )
    }
}
