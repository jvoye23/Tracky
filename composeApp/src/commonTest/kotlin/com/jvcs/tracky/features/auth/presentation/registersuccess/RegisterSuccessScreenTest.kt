package com.jvcs.tracky.features.auth.presentation.registersuccess

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.back_to_login
import tracky.composeapp.generated.resources.error_no_internet
import tracky.composeapp.generated.resources.open_email_app
import tracky.composeapp.generated.resources.resend_verification_email
import tracky.composeapp.generated.resources.resent_verification_email
import kotlin.test.Test

/** Drives the real view model through the Root, so the resend round trip is covered end to end. */
@OptIn(ExperimentalTestApi::class)
internal class RegisterSuccessScreenTest {

    private val authService = FakeAuthService()
    private val navigation = mutableListOf<String>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.showRoot() =
        setContent {
            TrackyTheme {
                RegisterSuccessScreenRoot(
                    viewModel = RegisterSuccessViewModel(authService, email = EMAIL),
                    onLoginClick = { navigation += "login" },
                )
            }
        }

    @Test
    fun resendingConfirmsWithASnackbar() =
        runComposeUiTest {
            showRoot()

            onNodeWithText(text(Res.string.resend_verification_email)).performClick()

            onNodeWithText(text(Res.string.resent_verification_email)).assertExists()
            assertThat(authService.resendVerificationEmailCalls).containsExactly(EMAIL)
        }

    @Test
    fun aFailedResendShowsTheError() =
        runComposeUiTest {
            authService.resendVerificationEmailResult = Result.Error(DataError.Remote.NO_INTERNET)
            showRoot()

            onNodeWithText(text(Res.string.resend_verification_email)).performClick()

            onNodeWithText(text(Res.string.error_no_internet)).assertExists()
        }

    @Test
    fun backToLoginNavigatesAndTheEmailAppButtonIsOffered() =
        runComposeUiTest {
            showRoot()

            onNodeWithText(text(Res.string.open_email_app)).performClick()
            onNodeWithText(text(Res.string.back_to_login)).performClick()

            assertThat(navigation).containsExactly("login")
        }

    @Test
    fun resendIsDisabledWhileItIsInFlight() =
        runComposeUiTest {
            setContent {
                TrackyTheme {
                    RegisterSuccessScreen(
                        state = RegisterSuccessState(isResendingVerificationEmail = true),
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                        onAction = {},
                    )
                }
            }

            onNodeWithText(text(Res.string.resend_verification_email)).assertIsNotEnabled()
        }

    private companion object {
        const val EMAIL = "ada@example.com"
    }
}
