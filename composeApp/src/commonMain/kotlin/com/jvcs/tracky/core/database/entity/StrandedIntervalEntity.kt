package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An interval the app found open at start-up, with nothing tracking it.
 *
 * The timer lives only in memory ([com.jvcs.tracky.core.domain.util.TimeManager]), so every
 * interval still open when the process starts was stranded by the last one dying. How long it
 * really ran is unknowable — closing it at `now` would bank every hour since, which is how a single
 * day comes to read 75 hours. So it is parked here instead, and the user decides.
 *
 * Deliberately a table of its own rather than a column on the interval:
 * `OfflineFirstIntervalRepository` writes the server's echo of a pushed interval straight back over
 * the local row, and the wire carries no such field, so a column would be silently nulled and the
 * row re-armed. Nothing syncs this table.
 *
 * No foreign key either: the row is deleted when the interval is resolved, and a dangling id left
 * behind by a cascade is harmless — every read joins back to a real interval.
 */
@Entity(tableName = "stranded_intervals")
data class StrandedIntervalEntity(
    @PrimaryKey(autoGenerate = false)
    val intervalId: String,
    /** True when [intervalId] names a `sub_task_intervals` row rather than a `task_intervals` one. */
    val isSubTaskInterval: Boolean,
    /**
     * When the app noticed. Also the proposed end: freezing it here is what stops the duration the
     * dialog offers from growing while the dialog sits unanswered.
     */
    val detectedAtEpochMs: Long
)
