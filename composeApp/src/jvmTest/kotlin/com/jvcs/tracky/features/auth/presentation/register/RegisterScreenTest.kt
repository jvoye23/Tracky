package com.jvcs.tracky.features.auth.presentation.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import tracky.composeapp.generated.resources.create_account
import tracky.composeapp.generated.resources.error_account_exists
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.terms_required
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class RegisterScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    // "Create account" is also the headline, so the button is the one that can be clicked.
    private fun createAccountButton() = hasText(text(Res.string.create_account)) and hasClickAction()

    @Test
    fun theCreateAccountButtonFollowsCanRegister() =
        runComposeUiTest {
            var state by mutableStateOf(RegisterState(canRegister = false))
            setContent { TrackyTheme { RegisterScreen(state = state, onAction = {}) } }

            onNode(createAccountButton()).assertIsNotEnabled()
            state = state.copy(canRegister = true)
            onNode(createAccountButton()).assertIsEnabled()
        }

    @Test
    fun everyAffordanceRaisesItsAction() =
        runComposeUiTest {
            val actions = mutableListOf<RegisterAction>()
            setContent {
                TrackyTheme { RegisterScreen(state = RegisterState(canRegister = true), onAction = { actions += it }) }
            }

            onNodeWithTag("register_terms_checkbox").performScrollTo().performClick()
            onNode(createAccountButton()).performScrollTo().performClick()
            onNodeWithText(text(Res.string.login)).performScrollTo().performClick()

            assertThat(actions).containsExactly(
                RegisterAction.OnTermsToggle(true),
                RegisterAction.OnRegisterClick,
                RegisterAction.OnLoginClick,
            )
        }

    @Test
    fun theTermsAndRegistrationErrorsAreShown() =
        runComposeUiTest {
            setContent {
                TrackyTheme {
                    RegisterScreen(
                        state =
                            RegisterState(
                                termsError = UiText.Resource(Res.string.terms_required),
                                registrationError = UiText.Resource(Res.string.error_account_exists),
                            ),
                        onAction = {},
                    )
                }
            }

            onNodeWithText(text(Res.string.terms_required)).performScrollTo().assertExists()
            onNodeWithText(text(Res.string.error_account_exists)).performScrollTo().assertExists()
        }

    @Test
    fun theRootRegistersAndNavigatesWithTheEmail() =
        runComposeUiTest {
            val navigation = mutableListOf<String>()
            val viewModel =
                RegisterViewModel(FakeAuthService(), FakeSessionStorage(initial = null), SocialAuthProvider())
            setContent {
                TrackyTheme {
                    RegisterScreenRoot(
                        onRegisterSuccess = { navigation += "success:$it" },
                        onLoginClick = { navigation += "login" },
                        viewModel = viewModel,
                    )
                }
            }

            val fields = onAllNodes(hasSetTextAction())
            listOf("Ada Lovelace", "ada@example.com", "Secret123!", "Secret123!")
                .forEachIndexed { index, value -> fields[index].performTextInput(value) }
            onNodeWithTag("register_terms_checkbox").performScrollTo().performClick()
            onNodeWithText(text(Res.string.login)).performScrollTo().performClick()
            onNode(createAccountButton()).performScrollTo().performClick()
            waitForIdle()

            assertThat(navigation).containsExactly("login", "success:ada@example.com")
        }
}
