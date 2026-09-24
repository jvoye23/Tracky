package com.jvcs.tracky.features.auth.presentation.resetpassword

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.UiText
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.error_same_password
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.reset_password_successfully
import tracky.composeapp.generated.resources.submit
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class ResetPasswordScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    @Test
    fun submitFollowsCanSubmit() =
        runComposeUiTest {
            setContent { TrackyTheme { ResetPasswordScreen(state = ResetPasswordState(), onAction = {}) } }

            onNodeWithText(text(Res.string.submit)).performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun theFormShowsItsErrorAndSubmits() =
        runComposeUiTest {
            val actions = mutableListOf<ResetPasswordAction>()
            val state =
                ResetPasswordState(canSubmit = true, errorText = UiText.Resource(Res.string.error_same_password))
            setContent { TrackyTheme { ResetPasswordScreen(state = state, onAction = { actions += it }) } }

            onNodeWithText(text(Res.string.error_same_password)).performScrollTo().assertExists()
            onNodeWithText(text(Res.string.submit)).performScrollTo().assertIsEnabled().performClick()

            assertThat(actions).containsExactly(ResetPasswordAction.OnSubmitClick)
        }

    @Test
    fun aSuccessfulResetLeadsToLogin() =
        runComposeUiTest {
            val actions = mutableListOf<ResetPasswordAction>()
            val state = ResetPasswordState(isResetSuccessful = true)
            setContent { TrackyTheme { ResetPasswordScreen(state = state, onAction = { actions += it }) } }

            onNodeWithText(text(Res.string.reset_password_successfully)).assertExists()
            onNodeWithText(text(Res.string.login)).performScrollTo().performClick()

            assertThat(actions).containsExactly(ResetPasswordAction.OnLoginClick)
        }
}
