package com.jvcs.tracky.features.project.presentation.task_detail.model

/**
 * One row of Task Detail's sessions table: a single counted interval slice. An interval that
 * crosses midnight yields one row per day, and those rows share [intervalId].
 */
data class DailyStatistic(
    val intervalId: String,
    val formattedDate: String,
    val formattedStartTime: String,
    val formattedEndTime: String,
    val formattedDuration: String,
)
