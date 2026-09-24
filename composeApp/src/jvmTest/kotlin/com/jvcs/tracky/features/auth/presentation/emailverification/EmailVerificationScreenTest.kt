package com.jvcs.tracky.features.auth.presentation.emailverification

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.auth.FakeAuthService
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.close
import tracky.composeapp.generated.resources.email_verified_failed
import tracky.composeapp.generated.resources.email_verified_successfully
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.verifying_account
import kotlin.test.Test

/** Drives the real view model through the Root, so opening the screen is what verifies the token. */
@OptIn(ExperimentalTestApi::class)
internal class EmailVerificationScreenTest {

    private val authService = FakeAuthService()
    private val navigation = mutableListOf<String>()

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private fun androidx.compose.ui.test.ComposeUiTest.showRoot() =
        setContent {
            TrackyTheme {
                EmailVerificationScreenRoot(
                    onLoginClick = { navigation += "login" },
                    onCloseClick = { navigation += "close" },
                    viewModel = EmailVerificationViewModel(authService, token = TOKEN),
                )
            }
        }

    @Test
    fun aValidLinkVerifiesAndLeadsToLogin() =
        runComposeUiTest {
            showRoot()

            onNodeWithText(text(Res.string.email_verified_successfully)).assertExists()
            onNodeWithText(text(Res.string.login)).performClick()

            assertThat(authService.verifyEmailCalls).containsExactly(TOKEN)
            assertThat(navigation).containsExactly("login")
        }

    @Test
    fun aRejectedLinkSaysSoAndCloses() =
        runComposeUiTest {
            authService.verifyEmailResult = Result.Error(DataError.Remote.UNAUTHORIZED)
            showRoot()

            onNodeWithText(text(Res.string.email_verified_failed)).assertExists()
            onNodeWithText(text(Res.string.close)).performClick()

            assertThat(navigation).isEqualTo(listOf("close"))
        }

    @Test
    fun whileVerifyingItSaysSo() =
        runComposeUiTest {
            setContent {
                TrackyTheme {
                    EmailVerificationScreen(
                        state = EmailVerificationState(isVerifying = true),
                        onAction = {},
                    )
                }
            }

            onNodeWithText(text(Res.string.verifying_account)).assertExists()
        }

    private companion object {
        const val TOKEN = "verify-token"
    }
}
