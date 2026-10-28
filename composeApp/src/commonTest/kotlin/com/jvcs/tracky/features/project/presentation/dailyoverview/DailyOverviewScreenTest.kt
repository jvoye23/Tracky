package com.jvcs.tracky.features.project.presentation.dailyoverview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.domain.models.Project
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

    private fun ComposeUiTest.showRoot(
        preselected: LocalDate,
        window: DpSize? = null,
        project: Project = trackedProject,
    ) = setContent {
        TrackyTheme {
            if (window == null) {
                Root(preselected, project)
            } else {
                // The adaptive layout reads the window size, so fake one and fill exactly that.
                val density = LocalDensity.current
                val windowInfo =
                    object : WindowInfo {
                        override val isWindowFocused = true
                        override val containerSize = with(density) { window.toSize().toIntSize() }
                    }
                CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
                    Box(modifier = Modifier.requiredSize(window)) { Root(preselected, project) }
                }
            }
        }
    }

    @Composable
    private fun Root(preselected: LocalDate, project: Project) =
        DailyOverviewScreenRoot(
            navigateBack = { backClicks++ },
            viewModel =
                DailyOverviewViewModel(
                    projectId = "project",
                    preselectedDateEpochDay = preselected.toEpochDays(),
                    projectRepository = FakeProjectRepository(project),
                    timeProvider = FixedTimeProvider(Instant.parse("2026-09-10T08:00:00Z")),
                    savedStateHandle = SavedStateHandle(),
                    ioDispatcher = Dispatchers.Unconfined,
                ),
        )

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

    /**
     * A phone on its side: the calendar column is taller than the screen while the day's list
     * beside it is not, so only the calendar can move the top bar.
     */
    @Test
    fun scrollingTheCalendarColumnInLandscapeSlidesTheTopBarAway() =
        runComposeUiTest {
            showRoot(LocalDate(2026, 9, 8), window = DpSize(900.dp, 400.dp))
            val back = onNodeWithContentDescription(text(Res.string.back))
            val calendarColumn =
                onNode(SemanticsMatcher.keyIsDefined(VerticalScrollAxisRange) and !hasScrollToIndexAction())
            val expandedTop = back.getBoundsInRoot().top

            calendarColumn.performTouchInput { swipeUp() }
            waitForIdle()
            assertThat(back.getBoundsInRoot().top).isLessThan(expandedTop)

            calendarColumn.performTouchInput { swipeDown() }
            waitForIdle()
            assertThat(back.getBoundsInRoot().top).isEqualTo(expandedTop)
        }

    /** A phone on its side: the day's list scrolls under the bar and leaves it where it is. */
    @Test
    fun scrollingTheListColumnInPhoneLandscapeKeepsTheTopBarInPlace() =
        runComposeUiTest {
            showRoot(LocalDate(2026, 9, 8), window = DpSize(900.dp, 400.dp), project = busyDayProject)
            val back = onNodeWithContentDescription(text(Res.string.back))
            val list = onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(VerticalScrollAxisRange))
            val expandedTop = back.getBoundsInRoot().top
            // Guards the premise: a list that cannot scroll would leave the bar alone anyway.
            assertThat(list.fetchSemanticsNode().config[VerticalScrollAxisRange].maxValue()).isGreaterThan(0f)

            list.performTouchInput { swipeUp() }
            waitForIdle()

            assertThat(back.getBoundsInRoot().top).isEqualTo(expandedTop)
        }

    /** Portrait, on a day with nothing tracked: the whole page fits, so a swipe leaves the bar be. */
    @Test
    fun aPortraitPageThatFitsKeepsTheTopBarInPlace() =
        runComposeUiTest {
            showRoot(LocalDate(2026, 9, 9), window = DpSize(412.dp, 960.dp))
            val back = onNodeWithContentDescription(text(Res.string.back))
            val page = onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(VerticalScrollAxisRange))
            val expandedTop = back.getBoundsInRoot().top
            // Guards the premise: if the page grew past the window, this test would prove nothing.
            assertThat(page.fetchSemanticsNode().config[VerticalScrollAxisRange].maxValue()).isEqualTo(0f)

            page.performTouchInput { swipeUp() }
            waitForIdle()

            assertThat(back.getBoundsInRoot().top).isEqualTo(expandedTop)
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

        /** A day with more sessions than a phone held sideways shows at once. */
        val busyDayProject =
            project(
                tasks =
                    listOf(
                        task(
                            title = "Design review",
                            intervals =
                                (10 until 22).map { hour ->
                                    interval("2026-09-08T$hour:00:00Z", minutes = 30, id = "busy$hour")
                                },
                        ),
                    ),
            )
    }
}
