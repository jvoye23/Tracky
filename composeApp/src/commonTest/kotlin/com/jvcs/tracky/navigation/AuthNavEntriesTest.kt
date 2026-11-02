package com.jvcs.tracky.navigation

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class AuthNavEntriesTest {

    @Test
    fun aLoginWithoutTheNotificationGrantGoesThroughThePermissionScreen() {
        assertThat(routeAfterLogin(needsNotificationPrompt = true))
            .isEqualTo(Route.AuthRoute.LoginNotificationPermission)
    }

    @Test
    fun aLoginWithNothingToAskGoesStraightToTheProjects() {
        assertThat(routeAfterLogin(needsNotificationPrompt = false))
            .isEqualTo(Route.ProjectRoute.ProjectOverview)
    }
}
