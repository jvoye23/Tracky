package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import com.jvcs.tracky.features.project.domain.models.SubTaskInterval
import com.jvcs.tracky.features.project.domain.models.TaskInterval
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/**
 * The project the PDF export design was drawn from, rebuilt as a domain [Project].
 *
 * The resource is a Room-shaped dump (booleans as 0/1, instants as epoch millis), read field by
 * field so the fixture stays independent of the app's DTOs and entities.
 */
object SampleProjectFixture {

    /** The export moment the design shows: 22 Sept 2026, in the afternoon in Berlin. */
    val exportedAt: Instant = Instant.parse("2026-09-22T14:00:00Z")

    fun load(): Project {
        val text =
            checkNotNull(javaClass.getResource("/export/sample_project_export.json")).readText()
        return Json
            .parseToJsonElement(text)
            .jsonObject
            .getValue("project")
            .jsonObject
            .toProject()
    }

    private fun JsonObject.toProject() =
        Project(
            projectId = string("projectId"),
            title = string("title"),
            description = stringOrNull("description"),
            colorArgb = getValue("color").jsonPrimitive.int,
            totalDurationMillis = long("totalDuration"),
            startDateTimeUtc = instant("startDateTimeEpochMs"),
            isFinished = flag("isFinished"),
            endDateTimeUtc = instantOrNull("endDateTimeEpochMs"),
            isArchived = flag("isArchived"),
            projectTasks = array("tasks").map { it.toTask() },
            sortIndex = long("sortIndex"),
        )

    private fun JsonObject.toTask() =
        ProjectTask(
            projectTaskId = string("projectTaskId"),
            title = string("title"),
            description = stringOrNull("description"),
            durationMillis = long("durationMillis"),
            startDateTimeUtc = instant("startDateTimeEpochMs"),
            endDateTimeUtc = instantOrNull("endDateTimeEpochMs"),
            isFinished = flag("isFinished"),
            parentProjectId = string("parentProjectId"),
            isTimerRunning = flag("isTimerRunning"),
            intervals =
                array("intervals").map {
                    TaskInterval(
                        intervalId = it.string("intervalId"),
                        parentTaskId = it.string("parentTaskId"),
                        parentProjectId = it.string("parentProjectId"),
                        startDateTimeUtc = it.instant("startDateTimeEpochMs"),
                        endDateTimeUtc = it.instantOrNull("endDateTimeEpochMs"),
                        durationMillis = it.long("durationMillis"),
                    )
                },
            subTasks = array("subTasks").map { it.toSubTask() },
            sortIndex = long("sortIndex"),
        )

    private fun JsonObject.toSubTask() =
        ProjectSubTask(
            projectSubTaskId = string("projectSubTaskId"),
            parentProjectTaskId = string("parentProjectTaskId"),
            parentProjectId = string("parentProjectId"),
            title = string("title"),
            durationMillis = long("durationMillis"),
            isTimerRunning = flag("isTimerRunning"),
            startDateTimeUtc = instant("startDateTimeEpochMs"),
            endDateTimeUtc = instantOrNull("endDateTimeEpochMs"),
            isFinished = flag("isFinished"),
            subTaskIntervals =
                array("intervals").map {
                    SubTaskInterval(
                        subTaskIntervalId = it.string("subTaskIntervalId"),
                        parentTaskIntervalId = it.string("parentTaskIntervalId"),
                        parentSubTaskId = it.string("parentSubTaskId"),
                        parentProjectId = it.string("parentProjectId"),
                        startDateTimeUtc = it.instant("startDateTimeEpochMs"),
                        endDateTimeUtc = it.instantOrNull("endDateTimeEpochMs"),
                        durationMillis = it.long("durationMillis"),
                    )
                },
            sortIndex = long("sortIndex"),
        )

    private fun JsonObject.array(key: String): List<JsonObject> =
        (get(key) as? JsonArray).orEmpty().map { it.jsonObject }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.stringOrNull(key: String) = get(key)?.jsonPrimitive?.takeIf { it.isString }?.content

    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

    private fun JsonObject.flag(key: String) = getValue(key).jsonPrimitive.int != 0

    private fun JsonObject.instant(key: String) = Instant.fromEpochMilliseconds(long(key))

    private fun JsonObject.instantOrNull(key: String) =
        get(key)?.jsonPrimitive?.longOrNull?.let(Instant::fromEpochMilliseconds)
}
