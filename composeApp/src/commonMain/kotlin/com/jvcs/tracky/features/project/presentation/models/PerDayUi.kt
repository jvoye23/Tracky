package com.jvcs.tracky.features.project.presentation.models

/**
 * One day in the "Per day" activity strip.
 *
 * All labels arrive already localised and formatted — the strip renders exactly what
 * it is given. [trackedMillis] is the only numeric field and exists solely to derive
 * the heat intensity of a day relative to the busiest day in the same strip.
 */
data class PerDayUi(
    /** Abbreviated weekday, e.g. "Tue". */
    val weekdayLabel: String,
    /** Day and month, e.g. "25.8" — zero-padded day, unpadded month. */
    val dateLabel: String,
    /** Display duration such as "00:52:12", or `null` when nothing was tracked that day. */
    val formattedDuration: String?,
    /** Total tracked time for the day; drives the cell tint. */
    val trackedMillis: Long
)


data class PerDayStripUi(
    val days: List<PerDayUi>,
    /** Weekday plus date of the busiest day, e.g. "Sat 05.9"; `null` when nothing was tracked. */
    val busiestDayLabel: String?
)
