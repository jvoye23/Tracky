package com.jvcs.tracky.features.auth.presentation.login

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.core.domain.auth.authInfo
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
import tracky.composeapp.generated.resources.error_email_not_verified
import tracky.composeapp.generated.resources.error_invalid_credentials
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val authService = FakeAuthService()
    private val sessionStorage = FakeSessionStorage(initial = null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = LoginViewModel(authService, sessionStorage, SocialAuthProvider())

    /** State is WhileSubscribed: without a collector it never leaves its initial value. */
    private fun TestScope.subscribe(viewModel: LoginViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
    }

    /** Subscribes, so onStart wires the validation, then types the credentials. */
    private fun TestScope.typeCredentials(
        viewModel: LoginViewModel,
        email: String,
        password: String,
    ) {
        subscribe(viewModel)
        viewModel.state.value.emailTextFieldState
            .setTextAndPlaceCursorAtEnd(email)
        viewModel.state.value.passwordTextFieldState
            .setTextAndPlaceCursorAtEnd(password)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    @Test
    fun loginIsDisabledUntilTheEmailIsValidAndThePasswordIsFilledIn() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            typeCredentials(viewModel, email = "not-an-email", password = "secret")
            assertThat(viewModel.state.value.canLogin).isFalse()

            viewModel.state.value.emailTextFieldState
                .setTextAndPlaceCursorAtEnd("user@example.com")
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()
            assertThat(viewModel.state.value.canLogin).isTrue()
        }

    @Test
    fun aClickWhileLoginIsDisabledSendsNothing() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            typeCredentials(viewModel, email = "user@example.com", password = "")

            viewModel.onAction(LoginAction.OnLoginClick)
            advanceUntilIdle()

            assertThat(authService.loginCalls).isEqualTo(emptyList())
        }

    @Test
    fun aSuccessfulLoginStoresTheSessionAndNavigates() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            typeCredentials(viewModel, email = "user@example.com", password = "secret")

            viewModel.events.test {
                viewModel.onAction(LoginAction.OnLoginClick)
                assertThat(awaitItem()).isEqualTo(LoginEvent.Success)
            }

            assertThat(authService.loginCalls).containsExactly("user@example.com" to "secret")
            assertThat(sessionStorage.current).isEqualTo(authInfo())
            assertThat(viewModel.state.value.isLoggingIn).isFalse()
        }

    @Test
    fun wrongCredentialsSayWhatIsWrong() =
        runTest(dispatcher) {
            authService.loginResult = Result.Error(DataError.Remote.UNAUTHORIZED)
            val viewModel = viewModel()
            typeCredentials(viewModel, email = "user@example.com", password = "wrong")

            viewModel.onAction(LoginAction.OnLoginClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error)
                .isEqualTo(UiText.Resource(Res.string.error_invalid_credentials))
            assertThat(sessionStorage.current).isNull()
            assertThat(viewModel.state.value.isLoggingIn).isFalse()
        }

    @Test
    fun anUnverifiedEmailIsToldApartFromWrongCredentials() =
        runTest(dispatcher) {
            authService.loginResult = Result.Error(DataError.Remote.FORBIDDEN)
            val viewModel = viewModel()
            typeCredentials(viewModel, email = "user@example.com", password = "secret")

            viewModel.onAction(LoginAction.OnLoginClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error)
                .isEqualTo(UiText.Resource(Res.string.error_email_not_verified))
        }

    @Test
    fun anyOtherFailureShowsTheGeneralError() =
        runTest(dispatcher) {
            authService.loginResult = Result.Error(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            typeCredentials(viewModel, email = "user@example.com", password = "secret")

            viewModel.onAction(LoginAction.OnLoginClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isNotNull()
            assertThat(sessionStorage.current).isNull()
        }

    @Test
    fun thePasswordVisibilityToggles() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.onAction(LoginAction.OnTogglePasswordVisibility)
            advanceUntilIdle()
            assertThat(viewModel.state.value.isPasswordVisible).isTrue()
            viewModel.onAction(LoginAction.OnTogglePasswordVisibility)
            advanceUntilIdle()
            assertThat(viewModel.state.value.isPasswordVisible).isFalse()
        }

    // The desktop providers are not implemented yet (#144) and report a server error; the screen
    // must show it and stop spinning rather than hang or crash.
    @Test
    fun socialSignInThatFailsShowsTheErrorAndStopsLoading() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.onAction(LoginAction.OnGoogleSignInClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.error).isNotNull()
            assertThat(viewModel.state.value.isLoggingIn).isFalse()

            viewModel.onAction(LoginAction.OnAppleSignInClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.error).isNotNull()
            assertThat(viewModel.state.value.isLoggingIn).isFalse()
            assertThat(sessionStorage.current).isNull()
        }
}
