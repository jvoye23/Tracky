package com.jvcs.tracky.features.auth.presentation.forgotpassword

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class ForgotPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val authService = FakeAuthService()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Subscribes, since the state is WhileSubscribed, then types the email. */
    private fun TestScope.typeEmail(email: String): ForgotPasswordViewModel {
        val viewModel = ForgotPasswordViewModel(authService)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        viewModel.state.value.emailTextFieldState
            .setTextAndPlaceCursorAtEnd(email)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun anInvalidEmailCannotBeSubmitted() =
        runTest(dispatcher) {
            val viewModel = typeEmail("nope")

            assertThat(viewModel.state.value.canSubmit).isFalse()
            viewModel.onAction(ForgotPasswordAction.OnSubmitClick)
            advanceUntilIdle()
            assertThat(authService.forgotPasswordCalls).isEqualTo(emptyList())
        }

    @Test
    fun aSentLinkShowsTheSuccessAndResendGoesBackToTheForm() =
        runTest(dispatcher) {
            val viewModel = typeEmail("ada@example.com")
            assertThat(viewModel.state.value.canSubmit).isTrue()

            viewModel.onAction(ForgotPasswordAction.OnSubmitClick)
            advanceUntilIdle()

            assertThat(authService.forgotPasswordCalls).containsExactly("ada@example.com")
            assertThat(viewModel.state.value.isEmailSentSuccessfully).isTrue()
            assertThat(viewModel.state.value.isLoading).isFalse()

            viewModel.onAction(ForgotPasswordAction.OnResendClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.isEmailSentSuccessfully).isFalse()
        }

    @Test
    fun aFailureShowsAnError() =
        runTest(dispatcher) {
            authService.forgotPasswordResult = Result.Error(DataError.Remote.NO_INTERNET)
            val viewModel = typeEmail("ada@example.com")

            viewModel.onAction(ForgotPasswordAction.OnSubmitClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.errorText).isNotNull()
            assertThat(viewModel.state.value.isEmailSentSuccessfully).isFalse()
            assertThat(viewModel.state.value.isLoading).isFalse()
        }
}
