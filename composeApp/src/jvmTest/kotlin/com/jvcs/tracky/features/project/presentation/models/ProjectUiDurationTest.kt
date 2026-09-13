package com.jvcs.tracky.features.project.presentation.models

import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import com.jvcs.tracky.features.project.presentation.mappers.toProject
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTask
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Durations must not round-trip through their own display string.
 *
 * Editing a project title maps the whole tree UI -> domain and writes it back, so anything the
 * string cannot carry is lost from the database on an edit that had nothing to do with time. The
 * display format is about to drop to whole seconds, which would make that loss a second per task
 * per save on a time tracker.
 */
class ProjectUiDurationTest {

    private val oddDuration = 3_661_234L // 1h 1m 1.234s - not a whole second

    @Test
    fun aTaskKeepsItsMillisecondsAcrossTheUiRoundTrip() {
        val domain = task(id = "t1").copy(durationMillis = oddDuration)

        val back = domain.toProjectTaskUi().toProjectTask(parentProjectId = "project")

        assertEquals(oddDuration, back.durationMillis)
    }

    @Test
    fun aSubTaskKeepsItsMillisecondsAcrossTheUiRoundTrip() {
        val domain = task(id = "t1", subTasks = listOf(subTask(id = "s1").copy(durationMillis = oddDuration)))

        val back = domain.toProjectTaskUi().toProjectTask(parentProjectId = "project")

        assertEquals(oddDuration, back.subTasks!!.single().durationMillis)
    }

    @Test
    fun aProjectTotalKeepsItsMillisecondsAcrossTheUiRoundTrip() {
        val domain = project().copy(totalDurationMillis = oddDuration)

        assertEquals(oddDuration, domain.toProjectUi().toProject().totalDurationMillis)
    }

    @Test
    fun aTasksDisplayedDurationIsItsSubtasksSummedToTheMillisecond() {
        val ui = task(
            id = "t1",
            subTasks = listOf(
                subTask(id = "s1").copy(durationMillis = 1_500L),
                subTask(id = "s2").copy(durationMillis = 2_499L)
            )
        ).toProjectTaskUi()

        // 3999 ms, not 3000: summing truncated strings loses a second per subtask.
        assertEquals(3_999L, ui.displayDurationMillis)
    }

    @Test
    fun aProjectTotalSumsItsTasksToTheMillisecond() {
        val ui = project(
            tasks = listOf(
                task(id = "t1").copy(durationMillis = 1_500L),
                task(id = "t2").copy(durationMillis = 2_499L)
            )
        ).toProjectUi()

        assertEquals(3_999L, ui.totalProjectDurationMillis)
    }
}
