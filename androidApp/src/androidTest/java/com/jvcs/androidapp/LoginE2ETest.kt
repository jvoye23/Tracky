package com.jvcs.androidapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class LoginE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginAndNavigateToProjectOverview() {
        // Wait for login screen to appear (auth check may show loading first)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTag("login_email")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Type email
        composeTestRule.onNodeWithTag("login_email").performTextInput(BuildConfig.TEST_EMAIL)

        // Type password
        composeTestRule.onNodeWithTag("login_password").performTextInput(BuildConfig.TEST_PASSWORD)

        // Click login button
        composeTestRule.onNodeWithTag("login_button").performClick()

        // Wait for navigation to project overview (network call, allow up to 15s)
        composeTestRule.waitUntil(15_000) {
            composeTestRule.onAllNodesWithTag("project_overview")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Assert project overview screen is displayed
        composeTestRule.onNodeWithTag("project_overview").assertIsDisplayed()
    }
}
