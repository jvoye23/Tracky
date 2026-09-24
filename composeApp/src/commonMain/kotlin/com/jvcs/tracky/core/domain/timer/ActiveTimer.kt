package com.jvcs.tracky.core.domain.timer

import kotlin.time.Instant

/**
 * The one interval the server considers open for this user, across every device they own.
 *
 * The product rule is exactly one running timer per user, globally, and that rule cannot be
 * enforced by the last-write-wins the client uses for everything else: two devices that were both
 * offline each hold an open interval, and neither can know about the other's. The server is the
 * only party that sees both, so it arbitrates — and this is its answer.
 *
 * It answers *which* interval is open and when it started, and deliberately nothing else. The
 * elapsed number stays derived (`banked + (now - startedAt)`) on each device independently, which
 * is what keeps a timer correct while offline and stops a transition needing a message per second.
 *
 * The counterpart to [com.jvcs.tracky.features.project.domain.timer.RunningTimer], which is what
 * the *local* database says is running. The two disagree exactly while a change is in flight.
 */
data class ActiveTimer(
    val intervalId: String,
    val kind: ActiveTimerKind,
    val parentProjectId: String,
    /** The enclosing task, for a subtask timer as well — starting one opens its parent's interval. */
    val parentTaskId: String,
    val parentSubTaskId: String?,
    /** The task interval a subtask timer runs inside. Non-null exactly when [kind] is SUB_TASK. */
    val parentTaskIntervalId: String?,
    val startedAt: Instant,
    /**
     * The installation that opened it. Null reads as "this device" — see
     * [com.jvcs.tracky.core.domain.device.DeviceIdProvider]; it is how a timer to adopt and display
     * is told apart from a crash to recover from.
     */
    val startedByDeviceId: String?,
)

/** Which table the open interval lives in. The wire spells these `task` and `sub_task`. */
enum class ActiveTimerKind { TASK, SUB_TASK }

/**
 * A request to make [intervalId] the running timer.
 *
 * The client generates the id, as it does for every entity, which is what lets the call be retried
 * safely: the server treats a repeat of the interval that is already active as a no-op rather than
 * restarting it.
 */
data class StartActiveTimer(
    val intervalId: String,
    val kind: ActiveTimerKind,
    val parentTaskId: String,
    val parentSubTaskId: String?,
    val parentTaskIntervalId: String?,
    val startedAt: Instant,
    val deviceId: String,
)
