package com.jvcs.tracky.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_intervals",
    foreignKeys = [
        ForeignKey(
            entity = ProjectTaskEntity::class,
            parentColumns = ["projectTaskId"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE // Deleting a task will delete the time it tracked
        ),
        // parentProjectId is denormalised rather than derived through the task on purpose: it makes
        // the project the direct parent of its intervals, so a project delete cascades to them even
        // if the task rows were ever to disappear on their own.
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["parentProjectId"],
            onDelete = ForeignKey.CASCADE // Deleting a project will delete all associated intervals
        )
    ],
    // Indexing the foreign keys is a best practice for performance
    indices = [Index(value = ["parentTaskId"]), Index(value = ["parentProjectId"])]
)
data class TaskIntervalEntity(
    @PrimaryKey(autoGenerate = false)
    val intervalId: String,
    val parentTaskId: String, // The Foreign Key link to the owning task
    val parentProjectId: String, // The Foreign Key link to the owning project
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val durationMillis: Long,
    // Which installation opened this interval. Null means unknown, which every row written before
    // multi-device sync is, and which is read as "this device" — the behaviour those rows already
    // had. See DeviceIdProvider: an open interval this device started is a crash to recover from,
    // one another device started is a timer to display.
    val startedByDeviceId: String? = null
)
