package com.jvcs.tracky.features.auth.presentation.forgotpassword

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.UiText
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.back
import tracky.composeapp.generated.resources.back_to_login
import tracky.composeapp.generated.resources.error_unknown
import tracky.composeapp.generated.resources.resend_email
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class ForgotPasswordScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    @Test
    fun theFormRaisesItsActionsAndShowsItsError() =
        runComposeUiTest {
            val actions = mutableListOf<ForgotPasswordAction>()
            val state =
                ForgotPasswordState(canSubmit = true, errorText = UiText.Resource(Res.string.error_unknown))
            setContent { TrackyTheme { ForgotPasswordScreen(state = state, onAction = { actions += it }) } }

            onNodeWithText(text(Res.string.error_unknown)).assertExists()
            onNodeWithTag("forgot_password_submit").performScrollTo().assertIsEnabled().performClick()
            onNodeWithText(text(Res.string.back_to_login)).performScrollTo().performClick()
            onNodeWithContentDescription(text(Res.string.back)).performClick()

            assertThat(actions).containsExactly(
                ForgotPasswordAction.OnSubmitClick,
                ForgotPasswordAction.OnBackToLoginClick,
                ForgotPasswordAction.OnBackClick,
            )
        }

    @Test
    fun submitIsDisabledUntilTheEmailIsValid() =
        runComposeUiTest {
            setContent { TrackyTheme { ForgotPasswordScreen(state = ForgotPasswordState(), onAction = {}) } }

            onNodeWithTag("forgot_password_submit").performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun afterSendingTheEmailIsNamedAndCanBeResent() =
        runComposeUiTest {
            val actions = mutableListOf<ForgotPasswordAction>()
            val state =
                ForgotPasswordState(
                    emailTextFieldState = TextFieldState("ada@example.com"),
                    isEmailSentSuccessfully = true,
                )
            setContent { TrackyTheme { ForgotPasswordScreen(state = state, onAction = { actions += it }) } }

            onNodeWithTag("forgot_password_success").assertExists()
            onNodeWithText("ada@example.com", substring = true).assertExists()
            onNodeWithText(text(Res.string.back_to_login)).performScrollTo().performClick()
            onNodeWithText(text(Res.string.resend_email)).performScrollTo().performClick()

            assertThat(actions).containsExactly(
                ForgotPasswordAction.OnBackToLoginClick,
                ForgotPasswordAction.OnResendClick,
            )
        }

    @Test
    fun theRootSendsTheLinkAndGoesBack() =
        runComposeUiTest {
            var backClicks = 0
            setContent {
                TrackyTheme {
                    ForgotPasswordScreenRoot(
                        viewModel = ForgotPasswordViewModel(FakeAuthService()),
                        onBackClick = { backClicks++ },
                    )
                }
            }

            onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag("forgot_password_email")),
            ).performTextInput("ada@example.com")
            onNodeWithTag("forgot_password_submit").performScrollTo().performClick()
            onNodeWithTag("forgot_password_success").assertExists()
            onNodeWithText(text(Res.string.back_to_login)).performScrollTo().performClick()

            assertThat(backClicks).isEqualTo(1)
        }
}
