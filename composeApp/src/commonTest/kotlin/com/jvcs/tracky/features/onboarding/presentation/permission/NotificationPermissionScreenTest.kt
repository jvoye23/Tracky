package com.jvcs.tracky.features.onboarding.presentation.permission

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.onboarding_permission_allow
import tracky.composeapp.generated.resources.onboarding_permission_description
import tracky.composeapp.generated.resources.onboarding_permission_skip
import tracky.composeapp.generated.resources.onboarding_permission_title
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class NotificationPermissionScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    @Test
    fun itExplainsTheRequestAndOffersBothAnswers() =
        runComposeUiTest {
            val actions = mutableListOf<NotificationPermissionAction>()
            setContent {
                TrackyTheme {
                    NotificationPermissionScreen(state = NotificationPermissionState(), onAction = { actions += it })
                }
            }

            onNodeWithText(text(Res.string.onboarding_permission_title)).assertExists()
            onNodeWithText(text(Res.string.onboarding_permission_description)).assertExists()
            onNodeWithText(text(Res.string.onboarding_permission_allow)).performClick()
            onNodeWithText(text(Res.string.onboarding_permission_skip)).performClick()

            assertThat(actions).containsExactly(
                NotificationPermissionAction.OnAllowClick,
                NotificationPermissionAction.OnSkipClick,
            )
        }

    @Test
    fun whileTheSystemDialogIsUpSkippingWaits() =
        runComposeUiTest {
            setContent {
                TrackyTheme {
                    NotificationPermissionScreen(
                        state = NotificationPermissionState(isRequesting = true),
                        onAction = {},
                    )
                }
            }

            onNodeWithText(text(Res.string.onboarding_permission_skip)).assertIsNotEnabled()
            // The allow button shows its progress in place of the label.
            onNodeWithText(text(Res.string.onboarding_permission_allow)).assertDoesNotExist()
        }
}
