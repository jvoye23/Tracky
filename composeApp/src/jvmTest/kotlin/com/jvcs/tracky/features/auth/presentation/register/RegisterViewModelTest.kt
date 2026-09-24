package com.jvcs.tracky.features.auth.presentation.register

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
import tracky.composeapp.generated.resources.error_account_exists
import tracky.composeapp.generated.resources.error_invalid_email
import tracky.composeapp.generated.resources.error_invalid_name
import tracky.composeapp.generated.resources.error_invalid_password
import tracky.composeapp.generated.resources.passwords_do_not_match
import tracky.composeapp.generated.resources.terms_required
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class RegisterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val authService = FakeAuthService()
    private val sessionStorage = FakeSessionStorage(initial = null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RegisterViewModel(authService, sessionStorage, SocialAuthProvider())

    /** State is WhileSubscribed: without a collector it never leaves its initial value. */
    private fun TestScope.subscribe(viewModel: RegisterViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.fillIn(
        viewModel: RegisterViewModel,
        name: String = "Ada Lovelace",
        email: String = "ada@example.com",
        password: String = VALID_PASSWORD,
        confirm: String = password,
        agree: Boolean = true,
    ) {
        subscribe(viewModel)
        with(viewModel.state.value) {
            nameTextState.setTextAndPlaceCursorAtEnd(name)
            emailTextState.setTextAndPlaceCursorAtEnd(email)
            passwordTextState.setTextAndPlaceCursorAtEnd(password)
            confirmPasswordTextState.setTextAndPlaceCursorAtEnd(confirm)
        }
        viewModel.onAction(RegisterAction.OnTermsToggle(agree))
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    @Test
    fun aCompleteFormCanRegister() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fillIn(viewModel)

            with(viewModel.state.value) {
                assertThat(isNameValid).isTrue()
                assertThat(isEmailValid).isTrue()
                assertThat(isPasswordValid).isTrue()
                assertThat(isConfirmPasswordValid).isTrue()
                assertThat(canRegister).isTrue()
            }
        }

    @Test
    fun withoutAgreeingToTheTermsNobodyCanRegister() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fillIn(viewModel, agree = false)

            assertThat(viewModel.state.value.canRegister).isFalse()
        }

    @Test
    fun aMismatchedConfirmationBlocksRegistration() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fillIn(viewModel, confirm = "Different1!")

            assertThat(viewModel.state.value.isConfirmPasswordValid).isFalse()
            assertThat(viewModel.state.value.canRegister).isFalse()
        }

    @Test
    fun anInvalidFormMarksEveryBadFieldAndSendsNothing() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fillIn(viewModel, name = "A", email = "nope", password = "weak", confirm = "other", agree = false)

            viewModel.onAction(RegisterAction.OnRegisterClick)
            advanceUntilIdle()

            with(viewModel.state.value) {
                assertThat(nameError).isEqualTo(UiText.Resource(Res.string.error_invalid_name))
                assertThat(emailError).isEqualTo(UiText.Resource(Res.string.error_invalid_email))
                assertThat(passwordError).isEqualTo(UiText.Resource(Res.string.error_invalid_password))
                assertThat(confirmPasswordError).isEqualTo(UiText.Resource(Res.string.passwords_do_not_match))
                assertThat(termsError).isEqualTo(UiText.Resource(Res.string.terms_required))
            }
            assertThat(authService.registerCalls).isEqualTo(emptyList())
        }

    @Test
    fun agreeingClearsTheTermsError() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fillIn(viewModel, agree = false)
            viewModel.onAction(RegisterAction.OnRegisterClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.termsError).isNotNull()

            viewModel.onAction(RegisterAction.OnTermsToggle(true))
            advanceUntilIdle()

            assertThat(viewModel.state.value.termsError).isNull()
        }

    @Test
    fun aSuccessfulRegistrationSendsTheTrimmedNameAndReportsTheEmail() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            fillIn(viewModel, name = "  Ada Lovelace  ")

            viewModel.events.test {
                viewModel.onAction(RegisterAction.OnRegisterClick)
                assertThat(awaitItem()).isEqualTo(RegisterEvent.Success("ada@example.com"))
            }

            assertThat(authService.registerCalls)
                .containsExactly(Triple("ada@example.com", "Ada Lovelace", VALID_PASSWORD))
            assertThat(viewModel.state.value.isRegistering).isFalse()
        }

    @Test
    fun anExistingAccountIsNamedAsSuch() =
        runTest(dispatcher) {
            authService.registerResult = Result.Error(DataError.Remote.CONFLICT)
            val viewModel = viewModel()
            fillIn(viewModel)

            viewModel.onAction(RegisterAction.OnRegisterClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.registrationError)
                .isEqualTo(UiText.Resource(Res.string.error_account_exists))
            assertThat(viewModel.state.value.isRegistering).isFalse()
        }

    @Test
    fun anyOtherFailureShowsAnError() =
        runTest(dispatcher) {
            authService.registerResult = Result.Error(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            fillIn(viewModel)

            viewModel.onAction(RegisterAction.OnRegisterClick)
            advanceUntilIdle()

            assertThat(viewModel.state.value.registrationError).isNotNull()
            assertThat(viewModel.state.value.isRegistering).isFalse()
        }

    @Test
    fun bothPasswordVisibilitiesToggleIndependently() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.onAction(RegisterAction.OnTogglePasswordVisibilityClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.isPasswordVisible).isTrue()
            assertThat(viewModel.state.value.isConfirmPasswordVisible).isFalse()

            viewModel.onAction(RegisterAction.OnToggleConfirmPasswordVisibilityClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.isConfirmPasswordVisible).isTrue()
        }

    // The desktop providers are not implemented yet (#144) and report a server error.
    @Test
    fun socialSignInThatFailsShowsTheErrorAndStopsLoading() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.onAction(RegisterAction.OnGoogleSignInClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.registrationError).isNotNull()
            assertThat(viewModel.state.value.isRegistering).isFalse()

            viewModel.onAction(RegisterAction.OnAppleSignInClick)
            advanceUntilIdle()
            assertThat(viewModel.state.value.registrationError).isNotNull()
            assertThat(viewModel.state.value.isRegistering).isFalse()
            assertThat(sessionStorage.current).isNull()
        }

    private companion object {
        const val VALID_PASSWORD = "Secret123!"
    }
}
