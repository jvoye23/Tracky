package com.jvcs.tracky.features.project.domain.interval

import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * When this project last banked time: the latest end among closed task intervals that tracked any.
 *
 * Task intervals only, for the reason the export gives — subtask intervals nest inside them. Open
 * intervals have banked nothing yet, and zero-length ones add no day to the report, so neither
 * counts. The list screens get the same number from TaskIntervalDao's aggregate query; keep the two
 * filters in step.
 */
internal fun List<TaskInterval>.lastTrackedEnd(): Instant? =
    filter { it.durationMillis > 0L }
        .mapNotNull { it.endDateTimeUtc }
        .maxOrNull()

/**
 * The local day [lastTrackedEnd] falls on, agreeing with the export's last active date.
 *
 * One millisecond back, because an interval ending at exactly local midnight tracked nothing on the
 * day that midnight starts — [splitAcrossLocalDays] files it under the day before, and so does this.
 */
internal fun Instant.lastActiveLocalDate(timeZone: TimeZone): LocalDate =
    (this - 1.milliseconds).toLocalDateTime(timeZone).date
