package com.jvcs.tracky.features.project.presentation.models

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.subTask
import com.jvcs.tracky.features.project.presentation.fakes.task
import com.jvcs.tracky.features.project.presentation.mappers.toProjectSubTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTask
import com.jvcs.tracky.features.project.presentation.mappers.toProjectTaskUi
import com.jvcs.tracky.features.project.presentation.mappers.toProjectUi
import kotlin.test.Test

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

        assertThat(back.durationMillis).isEqualTo(oddDuration)
    }

    @Test
    fun aSubTaskKeepsItsMillisecondsAcrossTheUiRoundTrip() {
        val domain = task(id = "t1", subTasks = listOf(subTask(id = "s1").copy(durationMillis = oddDuration)))

        val back = domain.toProjectTaskUi().toProjectTask(parentProjectId = "project")

        assertThat(back.subTasks!!.single().durationMillis).isEqualTo(oddDuration)
    }

    @Test
    fun aTasksDisplayedDurationIsItsSubtasksSummedToTheMillisecond() {
        val ui =
            task(
                id = "t1",
                subTasks =
                    listOf(
                        subTask(id = "s1").copy(durationMillis = 1_500L),
                        subTask(id = "s2").copy(durationMillis = 2_499L),
                    ),
            ).toProjectTaskUi()

        // 3999 ms, not 3000: summing truncated strings loses a second per subtask.
        assertThat(ui.displayDurationMillis).isEqualTo(3_999L)
    }

    @Test
    fun aProjectTotalSumsItsTasksToTheMillisecond() {
        val ui =
            project(
                tasks =
                    listOf(
                        task(id = "t1").copy(durationMillis = 1_500L),
                        task(id = "t2").copy(durationMillis = 2_499L),
                    ),
            ).toProjectUi()

        assertThat(ui.totalProjectDurationMillis).isEqualTo(3_999L)
    }

    /**
     * The display string used to be stored beside the number and written by hand at four call
     * sites. A copy that set one half and forgot the other put a number and a string on screen that
     * disagreed, with nothing to catch it. These pin the string to the number instead.
     */
    @Test
    fun aTasksFormattedDurationFollowsACopyThatSetsOnlyTheMilliseconds() {
        val ui = task(id = "t1").toProjectTaskUi().copy(durationMillis = 3_661_000L)

        assertThat(ui.formattedDuration).isEqualTo("01:01:01")
    }

    @Test
    fun aSubTasksFormattedDurationFollowsACopyThatSetsOnlyTheMilliseconds() {
        val ui = subTask(id = "s1").toProjectSubTaskUi().copy(durationMillis = 3_661_000L)

        assertThat(ui.formattedDuration).isEqualTo("01:01:01")
    }

    @Test
    fun aProjectsTotalDurationFollowsACopyThatSetsOnlyTheMilliseconds() {
        val ui = project().toProjectUi().copy(totalDurationMillis = 3_661_000L)

        assertThat(ui.totalDuration).isEqualTo("01:01:01")
    }
}
