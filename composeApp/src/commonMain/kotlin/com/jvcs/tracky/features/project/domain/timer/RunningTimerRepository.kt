package com.jvcs.tracky.features.project.domain.timer

import kotlinx.coroutines.flow.Flow

/**
 * The reactive answer to "what is being timed, and what is it called".
 *
 * Emits null whenever nothing is running. Parked intervals never count as running - they are open
 * rows that nothing is timing, and [StrandedTimerRepository] owns them instead.
 */
interface RunningTimerRepository {

    fun observeRunningTimer(): Flow<RunningTimer?>
}
