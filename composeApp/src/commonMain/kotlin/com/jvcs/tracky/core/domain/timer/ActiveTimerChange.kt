package com.jvcs.tracky.core.domain.timer

import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlin.time.Instant

/**
 * What the server did with a start or a stop.
 *
 * Both endpoints answer with every interval row they touched, not just the one named in the
 * request. That is the point of the resource: a device that supersedes another device's timer
 * learns *in the same response* which interval was closed and at what instant, and writes it
 * locally instead of waiting up to five minutes for the next delta to say so.
 */
sealed interface ActiveTimerChange {

    /** What is running now, as far as the server is concerned. Null when nothing is. */
    val active: ActiveTimer?

    /** The server's own clock, a sample for [com.jvcs.tracky.core.domain.util.ServerClock]. */
    val serverNow: Instant?

    /**
     * The server accepted the change.
     *
     * The touched rows arrive split by level because they land in different tables, and the caller
     * would otherwise have to re-derive the split from the wire's `kind` discriminator.
     *
     * Both lists can be empty on a legitimate success: replaying a stop the server has already
     * applied returns `200` with nothing touched, because nothing changed. A caller that reads an
     * empty echo as "the stop did not take" would leave the local interval open forever on every
     * retry.
     */
    data class Applied(
        override val active: ActiveTimer?,
        val touchedTaskIntervals: List<TaskInterval>,
        val touchedSubTaskIntervals: List<SubTaskInterval>,
        override val serverNow: Instant?,
    ) : ActiveTimerChange

    /**
     * The server refused: the compare-and-swap lost, or the interval was already closed.
     *
     * Not a transport failure and never worth retrying as-is — the request described an intention
     * that is no longer true. [active] is what is actually running, so the caller can converge on
     * it immediately rather than guessing or waiting for a pull.
     */
    data class Rejected(override val active: ActiveTimer?, override val serverNow: Instant?) : ActiveTimerChange
}
