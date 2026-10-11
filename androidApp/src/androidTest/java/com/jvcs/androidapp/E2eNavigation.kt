package com.jvcs.androidapp

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/**
 * A clean install opens on onboarding's Welcome screen, not on Login. Taps Welcome's "Log in" so a
 * test can start from the login form.
 */
fun AndroidComposeTestRule<*, *>.openLoginFromWelcome() {
    waitUntil(5_000) { onAllNodesWithTag("welcome_login").fetchSemanticsNodes().isNotEmpty() }
    onNodeWithTag("welcome_login").performClick()
}
