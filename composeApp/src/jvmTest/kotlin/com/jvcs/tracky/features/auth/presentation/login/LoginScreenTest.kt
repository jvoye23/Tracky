package com.jvcs.tracky.features.auth.presentation.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.core.domain.auth.FakeSessionStorage
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.UiText
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.continue_with_apple
import tracky.composeapp.generated.resources.continue_with_google
import tracky.composeapp.generated.resources.error_invalid_credentials
import tracky.composeapp.generated.resources.sign_up
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class LoginScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    @Test
    fun theLoginButtonFollowsCanLogin() =
        runComposeUiTest {
            var state by mutableStateOf(LoginState(canLogin = false))
            setContent { TrackyTheme { LoginScreen(state = state, onAction = {}) } }

            onNodeWithTag("login_button").assertIsNotEnabled()
            state = state.copy(canLogin = true)
            onNodeWithTag("login_button").assertIsEnabled()
        }

    @Test
    fun everyAffordanceRaisesItsAction() =
        runComposeUiTest {
            val actions = mutableListOf<LoginAction>()
            setContent {
                TrackyTheme { LoginScreen(state = LoginState(canLogin = true), onAction = { actions += it }) }
            }

            onNodeWithTag("login_button").performScrollTo().performClick()
            onNodeWithTag("login_forgot_password").performScrollTo().performClick()
            onNodeWithText(text(Res.string.sign_up)).performScrollTo().performClick()
            onNodeWithText(text(Res.string.continue_with_google)).performScrollTo().performClick()
            onNodeWithText(text(Res.string.continue_with_apple)).performScrollTo().performClick()

            assertThat(actions).containsExactly(
                LoginAction.OnLoginClick,
                LoginAction.OnForgotPasswordClick,
                LoginAction.OnSignUpClick,
                LoginAction.OnGoogleSignInClick,
                LoginAction.OnAppleSignInClick,
            )
        }

    @Test
    fun anErrorIsShown() =
        runComposeUiTest {
            setContent {
                TrackyTheme {
                    LoginScreen(
                        state = LoginState(error = UiText.Resource(Res.string.error_invalid_credentials)),
                        onAction = {},
                    )
                }
            }

            onNodeWithText(text(Res.string.error_invalid_credentials)).assertExists()
        }

    @Test
    fun whileLoggingInTheSocialButtonsAreDisabled() =
        runComposeUiTest {
            setContent { TrackyTheme { LoginScreen(state = LoginState(isLoggingIn = true), onAction = {}) } }

            onNodeWithText(text(Res.string.continue_with_google)).performScrollTo().assertIsNotEnabled()
            onNodeWithText(text(Res.string.continue_with_apple)).performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun theRootLogsInAndNavigates() =
        runComposeUiTest {
            val navigation = mutableListOf<String>()
            val viewModel = LoginViewModel(FakeAuthService(), FakeSessionStorage(initial = null), SocialAuthProvider())
            setContent {
                TrackyTheme {
                    LoginScreenRoot(
                        onLoginSuccess = { navigation += "home" },
                        onForgotPasswordClick = { navigation += "forgot" },
                        onCreateAccountClick = { navigation += "register" },
                        viewModel = viewModel,
                    )
                }
            }

            onNodeWithTag("login_forgot_password").performScrollTo().performClick()
            onNodeWithText(text(Res.string.sign_up)).performScrollTo().performClick()
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("login_email"))).performTextInput("ada@example.com")
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("login_password"))).performTextInput("secret")
            onNodeWithTag("login_button").performScrollTo().performClick()
            waitForIdle()

            assertThat(navigation).containsExactly("forgot", "register", "home")
        }
}
