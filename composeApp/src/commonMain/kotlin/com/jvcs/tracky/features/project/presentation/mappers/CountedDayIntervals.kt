package com.jvcs.tracky.features.project.presentation.mappers

import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Every interval of this project that counts, flattened, dated and cut at midnight.
 *
 * Three rules live here and nowhere else:
 *
 * 1. **Subtask intervals nest inside their parent task's intervals**, so counting both bills the
 *    same stretch of wall clock twice. A task owning subtasks contributes only their intervals;
 *    a task without subtasks contributes its own. Mirrors
 *    [com.jvcs.tracky.features.project.presentation.models.ProjectTaskUi.displayDuration].
 * 2. **Open intervals are dropped.** Elapsed time is not banked into `durationMillis` until the
 *    timer stops, so a running interval has nothing to count and no end to render. The same drop
 *    is what keeps a stranded timer out of every total while it waits to be reviewed.
 * 3. **An interval is split at every local midnight it crosses**, each day taking its own share —
 *    so a stretch from 23:40 to 00:20 puts 20 minutes on each day, and no day total can exceed 24
 *    hours. See [splitAcrossLocalDays] for why the shares are apportioned rather than recomputed.
 *
 * Pure: [timeZone] is passed in rather than read from the system, because which local day an
 * interval lands on is the only thing the zone decides, and that makes all three rules testable
 * without a clock.
 *
 * Zero-length intervals are kept. Whether a zero counts as activity is a display decision: the
 * strip drops such a day, while the daily list still has an interval to show.
 */
internal fun Project.countedDayIntervals(timeZone: TimeZone): List<CountedInterval> =
    projectTasks.orEmpty().flatMap { it.countedDayIntervals(timeZone) }

/**
 * The same three rules for a single task, which is all Task Detail has in hand.
 *
 * Rule 1 matters just as much there: a task that owns subtasks is timed through them, and counting
 * its own enclosing intervals as well would double every figure on the screen.
 */
@OptIn(ExperimentalTime::class)
internal fun ProjectTask.countedDayIntervals(timeZone: TimeZone): List<CountedInterval> =
    buildList {
        // Closes over the task, so both interval kinds are recorded against the owning task's title —
        // the list shows that on the card and the subtask beside the time range.
        fun record(
            intervalId: String,
            startedAt: Instant,
            endedAt: Instant?,
            durationMillis: Long,
            subTaskTitle: String?,
        ) {
            // Rule 2.
            val ended = endedAt ?: return
            // Rule 3.
            splitAcrossLocalDays(startedAt, ended, durationMillis, timeZone).forEach { slice ->
                add(
                    CountedInterval(
                        intervalId = intervalId,
                        date = slice.date,
                        start = slice.start,
                        end = slice.end,
                        endsAtMidnight = slice.endsAtMidnight,
                        durationMillis = slice.durationMillis,
                        sliceIndex = slice.sliceIndex,
                        sliceCount = slice.sliceCount,
                        taskId = projectTaskId,
                        taskTitle = title,
                        subTaskTitle = subTaskTitle,
                    ),
                )
            }
        }

        // Rule 1.
        val subTasks = subTasks.orEmpty()
        if (subTasks.isEmpty()) {
            intervals.forEach {
                record(it.intervalId, it.startDateTimeUtc, it.endDateTimeUtc, it.durationMillis, null)
            }
        } else {
            subTasks.forEach { subTask ->
                subTask.subTaskIntervals.forEach {
                    record(
                        it.subTaskIntervalId,
                        it.startDateTimeUtc,
                        it.endDateTimeUtc,
                        it.durationMillis,
                        subTask.title,
                    )
                }
            }
        }
    }

/**
 * One stretch of tracked time the app is willing to count, resolved to the local day it belongs
 * to and to the titles that name it.
 *
 * Every per-day view derives from this. The "Per day" strip, the calendar heat map, the daily
 * overview's interval list and Task Detail's daily statistics all render the same underlying
 * minutes — a day's tile and that day's `TOTAL` sit centimetres apart on screen — so deriving the
 * set separately in each mapper is how they drift apart.
 *
 * An interval that crosses midnight yields one of these per day, not one in total.
 */
internal data class CountedInterval(
    /**
     * The real interval row. Not unique across a multi-day interval's slices — two of them share
     * it — but unique within any one day, which is all a per-day list needs, and it stays the id a
     * delete or an edit would act on.
     */
    val intervalId: String,
    /** The local day this slice falls on. */
    val date: LocalDate,
    val start: LocalTime,
    /** Wall-clock end. `00:00` when [endsAtMidnight] — see [DaySlice]. */
    val end: LocalTime,
    val endsAtMidnight: Boolean,
    val durationMillis: Long,
    /** 0-based position within the interval; `0` of `1` when it did not cross midnight. */
    val sliceIndex: Int,
    val sliceCount: Int,
    val taskId: String,
    val taskTitle: String,
    /** The subtask that owns this interval, or `null` when the task timed it directly. */
    val subTaskTitle: String?,
)
