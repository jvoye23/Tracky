package com.jvcs.tracky.features.onboarding.presentation.welcome

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.onboarding.presentation.model.basicFeatures
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.get_started
import tracky.composeapp.generated.resources.welcome_hero_title
import kotlin.test.Test

/** The first screen a new install sees: what the free tier does, and the two ways on. */
@OptIn(ExperimentalTestApi::class)
internal class WelcomeScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    @Test
    fun itListsEveryFreeFeature() =
        runComposeUiTest {
            setContent { TrackyTheme { WelcomeScreen(onGetStartedClick = {}, onLoginClick = {}) } }

            onNodeWithText(text(Res.string.welcome_hero_title)).assertExists()
            basicFeatures.forEach { feature ->
                onNode(hasScrollAction()).performScrollToNode(hasText(text(feature.title)))
                onNodeWithText(text(feature.description)).assertExists()
            }
        }

    @Test
    fun getStartedAndLogInLeadOnSeparately() =
        runComposeUiTest {
            val clicks = mutableListOf<String>()
            setContent {
                TrackyTheme {
                    WelcomeScreen(onGetStartedClick = { clicks += "start" }, onLoginClick = { clicks += "login" })
                }
            }

            onNodeWithText(text(Res.string.get_started)).performClick()
            onNodeWithTag("welcome_login").performClick()

            assertThat(clicks).containsExactly("start", "login")
        }
}
