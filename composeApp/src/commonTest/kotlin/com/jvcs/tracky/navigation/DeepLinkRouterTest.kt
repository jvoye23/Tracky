package com.jvcs.tracky.navigation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class DeepLinkRouterTest {

    private val router = DeepLinkRouter()

    private fun detail(id: String) = Route.ProjectRoute.ProjectDetail(isEditMode = false, projectId = id)

    @Test
    fun aRequestMadeBeforeAnythingIsListeningIsDeliveredOnceAListenerArrives() {
        // The nav host is not composed while the session read is still in flight, so a tap on the
        // notification from a cold start always beats its own listener.
        router.request(detail("p1"))

        var received: Route? = null
        router.listener = { received = it }

        assertThat(received).isEqualTo(detail("p1"))
    }

    @Test
    fun aRequestMadeWhileListeningIsDeliveredImmediately() {
        var received: Route? = null
        router.listener = { received = it }

        router.request(detail("p1"))

        assertThat(received).isEqualTo(detail("p1"))
    }

    @Test
    fun theNewestRequestWins() {
        router.request(detail("p1"))
        router.request(detail("p2"))

        var received: Route? = null
        router.listener = { received = it }

        assertThat(received).isEqualTo(detail("p2"))
    }

    @Test
    fun aDeliveredRequestIsNotHandedToTheNextListener() {
        // Otherwise a rotation, which disposes the listener and attaches a fresh one, would
        // navigate the user back out of wherever they had gone.
        router.listener = { }
        router.request(detail("p1"))
        router.listener = null

        var received: Route? = null
        router.listener = { received = it }

        assertThat(received).isNull()
    }
}
