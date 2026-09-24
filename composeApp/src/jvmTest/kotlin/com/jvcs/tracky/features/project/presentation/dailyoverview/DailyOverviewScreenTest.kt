package com.jvcs.tracky.features.project.presentation.dailyoverview

import androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.fakes.FakeProjectRepository
import com.jvcs.tracky.features.project.presentation.fakes.FixedTimeProvider
import com.jvcs.tracky.features.project.presentation.fakes.interval
import com.jvcs.tracky.features.project.presentation.fakes.project
import com.jvcs.tracky.features.project.presentation.fakes.task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.back
import tracky.composeapp.generated.resources.calendar_day_untracked
import tracky.composeapp.generated.resources.calendar_jump_to_today
import tracky.composeapp.generated.resources.calendar_next_month
import tracky.composeapp.generated.resources.calendar_previous_month
import tracky.composeapp.generated.resources.calendar_select_year
import tracky.composeapp.generated.resources.day_empty
import tracky.composeapp.generated.resources.day_total
import kotlin.test.Test
import kotlin.time.Instant

/** Drives the real view model through the Root, over a project tracked in August and September. */
@OptIn(ExperimentalTestApi::class)
internal class DailyOverviewScreenTest {

    private var backClicks = 0

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showRoot(preselected: LocalDate) =
        setContent {
            TrackyTheme {
                DailyOverviewScreenRoot(
                    navigateBack = { backClicks++ },
                    viewModel =
                        DailyOverviewViewModel(
                            projectId = "project",
                            preselectedDateEpochDay = preselected.toEpochDays(),
                            projectRepository = FakeProjectRepository(trackedProject),
                            timeProvider = FixedTimeProvider(Instant.parse("2026-09-10T08:00:00Z")),
                            savedStateHandle = SavedStateHandle(),
                            ioDispatcher = Dispatchers.Unconfined,
                        ),
                )
            }
        }

    /** The day's rows sit below the calendar, past the edge of the test window. */
    private fun ComposeUiTest.scrollTo(text: String) =
        onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(VerticalScrollAxisRange))
            .performScrollToNode(hasText(text, substring = true))

    @Test
    fun aTrackedDayListsItsIntervals() =
        runComposeUiTest {
            showRoot(LocalDate(2026, 9, 8))

            scrollTo(text(Res.string.day_total))
            onNodeWithText(text(Res.string.day_total)).assertExists()
            scrollTo("Design review")
        }

    @Test
    fun theCalendarPagesPicksAYearAndJumpsBackToToday() =
        runComposeUiTest {
            showRoot(LocalDate(2026, 9, 8))

            onNodeWithContentDescription(text(Res.string.calendar_previous_month)).performClick()
            waitForIdle()
            onNodeWithContentDescription(text(Res.string.calendar_next_month)).performClick()
            waitForIdle()
            onNodeWithContentDescription(text(Res.string.calendar_select_year)).performClick()
            onNodeWithText("2026").performClick()
            waitForIdle()
            onNodeWithContentDescription(text(Res.string.calendar_jump_to_today)).performClick()

            scrollTo(text(Res.string.day_empty))
        }

    @Test
    fun tappingAnUntrackedDayShowsItEmptyAndBackLeaves() =
        runComposeUiTest {
            showRoot(LocalDate(2026, 9, 8))

            onAllNodes(hasContentDescription(text(Res.string.calendar_day_untracked), substring = true))
                .onFirst()
                .performClick()
            scrollTo(text(Res.string.day_empty))
            onNodeWithContentDescription(text(Res.string.back)).performClick()

            assertThat(backClicks).isEqualTo(1)
        }

    private companion object {
        val trackedProject =
            project(
                tasks =
                    listOf(
                        task(
                            title = "Design review",
                            intervals =
                                listOf(
                                    interval("2026-08-04T09:00:00Z", minutes = 240, id = "aug"),
                                    interval("2026-09-08T09:30:00Z", minutes = 42, id = "sep"),
                                ),
                        ),
                    ),
            )
    }
}
