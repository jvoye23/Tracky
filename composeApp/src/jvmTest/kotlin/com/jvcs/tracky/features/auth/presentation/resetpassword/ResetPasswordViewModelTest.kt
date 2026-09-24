package com.jvcs.tracky.features.auth.presentation.resetpassword

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
import com.jvcs.tracky.designsystem.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_reset_password_token_invalid
import tracky.composeapp.generated.resources.error_same_password
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class ResetPasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val authService = FakeAuthService()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Subscribes, since the state is WhileSubscribed, then types the new password. */
    private fun TestScope.typePassword(password: String): ResetPasswordViewModel {
        val viewModel = ResetPasswordViewModel(authService, token = TOKEN)
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        viewModel.state.value.passwordTextState
            .setTextAndPlaceCursorAtEnd(password)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        return viewModel
    }

    private fun TestScope.submitFailingWith(error: DataError.Remote): ResetPasswordViewModel {
        authService.resetPasswordResult = Result.Error(error)
        val viewModel = typePassword(VALID_PASSWORD)
        viewModel.onAction(ResetPasswordAction.OnSubmitClick)
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun aWeakPasswordCannotBeSubmitted() =
        runTest(dispatcher) {
            val viewModel = typePassword("weak")

            assertThat(viewModel.state.value.canSubmit).isFalse()
            viewModel.onAction(ResetPasswordAction.OnSubmitClick)
            advanceUntilIdle()
            assertThat(authService.resetPasswordCalls).isEqualTo(emptyList())
        }

    @Test
    fun aStrongPasswordIsSentWithTheLinksToken() =
        runTest(dispatcher) {
            val viewModel = typePassword(VALID_PASSWORD)

            viewModel.onAction(ResetPasswordAction.OnSubmitClick)
            advanceUntilIdle()

            assertThat(authService.resetPasswordCalls).containsExactly(VALID_PASSWORD to TOKEN)
            assertThat(viewModel.state.value.isResetSuccessful).isTrue()
            assertThat(viewModel.state.value.isLoading).isFalse()
        }

    @Test
    fun anExpiredLinkIsNamedAsSuch() =
        runTest(dispatcher) {
            assertThat(submitFailingWith(DataError.Remote.UNAUTHORIZED).state.value.errorText)
                .isEqualTo(UiText.Resource(Res.string.error_reset_password_token_invalid))
        }

    @Test
    fun reusingTheOldPasswordIsNamedAsSuch() =
        runTest(dispatcher) {
            assertThat(submitFailingWith(DataError.Remote.CONFLICT).state.value.errorText)
                .isEqualTo(UiText.Resource(Res.string.error_same_password))
        }

    @Test
    fun anyOtherFailureShowsAnError() =
        runTest(dispatcher) {
            val viewModel = submitFailingWith(DataError.Remote.NO_INTERNET)

            assertThat(viewModel.state.value.errorText).isNotNull()
            assertThat(viewModel.state.value.isResetSuccessful).isFalse()
        }

    @Test
    fun thePasswordVisibilityToggles() =
        runTest(dispatcher) {
            val viewModel = typePassword("")

            viewModel.onAction(ResetPasswordAction.OnTogglePasswordVisibilityClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.isPasswordVisible).isTrue()
        }

    private companion object {
        const val TOKEN = "reset-token"
        const val VALID_PASSWORD = "Secret123!"
    }
}
