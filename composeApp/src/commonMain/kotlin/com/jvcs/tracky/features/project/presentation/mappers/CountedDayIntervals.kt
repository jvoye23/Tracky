package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One stretch of tracked time the app is willing to count, resolved to the local day it belongs
 * to and to the titles that name it.
 *
 * Every per-day view derives from this. The "Per day" strip, the calendar heat map and the daily
 * overview's interval list all render the same underlying minutes — a day's tile and that day's
 * `TOTAL` sit centimetres apart on screen — so deriving the set separately in each mapper is how
 * they drift apart.
 */
internal data class CountedInterval(
    val intervalId: String,
    /** The local day this is billed to: the day it *started* on. See [countedDayIntervals]. */
    val date: LocalDate,
    val start: LocalTime,
    /** Wall-clock end. On an interval running past midnight this reads earlier than [start]. */
    val end: LocalTime,
    val durationMillis: Long,
    val taskId: String,
    val taskTitle: String,
    /** The subtask that owns this interval, or `null` when the task timed it directly. */
    val subTaskTitle: String?
)

/**
 * Every interval of this project that counts, flattened and dated.
 *
 * Three rules live here and nowhere else:
 *
 * 1. **Subtask intervals nest inside their parent task's intervals**, so counting both bills the
 *    same stretch of wall clock twice. A task owning subtasks contributes only their intervals;
 *    a task without subtasks contributes its own. Mirrors
 *    [com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi.displayDuration].
 * 2. **Open intervals are dropped.** Elapsed time is not banked into `durationMillis` until the
 *    timer stops, so a running interval has nothing to count and no end to render.
 * 3. **An interval belongs to the local day it started on**, never split at midnight — a stretch
 *    from 23:40 to 00:20 counts entirely against the earlier day.
 *
 * Pure: [timeZone] is passed in rather than read from the system, because which local day an
 * interval lands on is the only thing the zone decides, and that makes all three rules testable
 * without a clock.
 *
 * Zero-length intervals are kept. Whether a zero counts as activity is a display decision: the
 * strip drops such a day, while the daily list still has an interval to show.
 */
@OptIn(ExperimentalTime::class)
internal fun Project.countedDayIntervals(timeZone: TimeZone): List<CountedInterval> = buildList {
    projectTasks.orEmpty().forEach { task ->
        // Closes over the task, so both interval kinds are recorded against the owning task's
        // title — the list shows that on the card and the subtask beside the time range.
        fun record(
            intervalId: String,
            startedAt: Instant,
            endedAt: Instant?,
            durationMillis: Long,
            subTaskTitle: String?
        ) {
            // Rule 2.
            val ended = endedAt ?: return
            val startedLocal = startedAt.toLocalDateTime(timeZone)
            add(
                CountedInterval(
                    intervalId = intervalId,
                    // Rule 3: the start's day, whatever day the end falls on.
                    date = startedLocal.date,
                    start = startedLocal.time,
                    end = ended.toLocalDateTime(timeZone).time,
                    durationMillis = durationMillis,
                    taskId = task.projectTaskId,
                    taskTitle = task.title,
                    subTaskTitle = subTaskTitle
                )
            )
        }

        // Rule 1.
        val subTasks = task.subTasks.orEmpty()
        if (subTasks.isEmpty()) {
            task.intervals.forEach {
                record(it.intervalId, it.startDateTimeUtc, it.endDateTimeUtc, it.durationMillis, null)
            }
        } else {
            subTasks.forEach { subTask ->
                subTask.subTaskIntervals.forEach {
                    record(
                        it.subTaskIntervalId, it.startDateTimeUtc, it.endDateTimeUtc,
                        it.durationMillis, subTask.title
                    )
                }
            }
        }
    }
}
