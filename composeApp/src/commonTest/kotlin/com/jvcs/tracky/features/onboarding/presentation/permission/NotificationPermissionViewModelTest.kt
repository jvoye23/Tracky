package com.jvcs.tracky.features.onboarding.presentation.permission

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.notification.FakeTimerNotificationPermissionRequester
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** The permission step asks once and moves on whatever the answer: it is never a barrier. */
internal class NotificationPermissionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val requester = FakeTimerNotificationPermissionRequester()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun allowingAsksAndThenFinishes() =
        runTest(dispatcher) {
            val viewModel = NotificationPermissionViewModel(requester)

            viewModel.events.test {
                viewModel.onAction(NotificationPermissionAction.OnAllowClick)
                assertThat(awaitItem()).isEqualTo(NotificationPermissionEvent.Finished)
            }
            assertThat(requester.requests).isEqualTo(1)
        }

    @Test
    fun aRefusalFinishesToo() =
        runTest(dispatcher) {
            requester.answer = TimerNotificationPermission.DeniedAlways
            val viewModel = NotificationPermissionViewModel(requester)

            viewModel.events.test {
                viewModel.onAction(NotificationPermissionAction.OnAllowClick)
                assertThat(awaitItem()).isEqualTo(NotificationPermissionEvent.Finished)
            }
        }

    @Test
    fun skippingFinishesWithoutAsking() =
        runTest(dispatcher) {
            val viewModel = NotificationPermissionViewModel(requester)

            viewModel.events.test {
                viewModel.onAction(NotificationPermissionAction.OnSkipClick)
                assertThat(awaitItem()).isEqualTo(NotificationPermissionEvent.Finished)
            }
            assertThat(requester.requests).isEqualTo(0)
        }

    @Test
    fun theScreenShowsTheRequestWhileTheSystemDialogIsUpAndIgnoresASecondTap() =
        runTest(dispatcher) {
            val answer = CompletableDeferred<Unit>()
            requester.pending = answer
            val viewModel = NotificationPermissionViewModel(requester)

            viewModel.onAction(NotificationPermissionAction.OnAllowClick)
            viewModel.onAction(NotificationPermissionAction.OnAllowClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.isRequesting).isTrue()

            answer.complete(Unit)
            viewModel.events.test {
                assertThat(awaitItem()).isEqualTo(NotificationPermissionEvent.Finished)
            }
            assertThat(viewModel.state.value.isRequesting).isFalse()
            assertThat(requester.requests).isEqualTo(1)
        }
}
